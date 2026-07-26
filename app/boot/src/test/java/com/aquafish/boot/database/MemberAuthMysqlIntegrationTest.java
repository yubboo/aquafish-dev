package com.aquafish.boot.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.DatabaseType;
import com.aquafish.core.database.TableNameResolver;
import com.aquafish.core.database.TableNames;
import com.aquafish.core.database.r2dbc.R2dbcConnectionFactoryBuilder;
import com.aquafish.core.install.AuthoritativeInstallStatusService;
import com.aquafish.user.auth.MemberAuthService;
import com.aquafish.user.auth.MemberLoginMetadata;
import com.aquafish.user.auth.MemberLoginRequest;
import io.r2dbc.spi.ConnectionFactory;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 前台会员数据库会话的本地真实 MySQL R2DBC 验收测试。
 *
 * <p>测试使用随机表前缀创建六张隔离表，不读取或修改当前 Aquafish 实例数据。
 * 只有显式提供 AQUAFISH_TEST_MYSQL_* 环境变量时执行。</p>
 */
@EnabledIfEnvironmentVariable(named = "AQUAFISH_TEST_MYSQL_HOST", matches = ".+")
class MemberAuthMysqlIntegrationTest {

    private static final String PASSWORD = "correct-member-password";

    private DatabaseClient databaseClient;
    private MemberAuthService authService;
    private String tablePrefix;

    @BeforeEach
    void setUp() {
        tablePrefix = "mait" + UUID.randomUUID().toString()
            .replace("-", "")
            .substring(0, 8) + "_";
        DatabaseSettings settings = mysqlSettings(tablePrefix);
        ConnectionFactory connectionFactory =
            R2dbcConnectionFactoryBuilder.create(settings);
        databaseClient = DatabaseClient.create(connectionFactory);
        DatabaseRuntimeSettingsService runtimeSettings =
            new DatabaseRuntimeSettingsService(
                settings.type().value(),
                settings.host(),
                settings.port(),
                settings.name(),
                settings.username(),
                settings.password(),
                settings.tablePrefix()
            );
        AuthoritativeInstallStatusService installStatus = mock(
            AuthoritativeInstallStatusService.class
        );
        when(installStatus.requireInstalled()).thenReturn(Mono.empty());
        authService = new MemberAuthService(
            runtimeSettings,
            databaseClient,
            TransactionalOperator.create(
                new R2dbcTransactionManager(connectionFactory)
            ),
            installStatus
        );

        StepVerifier.create(createSchema().then(seedMember()))
            .verifyComplete();
    }

    @AfterEach
    void tearDown() {
        if (databaseClient == null || tablePrefix == null) {
            return;
        }
        StepVerifier.create(dropSchema()).verifyComplete();
    }

    @Test
    void shouldStoreOnlyHashLoadPermissionsApplyBanAndRevokeSession() {
        AtomicReference<String> rawToken = new AtomicReference<>();

        StepVerifier.create(authService.login(
                new MemberLoginRequest("member@example.com", PASSWORD, false),
                new MemberLoginMetadata("127.0.0.1", "Aquafish integration test")
            )
            .flatMap(token -> {
                rawToken.set(token.accessToken());
                return Mono.zip(
                    countByToken(token.accessToken()),
                    countByToken(sha256(token.accessToken())),
                    authService.authenticate(token.accessToken())
                ).map(values -> new LoginVerification(
                    token.accessToken(),
                    token.user().id(),
                    values.getT1(),
                    values.getT2(),
                    values.getT3().permissions().size()
                ));
            }))
            .assertNext(result -> {
                assertTrue(result.rawToken().length() >= 40);
                assertNotEquals(result.rawToken(), sha256(result.rawToken()));
                assertEquals(9L, result.userId());
                assertEquals(0L, result.rawTokenRows());
                assertEquals(1L, result.hashedTokenRows());
                assertEquals(2, result.permissionCount());
            })
            .verifyComplete();

        StepVerifier.create(insertPostBan()
                .then(authService.authenticate(rawToken.get())))
            .assertNext(user -> assertTrue(user.forumPostingBanned()))
            .verifyComplete();

        StepVerifier.create(authService.logout(rawToken.get())
                .flatMap(result -> authService.authenticate(rawToken.get())
                    .hasElement()
                    .map(authenticated -> new LogoutVerification(
                        result.revoked(),
                        authenticated
                    ))))
            .assertNext(result -> {
                assertTrue(result.revoked());
                assertFalse(result.authenticatedAfterLogout());
            })
            .verifyComplete();
    }

