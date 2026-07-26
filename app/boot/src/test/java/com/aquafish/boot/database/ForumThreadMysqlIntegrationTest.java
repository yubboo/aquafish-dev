package com.aquafish.boot.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.DatabaseType;
import com.aquafish.core.database.TableNameResolver;
import com.aquafish.core.database.r2dbc.R2dbcConnectionFactoryBuilder;
import com.aquafish.forum.database.ForumTableNames;
import com.aquafish.forum.permission.ForumMemberActor;
import com.aquafish.forum.permission.ForumPermissions;
import com.aquafish.forum.section.ForumSectionRepository;
import com.aquafish.forum.section.database.R2dbcForumSectionRepository;
import com.aquafish.forum.thread.ForumModerationStatus;
import com.aquafish.forum.thread.ForumThreadCreateCommand;
import com.aquafish.forum.thread.ForumThreadQuery;
import com.aquafish.forum.thread.ForumThreadRepository;
import com.aquafish.forum.thread.ForumThreadService;
import com.aquafish.forum.thread.database.R2dbcForumThreadRepository;
import io.r2dbc.spi.ConnectionFactory;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 主题发布与列表的本地真实 MySQL R2DBC 验收测试。
 *
 * <p>测试不使用 Docker。只有显式提供 AQUAFISH_TEST_MYSQL_* 环境变量时才执行。
 * 每个测试创建带随机前缀的四张隔离表，结束后只删除本测试前缀的表，
 * 不读取或修改当前 Aquafish 实例的 aq_ 业务数据。</p>
 */
@EnabledIfEnvironmentVariable(named = "AQUAFISH_TEST_MYSQL_HOST", matches = ".+")
class ForumThreadMysqlIntegrationTest {

    private DatabaseClient databaseClient;
    private ForumThreadService service;
    private String tablePrefix;

    @BeforeEach
    void setUp() {
        tablePrefix = "ftit" + UUID.randomUUID().toString()
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
        ForumSectionRepository sectionRepository =
            new R2dbcForumSectionRepository(databaseClient, runtimeSettings);
        ForumThreadRepository threadRepository =
            new R2dbcForumThreadRepository(databaseClient, runtimeSettings);
        TransactionalOperator transactions = TransactionalOperator.create(
            new R2dbcTransactionManager(connectionFactory)
        );
        service = new ForumThreadService(
            sectionRepository,
            threadRepository,
            transactions
        );

        StepVerifier.create(createSchema())
            .verifyComplete();
    }

    @AfterEach
    void tearDown() {
        if (databaseClient == null || tablePrefix == null) {
            return;
        }
        StepVerifier.create(dropSchema())
            .verifyComplete();
    }

    @Test
    void shouldCommitGeneratedIdsFirstPostStatisticsOutboxAndList() {
        StepVerifier.create(
                service.publish(
                    member(),
                    new ForumThreadCreateCommand(
                        3L,
                        "真实 MySQL 主题",
                        "真实 MySQL 第一楼"
                    )
                )
                .flatMap(result -> Mono.zip(
                    scalar(
                        "select first_post_id from " + table(ForumTableNames.THREADS)
                            + " where id = " + result.threadId()
                    ),
                    scalar(
                        "select floor_number from " + table(ForumTableNames.POSTS)
                            + " where id = " + result.firstPostId()
                    ),
                    scalar(
                        "select thread_count from " + table(ForumTableNames.SECTIONS)
                            + " where id = 3"
                    ),
                    scalar(
                        "select post_count from " + table(ForumTableNames.SECTIONS)
                            + " where id = 3"
                    ),
                    scalar("select count(*) from " + table(ForumTableNames.NOTIFICATION_OUTBOX))
                ).map(values -> new DatabaseVerification(
                    result.threadId(),
                    result.firstPostId(),
                    result.moderationStatus(),
                    values.getT1(),
                    values.getT2(),
                    values.getT3(),
                    values.getT4(),
                    values.getT5()
                )))
            )
            .assertNext(result -> {
                assertTrue(result.threadId() > 0L);
                assertTrue(result.firstPostId() > 0L);
                assertEquals(ForumModerationStatus.APPROVED, result.moderationStatus());
                assertEquals(result.firstPostId(), result.persistedFirstPostId());
                assertEquals(1L, result.floorNumber());
                assertEquals(1L, result.threadCount());
                assertEquals(1L, result.postCount());
                assertEquals(1L, result.outboxCount());
            })
            .verifyComplete();

        StepVerifier.create(service.list(null, 3L, ForumThreadQuery.defaults()))
            .assertNext(page -> {
                assertEquals(1L, page.total());
                assertEquals(1, page.items().size());
                assertEquals("真实 MySQL 主题", page.items().getFirst().title());
            })
            .verifyComplete();
    }

