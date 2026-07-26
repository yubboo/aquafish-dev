package com.aquafish.core.database.migration;

import com.aquafish.core.database.DatabaseType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Aquafish 模块化数据库迁移目录注册中心。
 *
 * <p>该类负责收集所有
 * {@link DatabaseMigrationLocationProvider}
 * 实现，并将各模块声明的迁移目录统一提供给 Flyway。</p>
 *
 * <p>当前职责：</p>
 *
 * <ol>
 *     <li>校验模块标识；</li>
 *     <li>拒绝重复模块注册；</li>
 *     <li>按照模块 order 排序；</li>
 *     <li>根据数据库类型收集迁移目录；</li>
 *     <li>校验迁移目录归属；</li>
 *     <li>拒绝重复迁移目录；</li>
 *     <li>拒绝路径穿越和非法路径。</li>
 * </ol>
 *
 * <p>该注册中心不负责：</p>
 *
 * <ol>
 *     <li>连接数据库；</li>
 *     <li>执行 Flyway migrate；</li>
 *     <li>执行 baseline；</li>
 *     <li>执行 repair；</li>
 *     <li>执行 clean；</li>
 *     <li>读取迁移 SQL 内容；</li>
 *     <li>修改真实数据库。</li>
 * </ol>
 */
@Component
public final class DatabaseMigrationLocationRegistry {

    /**
     * 模块标识规则。
     *
     * <p>允许：</p>
     *
     * <pre>
     * core
     * user
     * cms
     * bbs
     * user-center
     * </pre>
     *
     * <p>禁止：</p>
     *
     * <pre>
     * Core
     * user_center
     * user center
     * ../user
     * </pre>
     */
    private static final Pattern MODULE_KEY_PATTERN =
        Pattern.compile(
            "^[a-z][a-z0-9-]{0,63}$"
        );

    /**
     * 所有经过校验和排序的迁移目录提供者。
     */
    private final List<DatabaseMigrationLocationProvider>
        providers;

    /**
     * 构造模块迁移目录注册中心。
     *
     * <p>Spring 会自动注入当前 ApplicationContext 中
     * 所有 DatabaseMigrationLocationProvider 实现。</p>
     *
     * <p>构造阶段只校验模块本身，
     * 数据库类型对应的目录在 locations() 调用时校验。</p>
     *
     * @param providers 模块迁移目录提供者
     */
    public DatabaseMigrationLocationRegistry(
        List<DatabaseMigrationLocationProvider> providers
    ) {
        List<DatabaseMigrationLocationProvider>
            safeProviders =
            providers == null
                ? List.of()
                : providers;

        this.providers =
            validateAndSortProviders(
                safeProviders
            );
    }

    /**
     * 返回指定数据库类型的全部迁移目录。
     *
     * <p>目录顺序遵循：</p>
     *
     * <ol>
     *     <li>provider.order() 从小到大；</li>
     *     <li>order 相同时，moduleKey 按字母排序；</li>
     *     <li>同一个模块内部保持 locations() 返回顺序。</li>
     * </ol>
     *
     * <p>注意：</p>
     *
     * <p>该顺序只是 Flyway locations 的注册顺序。
     * SQL 最终执行顺序仍然由全局唯一的
     * V1、V2、V3 等版本号决定。</p>
     *
     * @param databaseType 数据库类型
     * @return 不可修改的迁移目录列表
     */
    public List<String> locations(
        DatabaseType databaseType
    ) {
        Objects.requireNonNull(
            databaseType,
            "数据库类型不能为空。"
        );

        Set<String> collectedLocations =
            new LinkedHashSet<>();

        for (
            DatabaseMigrationLocationProvider provider
                : providers
        ) {
            String moduleKey =
                validatedModuleKey(
                    provider
                );

            List<String> moduleLocations =
                provider.locations(
                    databaseType
                );

            if (moduleLocations == null) {
                throw new IllegalStateException(
                    "模块 "
                        + moduleKey
                        + " 返回了 null 迁移目录列表。"
                );
            }

            for (String location : moduleLocations) {
                String validatedLocation =
                    validateLocation(
                        moduleKey,
                        databaseType,
                        location
                    );

                boolean added =
                    collectedLocations.add(
                        validatedLocation
                    );

                if (!added) {
                    throw new IllegalStateException(
                        "检测到重复的数据库迁移目录："
                            + validatedLocation
                    );
                }
            }
        }

        return List.copyOf(
            collectedLocations
        );
    }

    /**
     * 返回已经注册的模块标识。
     *
     * <p>主要用于：</p>
     *
     * <ol>
     *     <li>安装器迁移预览；</li>
     *     <li>后台数据库诊断；</li>
     *     <li>自动化测试；</li>
     *     <li>后续插件迁移状态展示。</li>
     * </ol>
     *
     * @return 按加载顺序排列的模块标识
     */
    public List<String> registeredModuleKeys() {
        List<String> moduleKeys =
            new ArrayList<>(
                providers.size()
            );

        for (
            DatabaseMigrationLocationProvider provider
                : providers
        ) {
            moduleKeys.add(
                validatedModuleKey(
                    provider
                )
            );
        }

        return List.copyOf(
            moduleKeys
        );
    }

