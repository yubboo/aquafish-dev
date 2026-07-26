package com.aquafish.user.profile;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.TableNameResolver;
import com.aquafish.core.database.TableNames;
import com.aquafish.user.auth.MemberAuthUser;
import io.r2dbc.spi.Row;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 当前用户个人中心查询服务。
 *
 * <p>用户身份只接收 Spring Security 已验证的 {@link MemberAuthUser}，不接受前端提交
 * 的 uid，避免普通用户读取他人邮箱和私有资料。</p>
 */
@Service
public class MemberProfileService {

    private final DatabaseRuntimeSettingsService databaseSettings;
    private final DatabaseClient databaseClient;

    public MemberProfileService(
        DatabaseRuntimeSettingsService databaseSettings,
        DatabaseClient databaseClient
    ) {
        this.databaseSettings = databaseSettings;
        this.databaseClient = databaseClient;
    }

    public Mono<MemberProfile> current(MemberAuthUser authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.id() <= 0L) {
            return Mono.error(new IllegalStateException("当前用户登录状态无效。"));
        }

        DatabaseSettings settings = databaseSettings.current().normalized();
        String users = table(settings, TableNames.USERS);
        String groups = table(settings, TableNames.USER_GROUPS);
        String profiles = table(settings, TableNames.USER_PROFILES);
        String statistics = table(settings, TableNames.USER_STATISTICS);
        String userRoles = table(settings, TableNames.USER_ROLES);
        String roles = table(settings, TableNames.ROLES);

        String sql = "select u.id, u.uid, u.public_id, u.username, "
            + "coalesce(u.email, '') as email, "
            + "coalesce(u.display_name, u.username) as display_name, "
            + "coalesce(u.avatar, '') as avatar, coalesce(g.group_key, '') as group_key, "
            + "coalesce(g.name, '') as group_name, coalesce(p.bio, '') as bio, "
            + "coalesce(p.signature, '') as signature, coalesce(s.points, 0) as points, "
            + "coalesce(s.threads_count, 0) as threads_count, "
            + "coalesce(s.posts_count, 0) as posts_count, "
            + "coalesce(s.comments_count, 0) as comments_count, "
            + "coalesce(s.followers_count, 0) as followers_count, "
            + "coalesce(s.following_count, 0) as following_count, "
            + "coalesce(s.friends_count, 0) as friends_count, u.created_at, "
            + "u.last_login_at, "
            + "case when exists (select 1 from " + userRoles + " ur join " + roles
            + " r on r.id = ur.role_id where ur.user_id = u.id "
            + "and r.role_key in ('admin', 'super_admin')) then 1 else 0 end as admin_access "
            + "from " + users + " u left join " + groups + " g on g.id = u.group_id "
            + "left join " + profiles + " p on p.user_id = u.id "
            + "left join " + statistics + " s on s.user_id = u.id "
            + "where u.id = :userId and u.uid is not null "
            + "and upper(u.status) = 'ACTIVE' limit 1";

        return databaseClient.sql(sql)
            .bind("userId", authenticatedUser.id())
            .map((row, metadata) -> map(row))
            .one()
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "当前用户不存在或账号已停用。"
            )));
    }

    private MemberProfile map(Row row) {
        return new MemberProfile(
            number(row, "uid"),
            text(row, "public_id"),
            text(row, "username"),
            text(row, "email"),
            text(row, "display_name"),
            text(row, "avatar"),
            text(row, "group_key"),
            text(row, "group_name"),
            text(row, "bio"),
            text(row, "signature"),
            number(row, "points"),
            number(row, "threads_count"),
            number(row, "posts_count"),
            number(row, "comments_count"),
            number(row, "followers_count"),
            number(row, "following_count"),
            number(row, "friends_count"),
            valueText(row, "created_at"),
            valueText(row, "last_login_at"),
            booleanValue(row, "admin_access")
        );
    }

    private long number(Row row, String column) {
        Number value = row.get(column, Number.class);
        return value == null ? 0L : value.longValue();
    }

    private String text(Row row, String column) {
        String value = row.get(column, String.class);
        return value == null ? "" : value;
    }

    private String valueText(Row row, String column) {
        Object value = row.get(column);
        return value == null ? "" : String.valueOf(value);
    }

    private boolean booleanValue(Row row, String column) {
        Object value = row.get(column);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.longValue() > 0L;
        }
        return value != null && ("true".equalsIgnoreCase(String.valueOf(value))
            || "1".equals(String.valueOf(value)));
    }

    private String table(DatabaseSettings settings, String logicalName) {
        return TableNameResolver.tableName(settings.tablePrefix(), logicalName);
    }
}