    @Test
    void shouldRollbackThreadPostAndStatisticsWhenOutboxWriteFails() {
        StepVerifier.create(
                execute("drop table " + table(ForumTableNames.NOTIFICATION_OUTBOX))
                    .then(service.publish(
                        member(),
                        new ForumThreadCreateCommand(
                            3L,
                            "应回滚主题",
                            "应回滚第一楼"
                        )
                    ))
            )
            .expectError()
            .verify();

        StepVerifier.create(Mono.zip(
                scalar("select count(*) from " + table(ForumTableNames.THREADS)),
                scalar("select count(*) from " + table(ForumTableNames.POSTS)),
                scalar(
                    "select thread_count + post_count from "
                        + table(ForumTableNames.SECTIONS) + " where id = 3"
                )
            ))
            .assertNext(values -> {
                assertEquals(0L, values.getT1());
                assertEquals(0L, values.getT2());
                assertEquals(0L, values.getT3());
            })
            .verifyComplete();
    }

    /**
     * 仅创建仓储本步骤实际使用的最小 MySQL 表结构。
     */
    private Mono<Void> createSchema() {
        String sections = table(ForumTableNames.SECTIONS);
        String threads = table(ForumTableNames.THREADS);
        String posts = table(ForumTableNames.POSTS);
        String outbox = table(ForumTableNames.NOTIFICATION_OUTBOX);

        return execute(
                "create table " + sections + " ("
                    + "id bigint not null auto_increment primary key,"
                    + "parent_id bigint null,"
                    + "section_key varchar(120) not null unique,"
                    + "name varchar(120) not null,"
                    + "description text null,"
                    + "icon varchar(500) null,"
                    + "sort_order int not null default 0,"
                    + "visibility varchar(32) not null default 'PUBLIC',"
                    + "posting_policy varchar(32) not null default 'MEMBERS',"
                    + "moderation_policy varchar(32) not null default 'NONE',"
                    + "thread_count bigint not null default 0,"
                    + "post_count bigint not null default 0,"
                    + "enabled smallint not null default 1,"
                    + "created_by bigint not null,"
                    + "updated_by bigint not null,"
                    + "created_at datetime(3) not null default current_timestamp(3),"
                    + "updated_at datetime(3) not null default current_timestamp(3)"
                    + ") engine=InnoDB"
            )
            .then(execute(
                "create table " + threads + " ("
                    + "id bigint not null auto_increment primary key,"
                    + "section_id bigint not null,"
                    + "author_user_id bigint not null,"
                    + "title varchar(240) not null,"
                    + "status varchar(32) not null default 'OPEN',"
                    + "moderation_status varchar(32) not null default 'APPROVED',"
                    + "pinned_level smallint not null default 0,"
                    + "featured_level smallint not null default 0,"
                    + "reply_count bigint not null default 0,"
                    + "view_count bigint not null default 0,"
                    + "next_floor bigint not null default 2,"
                    + "first_post_id bigint null,"
                    + "last_post_id bigint null,"
                    + "last_reply_user_id bigint null,"
                    + "last_reply_at datetime(3) null,"
                    + "created_at datetime(3) not null default current_timestamp(3),"
                    + "updated_at datetime(3) not null default current_timestamp(3),"
                    + "deleted_at datetime(3) null"
                    + ") engine=InnoDB"
            ))
            .then(execute(
                "create table " + posts + " ("
                    + "id bigint not null auto_increment primary key,"
                    + "thread_id bigint not null,"
                    + "section_id bigint not null,"
                    + "author_user_id bigint not null,"
                    + "floor_number bigint not null,"
                    + "content_text longtext not null,"
                    + "quoted_post_id bigint null,"
                    + "status varchar(32) not null default 'PUBLISHED',"
                    + "moderation_status varchar(32) not null default 'APPROVED',"
                    + "edited_at datetime(3) null,"
                    + "created_at datetime(3) not null default current_timestamp(3),"
                    + "updated_at datetime(3) not null default current_timestamp(3),"
                    + "deleted_at datetime(3) null,"
                    + "unique key uk_thread_floor (thread_id, floor_number)"
                    + ") engine=InnoDB"
            ))
            .then(execute(
                "create table " + outbox + " ("
                    + "id bigint not null auto_increment primary key,"
                    + "event_key varchar(100) not null unique,"
                    + "event_type varchar(100) not null,"
                    + "aggregate_type varchar(32) not null,"
                    + "aggregate_id bigint not null,"
                    + "payload longtext not null,"
                    + "delivery_status varchar(32) not null default 'PENDING',"
                    + "attempt_count int not null default 0,"
                    + "available_at datetime(3) not null default current_timestamp(3),"
                    + "delivered_at datetime(3) null,"
                    + "last_error varchar(1000) null,"
                    + "created_at datetime(3) not null default current_timestamp(3),"
                    + "updated_at datetime(3) not null default current_timestamp(3)"
                    + ") engine=InnoDB"
            ))
            .then(execute(
                "insert into " + sections
                    + " (id, section_key, name, visibility, posting_policy, "
                    + "moderation_policy, enabled, created_by, updated_by) "
                    + "values (3, 'integration', '真实数据库测试板块', "
                    + "'PUBLIC', 'MEMBERS', 'NONE', 1, 1, 1)"
            ))
            .then();
    }

