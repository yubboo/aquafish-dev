package com.aquafish.plugin.runtime;

import com.aquafish.core.config.WorkDirResolver;
import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.TableNameResolver;
import com.aquafish.core.database.TableNames;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.pf4j.PluginDependency;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * PF4J 运行状态与 Aquafish 数据库注册表之间的协调层。
 *
 * <p>文件扫描、ClassLoader 和 Spring 子上下文属于阻塞型生命周期操作，统一切换到
 * boundedElastic；数据库登记仍保持 R2DBC。插件启动成功但数据库写入失败时会停止插件，
 * 避免页面显示“未启用”而 JVM 内实际运行。</p>
 */
@Service
public class PluginRuntimeLifecycleService {

    private final AquafishPluginManager pluginManager;
    private final WorkDirResolver workDirResolver;
    private final DatabaseRuntimeSettingsService settingsService;
    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;

    public PluginRuntimeLifecycleService(
        AquafishPluginManager pluginManager,
        WorkDirResolver workDirResolver,
        DatabaseRuntimeSettingsService settingsService,
        DatabaseClient databaseClient,
        TransactionalOperator transactionalOperator
    ) {
        this.pluginManager = pluginManager;
        this.workDirResolver = workDirResolver;
        this.settingsService = settingsService;
        this.databaseClient = databaseClient;
        this.transactionalOperator = transactionalOperator;
    }

    /* BEGIN：启动恢复、重扫与后台状态。 */

    /**
     * 应用就绪后扫描插件、同步注册表，并恢复数据库中标记为启用的插件。
     */
    public Mono<Void> bootstrap() {
        return load(false)
            .then(synchronizeRegistry())
            .then(enabledPluginIds())
            .flatMapMany(Flux::fromIterable)
            .concatMap(this::startWithoutPersistence)
            .then();
    }

    /**
     * 停止并卸载旧 ClassLoader 后重新扫描，再恢复 enabled_flag。
     */
    public Mono<PluginManagementSnapshot> rescan() {
        return load(true)
            .then(synchronizeRegistry())
            .then(enabledPluginIds())
            .flatMapMany(Flux::fromIterable)
            .concatMap(this::startWithoutPersistence)
            .then(snapshot());
    }

    public Mono<PluginManagementSnapshot> snapshot() {
        Mono<List<PluginRuntimeItem>> items = Mono.fromCallable(
                pluginManager::snapshot
            )
            .subscribeOn(Schedulers.boundedElastic());
        return ensureLoaded().then(Mono.zip(
            items,
            enabledPluginIds().onErrorReturn(Set.of())
        )).map(tuple -> new PluginManagementSnapshot(
                Files.isDirectory(workDirResolver.pluginsDir()),
                true,
                true,
                tuple.getT1().size(),
                tuple.getT1(),
                tuple.getT2(),
                "PF4J 独立类加载、依赖解析和 Spring 子上下文已经接入。"
            ));
    }

    /* END：启动恢复、重扫与后台状态。 */

    /* BEGIN：单个插件启停。 */

    public Mono<PluginManagementSnapshot> start(
        String pluginId,
        long operatorId
    ) {
        String safeId = requirePluginId(pluginId);
        return ensureLoaded()
            .then(Mono.fromCallable(() ->
                    pluginManager.startWithDependencies(safeId)
                )
                .subscribeOn(Schedulers.boundedElastic()))
            .flatMap(state -> updateRuntimeState(
                safeId,
                true,
                state.name(),
                null
            ))
            .then(writeAudit(
                operatorId,
                "plugin.lifecycle.start",
                safeId,
                "启用插件"
            ))
            .then(snapshot())
            .onErrorResume(error ->
                compensateFailedStart(safeId, error)
                    .then(Mono.error(error))
            );
    }

    public Mono<PluginManagementSnapshot> stop(
        String pluginId,
        long operatorId
    ) {
        String safeId = requirePluginId(pluginId);
        return ensureLoaded()
            .then(Mono.fromCallable(() ->
                    pluginManager.stopWithDependents(safeId)
                )
                .subscribeOn(Schedulers.boundedElastic()))
            .then(syncRuntimeStates(safeId))
            .then(writeAudit(
                operatorId,
                "plugin.lifecycle.stop",
                safeId,
                "停用插件"
            ))
            .then(snapshot());
    }

    /* END：单个插件启停。 */

