package com.aquafish.admin.user;

import com.aquafish.core.admin.auth.AdminAuthUser;
import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.TableNameResolver;
import com.aquafish.core.database.TableNames;
import com.aquafish.core.network.IpAddressRule;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/**
 * 后台 IP 封禁记录增删改服务。
 *
 * <p>该服务只接受经过 {@link IpAddressRule} 校验的 IPv4、IPv6 或 CIDR，不允许主机名
 * 和任意规则文本。每次变更与后台审计日志位于同一响应式事务中，且只有超级管理员
 * 可以执行。</p>
 */
@Service
public class AdminIpBanManagementService {

    private static final Set<String> BAN_TYPES =
        Set.of("access", "login", "register");

    private final DatabaseRuntimeSettingsService settingsService;
    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;
    private final AdminUserPrivilegeGuard privilegeGuard;

    public AdminIpBanManagementService(
        DatabaseRuntimeSettingsService settingsService,
        DatabaseClient databaseClient,
        TransactionalOperator transactionalOperator,
        AdminUserPrivilegeGuard privilegeGuard
    ) {
        this.settingsService = settingsService;
        this.databaseClient = databaseClient;
        this.transactionalOperator = transactionalOperator;
        this.privilegeGuard = privilegeGuard;
    }

    /* BEGIN：IP 封禁生命周期。 */

    /** 新增有效 IP 封禁规则。 */
    public Mono<Map<String, Object>> create(
        AdminAuthUser operator,
        Map<String, Object> request
    ) {
        privilegeGuard.requireSuperAdmin(operator, "新增 IP 封禁");
        DatabaseSettings settings = settings();
        IpBanInput input = input(request, null);

        DatabaseClient.GenericExecuteSpec insert = databaseClient.sql(
                "insert into " + table(settings, TableNames.IP_BANS)
                    + " (ip_value, ip_version, ban_type, reason, operator_id, "
                    + "started_at, expired_at, enabled) values "
                    + "(:ipValue, :ipVersion, :banType, :reason, :operatorId, "
                    + "current_timestamp, :expiredAt, :enabled)"
            )
            .bind("ipValue", input.rule().source())
            .bind("ipVersion", input.rule().version())
            .bind("banType", input.banType())
            .bind("reason", input.reason())
            .bind("operatorId", operator.id())
            .bind("enabled", input.enabled() ? 1 : 0);
        insert = bindNullable(
            insert,
            "expiredAt",
            input.expiredAt(),
            LocalDateTime.class
        );

        Mono<Long> work = insert.fetch()
            .rowsUpdated()
            .then(lastId(settings, input.rule().source(), operator.id()))
            .flatMap(id -> log(
                settings,
                operator,
                "ip_bans.create",
                id,
                "新增 IP 封禁",
                input.rule().source()
            ).thenReturn(id));

        return complete(work, "IP 封禁规则创建成功。");
    }

    /** 修改 IP、作用范围、原因、到期时间和启用状态。 */
    public Mono<Map<String, Object>> update(
        AdminAuthUser operator,
        long id,
        Map<String, Object> request
    ) {
        privilegeGuard.requireSuperAdmin(operator, "修改 IP 封禁");
        DatabaseSettings settings = settings();

        Mono<Long> work = requireRule(settings, id)
            .flatMap(current -> {
                IpBanInput input = input(request, current);
                DatabaseClient.GenericExecuteSpec update = databaseClient.sql(
                        "update " + table(settings, TableNames.IP_BANS)
                            + " set ip_value = :ipValue, ip_version = :ipVersion, "
                            + "ban_type = :banType, reason = :reason, "
                            + "expired_at = :expiredAt, enabled = :enabled, "
                            + "updated_at = current_timestamp where id = :id"
                    )
                    .bind("ipValue", input.rule().source())
                    .bind("ipVersion", input.rule().version())
                    .bind("banType", input.banType())
                    .bind("reason", input.reason())
                    .bind("enabled", input.enabled() ? 1 : 0)
                    .bind("id", id);
                update = bindNullable(
                    update,
                    "expiredAt",
                    input.expiredAt(),
                    LocalDateTime.class
                );
                return update.fetch()
                    .rowsUpdated()
                    .then(log(
                        settings,
                        operator,
                        "ip_bans.update",
                        id,
                        "修改 IP 封禁",
                        input.rule().source()
                    ))
                    .thenReturn(id);
            });

        return complete(work, "IP 封禁规则修改成功。");
    }

    /** 快速启用或停用现有规则，历史记录仍然保留。 */
    public Mono<Map<String, Object>> setEnabled(
        AdminAuthUser operator,
        long id,
        boolean enabled
    ) {
        privilegeGuard.requireSuperAdmin(
            operator,
            enabled ? "启用 IP 封禁" : "停用 IP 封禁"
        );
        DatabaseSettings settings = settings();

        Mono<Long> work = requireRule(settings, id)
            .then(databaseClient.sql(
                    "update " + table(settings, TableNames.IP_BANS)
                        + " set enabled = :enabled, updated_at = current_timestamp "
                        + "where id = :id"
                )
                .bind("enabled", enabled ? 1 : 0)
                .bind("id", id)
                .fetch()
                .rowsUpdated())
            .then(log(
                settings,
                operator,
                enabled ? "ip_bans.enable" : "ip_bans.disable",
                id,
                enabled ? "启用 IP 封禁" : "停用 IP 封禁",
                ""
            ))
            .thenReturn(id);

        return complete(work, enabled ? "IP 封禁已启用。" : "IP 封禁已停用。");
    }