    /**
     * 返回注册的迁移提供者数量。
     *
     * @return 提供者数量
     */
    public int providerCount() {
        return providers.size();
    }

    /**
     * 校验并排序所有提供者。
     */
    private List<DatabaseMigrationLocationProvider>
        validateAndSortProviders(
            List<DatabaseMigrationLocationProvider>
                source
        ) {
        List<DatabaseMigrationLocationProvider>
            sortedProviders =
            new ArrayList<>(
                source.size()
            );

        Set<String> moduleKeys =
            new LinkedHashSet<>();

        for (
            DatabaseMigrationLocationProvider provider
                : source
        ) {
            if (provider == null) {
                throw new IllegalStateException(
                    "数据库迁移目录提供者不能为空。"
                );
            }

            String moduleKey =
                validatedModuleKey(
                    provider
                );

            boolean added =
                moduleKeys.add(
                    moduleKey
                );

            if (!added) {
                throw new IllegalStateException(
                    "数据库迁移模块重复注册："
                        + moduleKey
                );
            }

            sortedProviders.add(
                provider
            );
        }

        sortedProviders.sort(
            Comparator
                .comparingInt(
                    DatabaseMigrationLocationProvider
                        ::order
                )
                .thenComparing(
                    provider ->
                        validatedModuleKey(
                            provider
                        )
                )
        );

        return List.copyOf(
            sortedProviders
        );
    }

    /**
     * 校验模块标识。
     *
     * <p>不会自动 trim、转小写或替换字符。</p>
     *
     * <p>配置错误必须明确失败，
     * 不能被静默修正。</p>
     */
    private String validatedModuleKey(
        DatabaseMigrationLocationProvider provider
    ) {
        String moduleKey =
            provider.moduleKey();

        if (
            moduleKey == null
                || moduleKey.isBlank()
        ) {
            throw new IllegalStateException(
                "数据库迁移模块标识不能为空。"
            );
        }

        if (
            !moduleKey.equals(
                moduleKey.trim()
            )
        ) {
            throw new IllegalStateException(
                "数据库迁移模块标识不能包含首尾空格："
                    + moduleKey
            );
        }

        if (
            !MODULE_KEY_PATTERN
                .matcher(
                    moduleKey
                )
                .matches()
        ) {
            throw new IllegalStateException(
                "数据库迁移模块标识不合法："
                    + moduleKey
                    + "。必须以小写字母开头，"
                    + "只能包含小写字母、数字和横杠，"
                    + "最大长度为 64。"
            );
        }

        return moduleKey;
    }

    /**
     * 校验单个迁移目录。
     *
     * <p>当前内置模块迁移统一放置在：</p>
     *
     * <pre>
     * classpath:db/migration/{moduleKey}/{databaseType}
     * </pre>
     *
     * <p>例如：</p>
     *
     * <pre>
     * classpath:db/migration/core/mysql
     * classpath:db/migration/user/mysql
     * classpath:db/migration/user/postgresql
     * </pre>
     */
    private String validateLocation(
        String moduleKey,
        DatabaseType databaseType,
        String location
    ) {
        if (
            location == null
                || location.isBlank()
        ) {
            throw new IllegalStateException(
                "模块 "
                    + moduleKey
                    + " 的迁移目录不能为空。"
            );
        }

        if (
            !location.equals(
                location.trim()
            )
        ) {
            throw new IllegalStateException(
                "迁移目录不能包含首尾空格："
                    + location
            );
        }

        if (location.contains("\\")) {
            throw new IllegalStateException(
                "迁移目录必须使用正斜杠，"
                    + "不能使用 Windows 反斜杠："
                    + location
            );
        }

        if (
            location.contains("..")
                || location.contains("./")
        ) {
            throw new IllegalStateException(
                "迁移目录不能包含路径穿越字符："
                    + location
            );
        }

        if (location.contains("//")) {
            throw new IllegalStateException(
                "迁移目录不能包含连续斜杠："
                    + location
            );
        }

        if (
            location.contains("?")
                || location.contains("#")
        ) {
            throw new IllegalStateException(
                "迁移目录不能包含查询参数或片段："
                    + location
            );
        }

        String databaseDirectory =
            databaseDirectory(
                databaseType
            );

        String expectedRoot =
            "classpath:db/migration/"
                + moduleKey
                + "/"
                + databaseDirectory;

        boolean belongsToModule =
            location.equals(
                expectedRoot
            )
                || location.startsWith(
                    expectedRoot + "/"
                );

        if (!belongsToModule) {
            throw new IllegalStateException(
                "模块 "
                    + moduleKey
                    + " 的迁移目录不属于该模块或数据库类型："
                    + location
                    + "。合法根目录应为："
                    + expectedRoot
            );
        }

        if (location.endsWith("/")) {
            throw new IllegalStateException(
                "迁移目录末尾不能带斜杠："
                    + location
            );
        }

        return location;
    }

    /**
     * 将数据库类型转换成迁移目录名称。
     */
    private String databaseDirectory(
        DatabaseType databaseType
    ) {
        return switch (databaseType) {
            case MYSQL, MARIADB ->
                "mysql";

            case POSTGRESQL ->
                "postgresql";
        };
    }
}
