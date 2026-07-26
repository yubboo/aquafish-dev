package com.aquafish.core.database;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Aquafish 正式业务表白名单。
 *
 * <p>表名直接从 {@link TableNames} 的 public static final String 常量读取，
 * 避免再维护第二份 71 表清单。安装器只精确识别这些表，不按前缀模糊匹配。</p>
 */
public final class AquafishDatabaseTableCatalog {

    private static final List<String> LOGICAL_TABLES =
        loadLogicalTables();

    private AquafishDatabaseTableCatalog() {
    }

    /**
     * 返回不带前缀的正式逻辑表名。
     */
    public static List<String> logicalTableNames() {
        return LOGICAL_TABLES;
    }

    /**
     * 根据安装器当前前缀生成精确真实表名。
     */
    public static List<String> physicalTableNames(
        DatabaseSettings settings
    ) {
        DatabaseSettings safe =
            Objects.requireNonNull(
                settings,
                "数据库配置不能为空。"
            ).normalized();

        return LOGICAL_TABLES
            .stream()
            .map(
                logicalName ->
                    TableNameResolver.tableName(
                        safe.tablePrefix(),
                        logicalName
                    )
            )
            .toList();
    }

    /**
     * 当前代码登记的正式业务表数量。
     */
    public static int expectedTableCount() {
        return LOGICAL_TABLES.size();
    }

    /**
     * 从唯一 TableNames 目录读取正式表名。
     */
    private static List<String> loadLogicalTables() {
        try {
            return Arrays
                .stream(
                    TableNames.class.getFields()
                )
                .filter(
                    field ->
                        field.getType()
                            == String.class
                        && Modifier.isPublic(
                            field.getModifiers()
                        )
                        && Modifier.isStatic(
                            field.getModifiers()
                        )
                        && Modifier.isFinal(
                            field.getModifiers()
                        )
                )
                .map(
                    AquafishDatabaseTableCatalog
                        ::stringValue
                )
                .distinct()
                .sorted()
                .toList();
        } catch (
            RuntimeException error
        ) {
            throw new ExceptionInInitializerError(
                error
            );
        }
    }

    /**
     * 安全读取单个 String 常量。
     */
    private static String stringValue(
        Field field
    ) {
        try {
            Object value =
                field.get(null);

            if (
                !(value instanceof String text)
                || text.isBlank()
            ) {
                throw new IllegalStateException(
                    "TableNames 包含空表名："
                        + field.getName()
                );
            }

            return text;
        } catch (
            IllegalAccessException error
        ) {
            throw new IllegalStateException(
                "无法读取 TableNames："
                    + field.getName(),
                error
            );
        }
    }
}