    @Test
    void invalidPasswordShouldNotCreateSessionAndShouldWriteFailureAudit() {
        StepVerifier.create(authService.login(
                new MemberLoginRequest("member", "wrong-password", false),
                MemberLoginMetadata.empty()
            ))
            .expectErrorMatches(error ->
                error.getMessage().contains("用户名或密码错误")
            )
            .verify();

        StepVerifier.create(Mono.zip(
                scalar("select count(*) from " + table(TableNames.USER_SESSIONS)),
                scalar("select count(*) from " + table(TableNames.USER_LOGIN_LOGS)
                    + " where login_result = 'FAILED'")
            ))
            .assertNext(values -> {
                assertEquals(0L, values.getT1());
                assertEquals(1L, values.getT2());
            })
            .verifyComplete();
    }

    private Mono<Void> createSchema() {
        return execute("create table " + table(TableNames.USER_GROUPS) + " ("
                + "id bigint not null primary key,"
                + "group_key varchar(64) not null unique"
                + ") engine=InnoDB")
            .then(execute("create table " + table(TableNames.USERS) + " ("
                + "id bigint not null primary key,"
                + "public_id varchar(64) not null unique,"
                + "username varchar(64) not null unique,"
                + "email varchar(191) null unique,"
                + "password_hash varchar(255) not null,"
                + "display_name varchar(100) null,"
                + "avatar varchar(500) null,"
                + "status varchar(32) not null default 'ACTIVE',"
                + "group_id bigint null,"
                + "last_login_at datetime(3) null,"
                + "last_login_ip varchar(45) null,"
                + "last_user_agent varchar(500) null,"
                + "login_count bigint not null default 0,"
                + "updated_at datetime(3) not null default current_timestamp(3)"
                + ") engine=InnoDB"))
            .then(execute("create table " + table(TableNames.USER_GROUP_PERMISSIONS) + " ("
                + "id bigint not null auto_increment primary key,"
                + "group_id bigint not null,"
                + "permission_key varchar(160) not null,"
                + "unique key uk_group_permission (group_id, permission_key)"
                + ") engine=InnoDB"))
            .then(execute("create table " + table(TableNames.USER_BANS) + " ("
                + "id bigint not null auto_increment primary key,"
                + "user_id bigint not null,"
                + "ban_type varchar(50) not null,"
                + "started_at datetime(3) not null default current_timestamp(3),"
                + "expired_at datetime(3) null,"
                + "enabled smallint not null default 1"
                + ") engine=InnoDB"))
            .then(execute("create table " + table(TableNames.USER_SESSIONS) + " ("
                + "id bigint not null auto_increment primary key,"
                + "user_id bigint not null,"
                + "session_type varchar(32) not null,"
                + "token_hash varchar(64) not null unique,"
                + "ip_address varchar(45) null,"
                + "user_agent varchar(500) null,"
                + "created_at datetime(3) not null default current_timestamp(3),"
                + "last_seen_at datetime(3) not null default current_timestamp(3),"
                + "expires_at datetime(3) not null,"
                + "revoked_at datetime(3) null,"
                + "revoke_reason varchar(255) null"
                + ") engine=InnoDB"))
            .then(execute("create table " + table(TableNames.USER_LOGIN_LOGS) + " ("
                + "id bigint not null auto_increment primary key,"
                + "user_id bigint null,"
                + "login_name varchar(191) not null,"
                + "login_result varchar(32) not null,"
                + "failure_reason varchar(500) null,"
                + "ip_address varchar(45) null,"
                + "remote_address varchar(45) null,"
                + "x_forwarded_for varchar(500) null,"
                + "x_real_ip varchar(45) null,"
                + "user_agent varchar(500) null,"
                + "created_at datetime(3) not null default current_timestamp(3)"
                + ") engine=InnoDB"))
            .then();
    }