    /** 物理删除选定规则；审计日志仍保留操作者和目标 ID。 */
    public Mono<Map<String, Object>> delete(
        AdminAuthUser operator,
        long id
    ) {
        privilegeGuard.requireSuperAdmin(operator, "删除 IP 封禁");
        DatabaseSettings settings = settings();

        Mono<Long> work = requireRule(settings, id)
            .flatMap(current -> databaseClient.sql(
                    "delete from " + table(settings, TableNames.IP_BANS)
                        + " where id = :id"
                )
                .bind("id", id)
                .fetch()
                .rowsUpdated()
                .then(log(
                    settings,
                    operator,
                    "ip_bans.delete",
                    id,
                    "删除 IP 封禁",
                    text(current.get("ip_value"))
                ))
                .thenReturn(id));

        return complete(work, "IP 封禁规则删除成功。");
    }

    /* END：IP 封禁生命周期。 */

    /* BEGIN：输入、数据库和审计工具。 */

    private IpBanInput input(
        Map<String, Object> request,
        Map<String, Object> current
    ) {
        String ipValue = requestText(
            request,
            "ipValue",
            current == null ? "" : text(current.get("ip_value"))
        );
        IpAddressRule rule = IpAddressRule.parse(ipValue);
        String banType = requestText(
            request,
            "banType",
            current == null ? "access" : text(current.get("ban_type"))
        ).toLowerCase(Locale.ROOT);
        if (!BAN_TYPES.contains(banType)) {
            throw new IllegalStateException("封禁范围只支持 access、login 或 register。");
        }

        String reason = requestText(
            request,
            "reason",
            current == null ? "后台手动封禁" : text(current.get("reason"))
        );
        if (reason.length() > 2_000) {
            throw new IllegalStateException("封禁原因不能超过 2000 个字符。");
        }
        LocalDateTime expiredAt = dateTime(
            request == null ? null : request.get("expiredAt"),
            current == null ? null : current.get("expired_at")
        );
        boolean enabled = bool(
            request == null ? null : request.get("enabled"),
            current == null || bool(current.get("enabled"), true)
        );
        return new IpBanInput(rule, banType, reason, expiredAt, enabled);
    }

    private Mono<Map<String, Object>> requireRule(
        DatabaseSettings settings,
        long id
    ) {
        return databaseClient.sql(
                "select * from " + table(settings, TableNames.IP_BANS)
                    + " where id = :id"
            )
            .bind("id", id)
            .map(this::rowMap)
            .one()
            .switchIfEmpty(Mono.error(new IllegalStateException("IP 封禁规则不存在。")));
    }

    private Mono<Long> lastId(
        DatabaseSettings settings,
        String ipValue,
        long operatorId
    ) {
        return databaseClient.sql(
                "select id from " + table(settings, TableNames.IP_BANS)
                    + " where ip_value = :ipValue and operator_id = :operatorId "
                    + "order by id desc limit 1"
            )
            .bind("ipValue", ipValue)
            .bind("operatorId", operatorId)
            .map((row, metadata) -> number(row.get("id")))
            .one();
    }

    private Mono<Void> log(
        DatabaseSettings settings,
        AdminAuthUser operator,
        String action,
        long id,
        String summary,
        String detail
    ) {
        return databaseClient.sql(
                "insert into " + table(settings, TableNames.ADMIN_OPERATION_LOGS)
                    + " (operator_id, action_key, target_type, target_id, summary, detail) "
                    + "values (:operatorId, :action, 'ip_ban', :targetId, :summary, :detail)"
            )
            .bind("operatorId", operator.id())
            .bind("action", action)
            .bind("targetId", id)
            .bind("summary", summary)
            .bind("detail", detail)
            .fetch()
            .rowsUpdated()
            .then();
    }

    private Mono<Map<String, Object>> complete(Mono<Long> work, String message) {
        return transactionalOperator.transactional(work)
            .map(id -> {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("id", id);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("message", message);
                result.put("data", data);
                return result;
            });
    }

    private DatabaseClient.GenericExecuteSpec bindNullable(
        DatabaseClient.GenericExecuteSpec spec,
        String name,
        Object value,
        Class<?> type
    ) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private Map<String, Object> rowMap(Row row, RowMetadata metadata) {
        Map<String, Object> result = new LinkedHashMap<>();
        metadata.getColumnMetadatas().forEach(column ->
            result.put(column.getName(), row.get(column.getName()))
        );
        return result;
    }

    private LocalDateTime dateTime(Object requested, Object fallback) {
        Object value = requested == null ? fallback : requested;
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        try {
            return LocalDateTime.parse(String.valueOf(value));
        } catch (Exception error) {
            throw new IllegalStateException("到期时间格式不正确，应使用 ISO 日期时间。", error);
        }
    }

    private boolean bool(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return "true".equalsIgnoreCase(String.valueOf(value))
            || "1".equals(String.valueOf(value));
    }

    private String requestText(
        Map<String, Object> request,
        String key,
        String fallback
    ) {
        Object value = request == null ? null : request.get(key);
        return value == null ? fallback : String.valueOf(value).trim();
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String table(DatabaseSettings settings, String logicalName) {
        return TableNameResolver.tableName(settings.tablePrefix(), logicalName);
    }

    private DatabaseSettings settings() {
        DatabaseSettings settings = settingsService.current();
        if (settings == null) {
            throw new IllegalStateException("尚未找到数据库运行配置。");
        }
        return settings.normalized();
    }

    private record IpBanInput(
        IpAddressRule rule,
        String banType,
        String reason,
        LocalDateTime expiredAt,
        boolean enabled
    ) {
    }

    /* END：输入、数据库和审计工具。 */
}
