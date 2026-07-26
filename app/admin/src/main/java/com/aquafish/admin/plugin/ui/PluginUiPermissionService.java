package com.aquafish.admin.plugin.ui;

import com.aquafish.admin.plugin.ui.PluginUiResourceService.Catalog;
import com.aquafish.admin.plugin.ui.PluginUiResourceService.Descriptor;
import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.TableNameResolver;
import com.aquafish.core.database.TableNames;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 从现有插件能力授权表读取宿主实际批准的能力。
 *
 * <p>查询异常时按空授权处理，确保数据库短暂异常不会把未批准能力错误开放给插件 UI。</p>
 */
@Service
public class PluginUiPermissionService {

    private final DatabaseRuntimeSettingsService settingsService;
    private final DatabaseClient databaseClient;

    public PluginUiPermissionService(
        DatabaseRuntimeSettingsService settingsService,
        DatabaseClient databaseClient
    ) {
        this.settingsService = settingsService;
        this.databaseClient = databaseClient;
    }

    public Mono<Catalog> enrich(Catalog catalog) {
        return Flux.fromIterable(catalog.items())
            .concatMap(descriptor ->
                grantedPermissions(descriptor.pluginId())
                    .map(descriptor::withGrantedPermissions)
            )
            .collectList()
            .map(items -> new Catalog(
                List.copyOf(items),
                catalog.failures()
            ));
    }

    Mono<Set<String>> grantedPermissions(String pluginId) {
        DatabaseSettings settings = settingsService.current();
        if (settings == null) {
            return Mono.just(Set.of());
        }
        DatabaseSettings normalized = settings.normalized();
        String plugins = table(normalized, TableNames.PLUGINS);
        String permissions = table(
            normalized,
            TableNames.PLUGIN_PERMISSIONS
        );
        return databaseClient.sql(
                "select pp.capability_key from " + permissions
                    + " pp join " + plugins
                    + " p on p.id = pp.plugin_id "
                    + "where p.plugin_key = :pluginKey "
                    + "and pp.granted_flag = 1 "
                    + "order by pp.capability_key"
            )
            .bind("pluginKey", pluginId)
            .map((row, metadata) ->
                row.get("capability_key", String.class)
            )
            .all()
            .filter(value -> value != null && !value.isBlank())
            .collect(
                LinkedHashSet<String>::new,
                (values, value) -> values.add(value.trim())
            )
            .map(Set::copyOf)
            .onErrorReturn(Set.of());
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
}
