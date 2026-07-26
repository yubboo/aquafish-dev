package com.aquafish.core.database;

import com.aquafish.core.database.r2dbc.R2dbcConnectionFactoryBuilder;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 安装向导使用的响应式数据库连接测试器。
 *
 * <p>只测试用户当前选择的一种数据库，不保存配置、不执行迁移。</p>
 */
@Service
public class DatabaseConnectionTester {

    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(8);

    public Mono<DatabaseConnectionTestResult> test(DatabaseSettings request) {
        return Mono.defer(() -> {
            long startedAtNanos = System.nanoTime();
            final DatabaseSettings settings;

            try {
                settings = safeSettings(request);
            } catch (Exception error) {
                DatabaseSettings fallback =
                    DatabaseSettings.defaultMysql();

                return Mono.just(
                    DatabaseConnectionTestResult.failure(
                        fallback,
                        R2dbcConnectionFactoryBuilder
                            .displayUrl(fallback),
                        elapsedMillis(startedAtNanos),
                        rootMessage(error)
                    )
                );
            }

            String connectionUrl =
                R2dbcConnectionFactoryBuilder
                    .displayUrl(settings);

            if (!settings.hasRequiredFields()) {
                return Mono.just(
                    DatabaseConnectionTestResult.failure(
                        settings,
                        connectionUrl,
                        elapsedMillis(startedAtNanos),
                        "数据库配置不完整，请检查主机、端口、数据库名和用户名。"
                    )
                );
            }

            final ConnectionFactory connectionFactory;

            try {
                connectionFactory =
                    R2dbcConnectionFactoryBuilder
                        .create(settings);
            } catch (Exception error) {
                return Mono.just(
                    DatabaseConnectionTestResult.failure(
                        settings,
                        connectionUrl,
                        elapsedMillis(startedAtNanos),
                        rootMessage(error)
                    )
                );
            }

            return Mono.usingWhen(
                    Mono.from(
                        connectionFactory.create()
                    ),
                    connection -> success(
                        settings,
                        connectionUrl,
                        connectionFactory,
                        connection,
                        startedAtNanos
                    ),
                    connection ->
                        Mono.from(
                            connection.close()
                        )
                )
                .timeout(CONNECTION_TIMEOUT)
                .onErrorResume(
                    error -> Mono.just(
                        DatabaseConnectionTestResult
                            .failure(
                                settings,
                                connectionUrl,
                                elapsedMillis(
                                    startedAtNanos
                                ),
                                rootMessage(error)
                            )
                    )
                );
        });
    }

    private Mono<DatabaseConnectionTestResult> success(
        DatabaseSettings settings,
        String connectionUrl,
        ConnectionFactory connectionFactory,
        Connection connection,
        long startedAtNanos
    ) {
        return Mono.just(DatabaseConnectionTestResult.success(
            settings,
            connectionUrl,
            elapsedMillis(startedAtNanos),
            connection.getMetadata().getDatabaseProductName(),
            connection.getMetadata().getDatabaseVersion(),
            connectionFactory.getMetadata().getName(),
            null
        ));
    }

    /**
     * 把纳秒计时转换成毫秒。
     *
     * 极快的本机连接至少返回 1 ms，避免页面显示为 0 秒。
     */
    private long elapsedMillis(
        long startedAtNanos
    ) {
        long elapsed =
            TimeUnit.NANOSECONDS.toMillis(
                Math.max(
                    0L,
                    System.nanoTime()
                        - startedAtNanos
                )
            );

        return Math.max(1L, elapsed);
    }

    private DatabaseSettings safeSettings(DatabaseSettings request) {
        return request == null
            ? DatabaseSettings.defaultMysql()
            : request.normalized();
    }

    private String rootMessage(Throwable error) {
        if (error == null) {
            return "未知错误";
        }

        Throwable current = error;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();
        return message == null || message.isBlank()
            ? current.getClass().getName()
            : message;
    }
}
