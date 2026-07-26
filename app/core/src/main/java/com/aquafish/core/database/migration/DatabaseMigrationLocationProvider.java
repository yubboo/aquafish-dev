package com.aquafish.core.database.migration;

import com.aquafish.core.database.DatabaseType;
import java.util.List;

/**
 * 数据库迁移目录提供者。
 *
 * <p>这是 Aquafish 模块化数据库迁移体系的核心 SPI 契约。</p>
 *
 * <p>Core、User、CMS、BBS、Comment、Media、AI 等模块，
 * 都可以实现该接口，向底层迁移注册中心声明：</p>
 *
 * <ol>
 *     <li>当前迁移目录属于哪个模块；</li>
 *     <li>当前模块的加载顺序；</li>
 *     <li>MySQL 使用哪些迁移目录；</li>
 *     <li>PostgreSQL 使用哪些迁移目录。</li>
 * </ol>
 *
 * <p>接口本身只负责声明，不负责：</p>
 *
 * <ol>
 *     <li>连接数据库；</li>
 *     <li>执行 Flyway migrate；</li>
 *     <li>执行 baseline；</li>
 *     <li>执行 repair；</li>
 *     <li>执行 clean；</li>
 *     <li>检查目录重复；</li>
 *     <li>检查迁移版本重复。</li>
 * </ol>
 *
 * <p>目录合法性、模块名称合法性和冲突检查，
 * 后续统一由 DatabaseMigrationLocationRegistry 处理。</p>
 */
public interface DatabaseMigrationLocationProvider {

    /**
     * 普通业务模块的默认加载顺序。
     *
     * <p>约定：</p>
     *
     * <ul>
     *     <li>Core 核心迁移可以使用较小值，例如 0；</li>
     *     <li>User 等基础业务模块可以使用 100；</li>
     *     <li>CMS、BBS 等业务模块可以使用 200 以后；</li>
     *     <li>插件迁移可以使用更大的顺序值。</li>
     * </ul>
     *
     * <p>注意：加载顺序只控制迁移目录注册顺序，
     * Flyway 仍然按照全局迁移版本号执行 SQL。</p>
     */
    int DEFAULT_ORDER = 1000;

    /**
     * 返回当前迁移提供者所属的模块标识。
     *
     * <p>推荐值：</p>
     *
     * <pre>
     * core
     * user
     * cms
     * bbs
     * comment
     * media
     * ai
     * </pre>
     *
     * <p>要求：</p>
     *
     * <ol>
     *     <li>不能返回 null；</li>
     *     <li>不能为空字符串；</li>
     *     <li>应使用稳定的小写英文模块标识；</li>
     *     <li>同一个模块标识不能被多个提供者重复注册。</li>
     * </ol>
     *
     * @return 模块唯一标识
     */
    String moduleKey();

    /**
     * 返回当前模块迁移目录的注册顺序。
     *
     * <p>数值越小，越早加入 Flyway locations。</p>
     *
     * <p>该顺序不能代替 Flyway 的 V1、V2、V3 版本排序。
     * 所有模块的迁移版本号仍然必须在整个 Aquafish 系统中唯一。</p>
     *
     * @return 注册顺序
     */
    default int order() {
        return DEFAULT_ORDER;
    }

    /**
     * 返回指定数据库类型对应的迁移目录。
     *
     * <p>目录示例：</p>
     *
     * <pre>
     * classpath:db/migration/core/mysql
     * classpath:db/migration/core/postgresql
     * classpath:db/migration/user/mysql
     * classpath:db/migration/user/postgresql
     * </pre>
     *
     * <p>实现类可以针对不同数据库返回不同目录。</p>
     *
     * <p>某模块暂时不支持指定数据库时，应返回空列表：</p>
     *
     * <pre>
     * List.of()
     * </pre>
     *
     * <p>禁止返回 null。</p>
     *
     * @param databaseType 当前数据库类型
     * @return 当前模块对应数据库的迁移目录列表
     */
    List<String> locations(DatabaseType databaseType);
}