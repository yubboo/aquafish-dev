package com.aquafish.core.database.migration.r2dbc;

import com.aquafish.core.database.TableNameResolver;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import name.nkonev.r2dbc.migrate.reader.MigrateResource;
import name.nkonev.r2dbc.migrate.reader.MigrateResourceReader;
import name.nkonev.r2dbc.migrate.reader.SpringResourceReader;

/**
 * Aquafish R2DBC 迁移资源读取器。
 *
 * <p>
 * 现有 V1～V4 SQL 中包含两个旧 Flyway 占位符：
 * </p>
 *
 * <ul>
 *     <li>${tablePrefix}</li>
 *     <li>${flyway:database}</li>
 * </ul>
 *
 * <p>
 * 本读取器在 SQL 进入 r2dbc-migrate 前进行严格替换，
 * 不使用全局系统属性，也不读取环境变量，
 * 防止其他进程配置污染迁移 SQL。
 * </p>
 */
public final class AquafishR2dbcMigrationResourceReader
    implements MigrateResourceReader {

    private static final String TABLE_PREFIX_PLACEHOLDER =
        "${tablePrefix}";

    private static final String DATABASE_PLACEHOLDER =
        "${flyway:database}";

    /**
     * 数据库名会被替换到 MySQL 反引号标识符中。
     *
     * <p>
     * 为避免标识符注入，只允许字母、数字和下划线。
     * </p>
     */
    private static final Pattern DATABASE_IDENTIFIER =
        Pattern.compile(
            "^[A-Za-z0-9_]{1,64}$"
        );

    private final MigrateResourceReader delegate;

    private final String tablePrefix;

    private final String databaseName;

    /**
     * 创建正式 Spring Classpath 资源读取器。
     */
    public AquafishR2dbcMigrationResourceReader(
        String tablePrefix,
        String databaseName
    ) {
        this(
            new SpringResourceReader(),
            tablePrefix,
            databaseName
        );
    }

    /**
     * 创建可测试的资源读取器。
     */
    AquafishR2dbcMigrationResourceReader(
        MigrateResourceReader delegate,
        String tablePrefix,
        String databaseName
    ) {
        this.delegate =
            Objects.requireNonNull(
                delegate,
                "迁移资源委托读取器不能为空。"
            );

        this.tablePrefix =
            TableNameResolver
                .normalizeConfiguredPrefix(
                    tablePrefix
                );

        this.databaseName =
            requireDatabaseIdentifier(
                databaseName
            );
    }

    @Override
    public List<MigrateResource> getResources(
        String resourcesPath
    ) {
        List<MigrateResource> resources =
            delegate.getResources(
                resourcesPath
            );

        if (resources == null) {
            throw new IllegalStateException(
                "迁移资源读取器返回了 null。"
            );
        }

        return resources
            .stream()
            .<MigrateResource>map(
                resource ->
                    new SubstitutedMigrationResource(
                        resource,
                        tablePrefix,
                        databaseName
                    )
            )
            .toList();
    }

    private String requireDatabaseIdentifier(
        String value
    ) {
        if (
            value == null
            || value.isBlank()
        ) {
            throw new IllegalStateException(
                "数据库名称不能为空。"
            );
        }

        String normalized =
            value.trim();

        if (
            !normalized.equals(value)
        ) {
            throw new IllegalStateException(
                "数据库名称不能包含首尾空格。"
            );
        }

        if (
            !DATABASE_IDENTIFIER
                .matcher(normalized)
                .matches()
        ) {
            throw new IllegalStateException(
                "数据库名称只能包含字母、数字和下划线，"
                    + "最大长度为 64。"
            );
        }

        return normalized;
    }

    /**
     * 对单个 SQL 资源执行受控占位符替换。
     */
    private static final class
        SubstitutedMigrationResource
        implements MigrateResource {

        private final MigrateResource delegate;

        private final String tablePrefix;

        private final String databaseName;

        private SubstitutedMigrationResource(
            MigrateResource delegate,
            String tablePrefix,
            String databaseName
        ) {
            this.delegate =
                Objects.requireNonNull(
                    delegate,
                    "单个迁移资源不能为空。"
                );

            this.tablePrefix =
                tablePrefix;

            this.databaseName =
                databaseName;
        }

        @Override
        public boolean isReadable() {
            return delegate.isReadable();
        }

        @Override
        public InputStream getInputStream()
            throws IOException {

            final String source;

            try (
                InputStream inputStream =
                    delegate.getInputStream()
            ) {
                source =
                    new String(
                        inputStream.readAllBytes(),
                        StandardCharsets.UTF_8
                    );
            }

            String substituted =
                source
                    .replace(
                        TABLE_PREFIX_PLACEHOLDER,
                        tablePrefix
                    )
                    .replace(
                        DATABASE_PLACEHOLDER,
                        databaseName
                    );

            if (
                substituted.contains(
                    TABLE_PREFIX_PLACEHOLDER
                )
                || substituted.contains(
                    DATABASE_PLACEHOLDER
                )
            ) {
                throw new IllegalStateException(
                    "迁移 SQL 中仍存在未替换的 Aquafish 占位符："
                        + getFilename()
                );
            }

            return new ByteArrayInputStream(
                substituted.getBytes(
                    StandardCharsets.UTF_8
                )
            );
        }

        @Override
        public String getFilename() {
            return delegate.getFilename();
        }
    }
}
