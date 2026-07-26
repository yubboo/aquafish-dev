package com.aquafish.user.security;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.R2dbcPaginationSql;
import com.aquafish.core.database.TableNameResolver;
import com.aquafish.core.database.TableNames;
import com.aquafish.core.network.IpAddressRule;
import java.util.Locale;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 有效 IP 封禁规则查询与匹配服务。
 *
 * <p>数据库只负责筛选“已启用且未过期”的候选记录，IPv4、IPv6 和 CIDR 的实际匹配
 * 交给 {@link IpAddressRule}，保证 MySQL 与 PostgreSQL 行为一致。历史非法规则会被
 * 忽略，不会让所有用户登录失败。</p>
 */
@Service
public class IpBanLookupService {

    private static final int MAX_ACTIVE_RULES = 5_000;

    private final DatabaseRuntimeSettingsService settingsService;
    private final DatabaseClient databaseClient;

    public IpBanLookupService(
        DatabaseRuntimeSettingsService settingsService,
        DatabaseClient databaseClient
    ) {
        this.settingsService = settingsService;
        this.databaseClient = databaseClient;
    }

    /**
     * 要求当前 IP 不命中 access 或指定场景规则。
     *
     * @param address 请求远端 IP
     * @param scope login 或 register
     */
    public Mono<Void> requireAllowed(String address, String scope) {
        String safeAddress = address == null ? "" : address.trim();
        String safeScope = scope == null ? "" : scope.trim().toLowerCase(Locale.ROOT);
        if (safeAddress.isBlank()) {
            return Mono.empty();
        }

        return activeRules()
            .filter(rule -> "access".equals(rule.banType())
                || safeScope.equals(rule.banType()))
            .filter(rule -> matches(rule.ipValue(), safeAddress))
            .next()
            .flatMap(rule -> Mono.error(new IpAccessBannedException(
                rule.reason().isBlank()
                    ? "当前 IP 已被禁止访问。"
                    : "当前 IP 已被禁止访问：" + rule.reason()
            )))
            .then();
    }

    private Flux<IpBanRule> activeRules() {
        DatabaseSettings settings = settings();
        String sql = R2dbcPaginationSql.limit(
            "select ip_value, ban_type, reason from "
                + TableNameResolver.tableName(
                    settings.tablePrefix(),
                    TableNames.IP_BANS
                )
                + " where enabled = 1 and "
                + "(expired_at is null or expired_at > current_timestamp) "
                + "order by id desc",
            MAX_ACTIVE_RULES
        );
        return databaseClient.sql(sql)
            .map((row, metadata) -> new IpBanRule(
                text(row.get("ip_value")),
                text(row.get("ban_type")).toLowerCase(Locale.ROOT),
                text(row.get("reason"))
            ))
            .all();
    }

    private boolean matches(String rule, String address) {
        try {
            return IpAddressRule.parse(rule).matches(address);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private DatabaseSettings settings() {
        DatabaseSettings settings = settingsService.current();
        if (settings == null) {
            throw new IllegalStateException("尚未找到数据库运行配置。");
        }
        return settings.normalized();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record IpBanRule(String ipValue, String banType, String reason) {
    }
}
