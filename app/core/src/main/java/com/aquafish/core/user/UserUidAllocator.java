package com.aquafish.core.user;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.TableNameResolver;
import com.aquafish.core.database.TableNames;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 用户展示 UID 的事务分配器。
 *
 * <p>数据库内部主键 {@code users.id} 永不回收；本服务只分配独立的
 * {@code users.uid}。调用方必须把 {@link #allocate()} 与用户 INSERT 放在同一个
 * 响应式事务中，单例锁行才能覆盖“读取空缺值 → 写入用户”的完整临界区。</p>
 *
 * <p>分配顺序固定为最小可用正整数，例如现有 UID 为 1、2、4 时返回 3。
 * 真实表名始终由 {@link TableNameResolver} 根据实例表前缀生成，没有写死
 * {@code aq_}。</p>
 */
@Service
public class UserUidAllocator {

    private final DatabaseRuntimeSettingsService settingsService;
    private final DatabaseClient databaseClient;

    public UserUidAllocator(
        DatabaseRuntimeSettingsService settingsService,
        DatabaseClient databaseClient
    ) {
        this.settingsService = settingsService;
        this.databaseClient = databaseClient;
    }

    /**
     * 锁定 UID 分配器并返回当前最小空缺 UID。
     *
     * @return 大于 0 的可用 UID
     */
    public Mono<Long> allocate() {
        DatabaseSettings settings = currentSettings();
        String users = table(settings, TableNames.USERS);
        String allocator = table(settings, TableNames.USER_UID_ALLOCATOR);

        /*
         * BEGIN：跨实例 UID 分配临界区。
         *
         * 第一条 SQL 获取数据库行锁；第二条 SQL 只在持锁事务内计算空缺值。
         * 调用方完成用户 INSERT 并提交事务后，其他注册请求才能继续分配。
         */
        Mono<Long> lock = databaseClient.sql(
                "select id from " + allocator + " where id = 1 for update"
            )
            .map((row, metadata) -> {
                Number value = row.get("id", Number.class);
                return value == null ? 0L : value.longValue();
            })
            .one()
            .filter(value -> value == 1L)
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "用户 UID 分配锁尚未初始化，请先执行数据库迁移。"
            )));

        String smallestGapSql = "select case "
            + "when exists (select 1 from " + users + " where uid = 1) then "
            + "coalesce((select min(uid_source.uid + 1) from " + users
            + " uid_source left join " + users
            + " occupied on occupied.uid = uid_source.uid + 1 "
            + "where uid_source.uid is not null and occupied.uid is null), 1) "
            + "else 1 end as next_uid";

        return lock.then(databaseClient.sql(smallestGapSql)
                .map((row, metadata) -> {
                    Number value = row.get("next_uid", Number.class);
                    return value == null ? 0L : value.longValue();
                })
                .one())
            .filter(uid -> uid > 0L)
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "无法分配有效的用户 UID。"
            )));
        // END：跨实例 UID 分配临界区。
    }

    private DatabaseSettings currentSettings() {
        DatabaseSettings settings = settingsService.current();
        if (settings == null) {
            throw new IllegalStateException("尚未找到数据库运行配置。");
        }
        return settings.normalized();
    }

    private String table(DatabaseSettings settings, String logicalName) {
        return TableNameResolver.tableName(settings.tablePrefix(), logicalName);
    }
}