    private Mono<Void> seedMember() {
        String passwordHash = new BCryptPasswordEncoder().encode(PASSWORD);
        return execute("insert into " + table(TableNames.USER_GROUPS)
                + " (id, group_key) values (1, 'member')")
            .then(databaseClient.sql("insert into " + table(TableNames.USERS)
                    + " (id, public_id, username, email, password_hash, display_name, "
                    + "status, group_id) values (9, 'AQUA_9', 'member', "
                    + "'member@example.com', :passwordHash, '测试会员', 'ACTIVE', 1)")
                .bind("passwordHash", passwordHash)
                .fetch()
                .rowsUpdated())
            .then(execute("insert into " + table(TableNames.USER_GROUP_PERMISSIONS)
                + " (group_id, permission_key) values "
                + "(1, 'forum.thread.read'), (1, 'forum.thread.create')"))
            .then();
    }

    private Mono<Void> insertPostBan() {
        return execute("insert into " + table(TableNames.USER_BANS)
            + " (user_id, ban_type, enabled) values (9, 'post', 1)")
            .then();
    }

    private Mono<Long> countByToken(String value) {
        return databaseClient.sql("select count(*) as row_count from "
                + table(TableNames.USER_SESSIONS) + " where token_hash = :value")
            .bind("value", value)
            .map((row, metadata) -> {
                Number number = row.get("row_count", Number.class);
                return number == null ? 0L : number.longValue();
            })
            .one();
    }

    private Mono<Long> execute(String sql) {
        return databaseClient.sql(sql).fetch().rowsUpdated();
    }

    private Mono<Long> scalar(String sql) {
        return databaseClient.sql(sql)
            .map((row, metadata) -> {
                Number number = row.get(0, Number.class);
                return number == null ? 0L : number.longValue();
            })
            .one();
    }

    private Mono<Void> dropSchema() {
        return execute("drop table if exists " + table(TableNames.USER_LOGIN_LOGS))
            .then(execute("drop table if exists " + table(TableNames.USER_SESSIONS)))
            .then(execute("drop table if exists " + table(TableNames.USER_BANS)))
            .then(execute("drop table if exists "
                + table(TableNames.USER_GROUP_PERMISSIONS)))
            .then(execute("drop table if exists " + table(TableNames.USERS)))
            .then(execute("drop table if exists " + table(TableNames.USER_GROUPS)))
            .then();
    }

    private String table(String logicalName) {
        return TableNameResolver.tableName(tablePrefix, logicalName);
    }

    private String sha256(String value) {
        try {
            byte[] bytes = java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private DatabaseSettings mysqlSettings(String prefix) {
        return new DatabaseSettings(
            DatabaseType.MYSQL,
            required("HOST"),
            integer("PORT", 3306),
            required("DATABASE"),
            required("USERNAME"),
            environment("PASSWORD", ""),
            prefix
        ).normalized();
    }

    private String required(String suffix) {
        String value = System.getenv("AQUAFISH_TEST_MYSQL_" + suffix);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "缺少测试环境变量：AQUAFISH_TEST_MYSQL_" + suffix
            );
        }
        return value;
    }

    private int integer(String suffix, int fallback) {
        String value = System.getenv("AQUAFISH_TEST_MYSQL_" + suffix);
        return value == null || value.isBlank()
            ? fallback
            : Integer.parseInt(value);
    }

    private String environment(String suffix, String fallback) {
        String value = System.getenv("AQUAFISH_TEST_MYSQL_" + suffix);
        return value == null ? fallback : value;
    }

    private record LoginVerification(
        String rawToken,
        long userId,
        long rawTokenRows,
        long hashedTokenRows,
        int permissionCount
    ) {
    }

    private record LogoutVerification(
        boolean revoked,
        boolean authenticatedAfterLogout
    ) {
    }
}