    /* BEGIN：PF4J 注册表与依赖图同步。 */

    private Mono<Void> synchronizeRegistry() {
        DatabaseSettings settings = settings();
        return Mono.fromCallable(pluginManager::snapshot)
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapMany(Flux::fromIterable)
            .concatMap(item -> synchronizePlugin(settings, item))
            .then();
    }

    private Mono<Void> synchronizePlugin(
        DatabaseSettings settings,
        PluginRuntimeItem item
    ) {
        PluginWrapper wrapper = pluginManager.getPlugin(item.pluginId());
        if (wrapper == null) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> packageHash(wrapper.getPluginPath()))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(hash -> ensurePluginRecord(
                settings,
                wrapper,
                item,
                hash
            ))
            .flatMap(pluginDatabaseId ->
                replaceDependencies(
                    settings,
                    pluginDatabaseId,
                    wrapper
                ).then()
            );
    }

    private Mono<Long> ensurePluginRecord(
        DatabaseSettings settings,
        PluginWrapper wrapper,
        PluginRuntimeItem item,
        String packageHash
    ) {
        return pluginDatabaseId(settings, item.pluginId())
            .flatMap(id -> updatePluginRecord(
                settings,
                id,
                wrapper,
                item,
                packageHash
            ).thenReturn(id))
            .switchIfEmpty(
                insertPluginRecord(
                    settings,
                    wrapper,
                    item,
                    packageHash
                ).then(pluginDatabaseId(settings, item.pluginId()))
            );
    }

    private Mono<Void> updatePluginRecord(
        DatabaseSettings settings,
        long id,
        PluginWrapper wrapper,
        PluginRuntimeItem item,
        String packageHash
    ) {
        return databaseClient.sql(
                "update " + table(settings, TableNames.PLUGINS)
                    + " set name = :name, version = :version, "
                    + "provider_name = :provider, description = :description, "
                    + "main_class = :mainClass, package_hash = :packageHash, "
                    + "status = :status, last_error = :lastError, "
                    + "updated_at = current_timestamp where id = :id"
            )
            .bind("name", item.name())
            .bind("version", item.version())
            .bind("provider", safe(item.provider()))
            .bind("description", safe(item.description()))
            .bind("mainClass", wrapper.getDescriptor().getPluginClass())
            .bind("packageHash", packageHash)
            .bind("status", databaseStatus(item))
            .bind("lastError", safe(item.error()))
            .bind("id", id)
            .fetch()
            .rowsUpdated()
            .then();
    }

    private Mono<Void> insertPluginRecord(
        DatabaseSettings settings,
        PluginWrapper wrapper,
        PluginRuntimeItem item,
        String packageHash
    ) {
        return databaseClient.sql(
                "insert into " + table(settings, TableNames.PLUGINS)
                    + " (plugin_key, name, version, provider_name, description, "
                    + "main_class, package_hash, source_type, status, enabled_flag, "
                    + "last_error) values (:pluginKey, :name, :version, :provider, "
                    + ":description, :mainClass, :packageHash, 'UPLOAD', :status, "
                    + "0, :lastError)"
            )
            .bind("pluginKey", item.pluginId())
            .bind("name", item.name())
            .bind("version", item.version())
            .bind("provider", safe(item.provider()))
            .bind("description", safe(item.description()))
            .bind("mainClass", wrapper.getDescriptor().getPluginClass())
            .bind("packageHash", packageHash)
            .bind("status", databaseStatus(item))
            .bind("lastError", safe(item.error()))
            .fetch()
            .rowsUpdated()
            .then();
    }

    private Mono<Void> replaceDependencies(
        DatabaseSettings settings,
        long pluginDatabaseId,
        PluginWrapper wrapper
    ) {
        Mono<Void> delete = databaseClient.sql(
                "delete from "
                    + table(settings, TableNames.PLUGIN_DEPENDENCIES)
                    + " where plugin_id = :pluginId"
            )
            .bind("pluginId", pluginDatabaseId)
            .fetch()
            .rowsUpdated()
            .then();

        return delete.thenMany(
            Flux.fromIterable(
                wrapper.getDescriptor().getDependencies()
            ).concatMap(dependency -> insertDependency(
                settings,
                pluginDatabaseId,
                dependency
            ))
        ).then().as(transactionalOperator::transactional);
    }

    private Mono<Void> insertDependency(
        DatabaseSettings settings,
        long pluginDatabaseId,
        PluginDependency dependency
    ) {
        return pluginDatabaseId(settings, dependency.getPluginId())
            .map(Long.class::cast)
            .defaultIfEmpty(0L)
            .flatMap(resolvedId -> {
                boolean present = resolvedId > 0L;
                DatabaseClient.GenericExecuteSpec insert =
                    databaseClient.sql(
                            "insert into "
                                + table(
                                    settings,
                                    TableNames.PLUGIN_DEPENDENCIES
                                )
                                + " (plugin_id, dependency_key, "
                                + "version_requirement, optional_flag, "
                                + "resolved_plugin_id, status, last_error) "
                                + "values (:pluginId, :dependencyKey, "
                                + ":versionRequirement, :optionalFlag, "
                                + ":resolvedPluginId, :status, :lastError)"
                        )
                        .bind("pluginId", pluginDatabaseId)
                        .bind(
                            "dependencyKey",
                            dependency.getPluginId()
                        )
                        .bind(
                            "versionRequirement",
                            safeVersion(
                                dependency.getPluginVersionSupport()
                            )
                        )
                        .bind(
                            "optionalFlag",
                            dependency.isOptional() ? 1 : 0
                        )
                        .bind(
                            "status",
                            present ? "RESOLVED" : "MISSING"
                        )
                        .bind(
                            "lastError",
                            present || dependency.isOptional()
                                ? ""
                                : "缺少必选依赖 "
                                    + dependency.getPluginId()
                        );
                insert = present
                    ? insert.bind("resolvedPluginId", resolvedId)
                    : insert.bindNull("resolvedPluginId", Long.class);
                return insert.fetch().rowsUpdated().then();
            });
    }

    /* END：PF4J 注册表与依赖图同步。 */

    /* BEGIN：运行状态持久化与补偿。 */

    private Mono<Void> startWithoutPersistence(String pluginId) {
        return Mono.fromCallable(() ->
                pluginManager.startWithDependencies(pluginId)
            )
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(state -> updateRuntimeState(
                pluginId,
                true,
                state.name(),
                null
            ))
            .onErrorResume(error ->
                updateRuntimeState(
                    pluginId,
                    true,
                    "FAILED",
                    rootMessage(error)
                )
            );
    }

    private Mono<Void> syncRuntimeStates(String disabledPluginId) {
        DatabaseSettings settings = settings();
        return Mono.fromCallable(pluginManager::snapshot)
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapMany(Flux::fromIterable)
            .concatMap(item -> {
                boolean target = item.pluginId()
                    .equals(disabledPluginId);
                return databaseClient.sql(
                        "update " + table(settings, TableNames.PLUGINS)
                            + " set enabled_flag = case when :target = 1 "
                            + "then 0 else enabled_flag end, status = :status, "
                            + "disabled_at = case when :started = 0 "
                            + "then current_timestamp else disabled_at end, "
                            + "updated_at = current_timestamp "
                            + "where plugin_key = :pluginKey"
                    )
                    .bind("target", target ? 1 : 0)
                    .bind(
                        "status",
                        target
                            ? "DISABLED"
                            : databaseStatus(item)
                    )
                    .bind("started", item.started() ? 1 : 0)
                    .bind("pluginKey", item.pluginId())
                    .fetch()
                    .rowsUpdated();
            })
            .then();
    }

    private Mono<Void> updateRuntimeState(
        String pluginId,
        boolean enabled,
        String state,
        String error
    ) {
        DatabaseSettings settings = settings();
        DatabaseClient.GenericExecuteSpec update = databaseClient.sql(
                "update " + table(settings, TableNames.PLUGINS)
                    + " set enabled_flag = :enabled, status = :status, "
                    + "enabled_at = case when :enabled = 1 "
                    + "then current_timestamp else enabled_at end, "
                    + "disabled_at = case when :enabled = 0 "
                    + "then current_timestamp else disabled_at end, "
                    + "last_error = :lastError, updated_at = current_timestamp "
                    + "where plugin_key = :pluginKey"
            )
            .bind("enabled", enabled ? 1 : 0)
            .bind("status", normalizeState(state, enabled))
            .bind("lastError", safe(error))
            .bind("pluginKey", pluginId);
        return update.fetch().rowsUpdated().then();
    }

    private Mono<Void> compensateFailedStart(
        String pluginId,
        Throwable error
    ) {
        Mono<Void> stop = Mono.fromRunnable(() -> {
                try {
                    pluginManager.stopWithDependents(pluginId);
                } catch (Throwable ignored) {
                    // 原始异常优先返回；失败摘要仍会写入数据库。
                }
            })
            .subscribeOn(Schedulers.boundedElastic())
            .then();
        return stop.then(
            updateRuntimeState(
                pluginId,
                false,
                "FAILED",
                rootMessage(error)
            )
        );
    }

    private Mono<Void> writeAudit(
        long operatorId,
        String action,
        String pluginId,
        String summary
    ) {
        DatabaseSettings settings = settings();
        return pluginDatabaseId(settings, pluginId)
            .flatMap(targetId -> databaseClient.sql(
                    "insert into "
                        + table(
                            settings,
                            TableNames.ADMIN_OPERATION_LOGS
                        )
                        + " (operator_id, action_key, target_type, target_id, "
                        + "summary, detail) values (:operatorId, :action, "
                        + "'plugin', :targetId, :summary, :detail)"
                )
                .bind("operatorId", operatorId)
                .bind("action", action)
                .bind("targetId", targetId)
                .bind("summary", summary)
                .bind("detail", pluginId)
                .fetch()
                .rowsUpdated()
                .then());
    }

    /* END：运行状态持久化与补偿。 */

    private Mono<Void> load(boolean reload) {
        return Mono.fromRunnable(() -> {
                if (reload) {
                    pluginManager.reloadAll();
                } else {
                    pluginManager.loadAll();
                }
            })
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    private Mono<Void> ensureLoaded() {
        return pluginManager.isLoaded()
            ? Mono.empty()
            : load(false).then(synchronizeRegistry());
    }

    private Mono<Set<String>> enabledPluginIds() {
        DatabaseSettings settings = settings();
        return databaseClient.sql(
                "select plugin_key from "
                    + table(settings, TableNames.PLUGINS)
                    + " where enabled_flag = 1 order by plugin_key"
            )
            .map((row, metadata) ->
                safe(row.get("plugin_key", String.class))
            )
            .all()
            .collectList()
            .map(values -> Set.copyOf(new LinkedHashSet<>(values)));
    }

    private Mono<Long> pluginDatabaseId(
        DatabaseSettings settings,
        String pluginId
    ) {
        return databaseClient.sql(
                "select id from " + table(settings, TableNames.PLUGINS)
                    + " where plugin_key = :pluginKey"
            )
            .bind("pluginKey", pluginId)
            .map((row, metadata) -> {
                Number value = row.get("id", Number.class);
                return value == null ? 0L : value.longValue();
            })
            .one();
    }

    private String packageHash(Path pluginPath) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        Path source = Files.isDirectory(pluginPath)
            ? pluginPath.resolve("plugin.yaml")
            : pluginPath;
        try (InputStream input = Files.newInputStream(source)) {
            byte[] buffer = new byte[16_384];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String databaseStatus(PluginRuntimeItem item) {
        if (!item.error().isBlank()) {
            return "FAILED";
        }
        return item.started() ? "ENABLED" : item.state();
    }

    private String normalizeState(String state, boolean enabled) {
        if ("STARTED".equalsIgnoreCase(state) && enabled) {
            return "ENABLED";
        }
        if ("STOPPED".equalsIgnoreCase(state) && !enabled) {
            return "DISABLED";
        }
        return state == null || state.isBlank()
            ? (enabled ? "ENABLED" : "DISABLED")
            : state.toUpperCase();
    }

    private String requirePluginId(String pluginId) {
        String value = safe(pluginId);
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,119}")) {
            throw new IllegalArgumentException("插件 ID 格式不正确。");
        }
        return value;
    }

    private String safeVersion(String value) {
        return value == null || value.isBlank() ? "*" : value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
            ? current.getClass().getSimpleName()
            : message;
    }

    private String table(
        DatabaseSettings settings,
        String logicalName
    ) {
        return TableNameResolver.tableName(
            settings.tablePrefix(),
            logicalName
        );
    }

    private DatabaseSettings settings() {
        DatabaseSettings settings = settingsService.current();
        if (settings == null) {
            throw new IllegalStateException("数据库运行配置尚未就绪。");
        }
        return settings.normalized();
    }
}