    /**
     * DROP TABLE IF EXISTS 只作用于当前测试随机前缀。
     */
    private Mono<Void> dropSchema() {
        return execute("drop table if exists " + table(ForumTableNames.NOTIFICATION_OUTBOX))
            .then(execute("drop table if exists " + table(ForumTableNames.POSTS)))
            .then(execute("drop table if exists " + table(ForumTableNames.THREADS)))
            .then(execute("drop table if exists " + table(ForumTableNames.SECTIONS)))
            .then();
    }

    private Mono<Long> execute(String sql) {
        return databaseClient.sql(sql)
            .fetch()
            .rowsUpdated();
    }

    private Mono<Long> scalar(String sql) {
        return databaseClient.sql(sql)
            .map((row, metadata) -> {
                Number value = row.get(0, Number.class);
                return value == null ? 0L : value.longValue();
            })
            .one();
    }

    private String table(String logicalName) {
        return TableNameResolver.tableName(tablePrefix, logicalName);
    }

    private ForumMemberActor member() {
        return new ForumMemberActor(
            9L,
            true,
            false,
            Set.of(
                ForumPermissions.THREAD_CREATE,
                ForumPermissions.THREAD_READ
            ),
            Set.of(),
            Set.of()
        );
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

    private record DatabaseVerification(
        long threadId,
        long firstPostId,
        ForumModerationStatus moderationStatus,
        long persistedFirstPostId,
        long floorNumber,
        long threadCount,
        long postCount,
        long outboxCount
    ) {
    }
}
