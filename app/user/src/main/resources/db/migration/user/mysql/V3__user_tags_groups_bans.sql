/*
 * Aquafish 数据库迁移
 *
 * 版本：
 * V3
 *
 * 名称：
 * user_tags_groups_bans
 *
 * 数据库：
 * MySQL 8.0+
 *
 * 作用：
 * 1. 建立用户标签表；
 * 2. 建立用户和标签关联表；
 * 3. 建立前台用户组权限表；
 * 4. 建立用户封禁记录表；
 * 5. 建立 IP 封禁记录表；
 * 6. 补齐旧数据库缺失字段；
 * 7. 补齐唯一约束和查询索引；
 * 8. 统一存储引擎、字符集和排序规则。
 *
 * 权限边界：
 *
 * user_groups
 *     前台用户组和会员等级。
 *
 * user_group_permissions
 *     前台社区能力，例如发帖、评论、上传和访问范围。
 *
 * roles + role_permissions
 *     系统 RBAC，不由本迁移修改。
 *
 * admin_groups
 *     当前过渡期后台管理组，不由本迁移修改。
 *
 * 数据安全原则：
 * 1. 不删除旧表；
 * 2. 不删除旧字段；
 * 3. 不清空已有数据；
 * 4. 不自动解除已有封禁；
 * 5. 不自动修改用户状态；
 * 6. 不自动删除重复标签或重复关联；
 * 7. 不把用户组权限迁移到后台 RBAC；
 * 8. 所有真实表名使用 ${tablePrefix}；
 * 9. 缺字段时才增加字段；
 * 10. 缺索引时才增加索引；
 * 11. 旧数据违反唯一约束时让迁移明确失败，
 *     禁止静默覆盖或删除数据。
 */


/* =========================================================
 * 一、提高动态 SQL 拼接长度
 * ========================================================= */

SET SESSION group_concat_max_len = 100000;


/* =========================================================
 * 二、用户标签表
 * ========================================================= */

CREATE TABLE IF NOT EXISTS `${tablePrefix}user_tags` (
    `id` BIGINT NOT NULL AUTO_INCREMENT
        COMMENT '用户标签主键 ID',

    `tag_key` VARCHAR(120) NOT NULL
        COMMENT '用户标签唯一标识',

    `name` VARCHAR(120) NOT NULL
        COMMENT '用户标签显示名称',

    `color` VARCHAR(50) NOT NULL DEFAULT ''
        COMMENT '标签显示颜色',

    `description` TEXT NULL
        COMMENT '标签说明',

    `built_in` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '是否为系统内置标签',

    `enabled` TINYINT(1) NOT NULL DEFAULT 1
        COMMENT '标签是否启用',

    `sort_order` INT NOT NULL DEFAULT 0
        COMMENT '标签排序值',

    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '创建时间',

    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3)
        COMMENT '更新时间',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_user_tags_tag_key` (`tag_key`),

    KEY `idx_user_tags_name` (`name`),

    KEY `idx_user_tags_enabled_sort`
        (`enabled`, `sort_order`),

    KEY `idx_user_tags_built_in`
        (`built_in`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '后台用户运营标签表';


/* =========================================================
 * 三、用户标签关联表
 * ========================================================= */

CREATE TABLE IF NOT EXISTS `${tablePrefix}user_tag_relations` (
    `id` BIGINT NOT NULL AUTO_INCREMENT
        COMMENT '用户标签关联主键 ID',

    `user_id` BIGINT NOT NULL
        COMMENT '用户 ID',

    `tag_id` BIGINT NOT NULL
        COMMENT '用户标签 ID',

    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '标签绑定时间',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_user_tag_relations_user_tag`
        (`user_id`, `tag_id`),

    KEY `idx_user_tag_relations_user_id`
        (`user_id`),

    KEY `idx_user_tag_relations_tag_id`
        (`tag_id`),

    KEY `idx_user_tag_relations_created_at`
        (`created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '用户与运营标签关联表';


/* =========================================================
 * 四、前台用户组权限表
 * ========================================================= */

/*
 * 本表只表达前台用户组能力。
 *
 * 示例：
 * forum.thread.create
 * forum.reply.create
 * comment.create
 * media.upload
 * profile.signature.edit
 *
 * 本表不是后台 RBAC 的 role_permissions。
 */
CREATE TABLE IF NOT EXISTS `${tablePrefix}user_group_permissions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT
        COMMENT '用户组权限记录主键 ID',

    `group_id` BIGINT NOT NULL
        COMMENT '前台用户组 ID',

    `permission_key` VARCHAR(160) NOT NULL
        COMMENT '前台权限唯一标识',

    `permission_value` TEXT NULL
        COMMENT '权限值或权限配置 JSON',

    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '创建时间',

    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3)
        COMMENT '更新时间',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_user_group_permissions_group_key`
        (`group_id`, `permission_key`),

    KEY `idx_user_group_permissions_group_id`
        (`group_id`),

    KEY `idx_user_group_permissions_permission_key`
        (`permission_key`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '前台用户组社区权限配置表';


/* =========================================================
 * 五、用户封禁记录表
 * ========================================================= */

/*
 * ban_type 示例：
 *
 * login
 * post
 * comment
 * upload
 * message
 * all
 *
 * enabled：
 *
 * 1 = 当前封禁记录有效
 * 0 = 已解除或已停用
 *
 * expired_at：
 *
 * NULL = 不设置自动到期时间
 */
CREATE TABLE IF NOT EXISTS `${tablePrefix}user_bans` (
    `id` BIGINT NOT NULL AUTO_INCREMENT
        COMMENT '用户封禁记录主键 ID',

    `user_id` BIGINT NOT NULL
        COMMENT '被封禁用户 ID',

    `ban_type` VARCHAR(50) NOT NULL DEFAULT 'login'
        COMMENT '封禁类型',

    `reason` TEXT NULL
        COMMENT '封禁原因',

    `operator_id` BIGINT NOT NULL DEFAULT 0
        COMMENT '执行封禁的管理员用户 ID',

    `started_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '封禁开始时间',

    `expired_at` DATETIME(3) NULL
        COMMENT '封禁到期时间，空表示没有自动到期时间',

    `enabled` TINYINT(1) NOT NULL DEFAULT 1
        COMMENT '封禁记录是否有效',

    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '创建时间',

    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3)
        COMMENT '更新时间',

    PRIMARY KEY (`id`),

    KEY `idx_user_bans_user_enabled`
        (`user_id`, `enabled`),

    KEY `idx_user_bans_type_enabled`
        (`ban_type`, `enabled`),

    KEY `idx_user_bans_expired_at`
        (`expired_at`),

    KEY `idx_user_bans_operator_id`
        (`operator_id`),

    KEY `idx_user_bans_created_at`
        (`created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '用户登录、发帖和社区行为封禁记录表';


/* =========================================================
 * 六、IP 封禁记录表
 * ========================================================= */

/*
 * ip_value 可以保存：
 *
 * 单个 IPv4
 * 单个 IPv6
 * CIDR 网段
 * 后续风控系统支持的 IP 规则文本
 *
 * 同一个 IP 可以存在多条历史记录，
 * 因此不对 ip_value 设置唯一约束。
 */
CREATE TABLE IF NOT EXISTS `${tablePrefix}ip_bans` (
    `id` BIGINT NOT NULL AUTO_INCREMENT
        COMMENT 'IP 封禁记录主键 ID',

    `ip_value` VARCHAR(120) NOT NULL
        COMMENT '被封禁的 IP、IP 段或规则文本',

    `ban_type` VARCHAR(50) NOT NULL DEFAULT 'access'
        COMMENT 'IP 封禁类型',

    `reason` TEXT NULL
        COMMENT 'IP 封禁原因',

    `operator_id` BIGINT NOT NULL DEFAULT 0
        COMMENT '执行封禁的管理员用户 ID',

    `started_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '封禁开始时间',

    `expired_at` DATETIME(3) NULL
        COMMENT '封禁到期时间，空表示没有自动到期时间',

    `enabled` TINYINT(1) NOT NULL DEFAULT 1
        COMMENT '封禁记录是否有效',

    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '创建时间',

    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3)
        COMMENT '更新时间',

    PRIMARY KEY (`id`),

    KEY `idx_ip_bans_value_enabled`
        (`ip_value`, `enabled`),

    KEY `idx_ip_bans_type_enabled`
        (`ban_type`, `enabled`),

    KEY `idx_ip_bans_expired_at`
        (`expired_at`),

    KEY `idx_ip_bans_operator_id`
        (`operator_id`),

    KEY `idx_ip_bans_created_at`
        (`created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'IP 黑名单、IP 段封禁和访问风控记录表';


/* =========================================================
 * 七、补齐旧 user_tags 表缺失字段
 * ========================================================= */

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `${tablePrefix}user_tags` ',
            GROUP_CONCAT(
                CONCAT(
                    'ADD COLUMN `',
                    desired.column_name,
                    '` ',
                    desired.column_definition
                )
                ORDER BY desired.ordinal
                SEPARATOR ', '
            )
        )
    )
    FROM (
        SELECT
            1 AS ordinal,
            'tag_key' AS column_name,
            'VARCHAR(120) NOT NULL DEFAULT '''' COMMENT ''用户标签唯一标识'''
                AS column_definition

        UNION ALL SELECT
            2,
            'name',
            'VARCHAR(120) NOT NULL DEFAULT '''' COMMENT ''用户标签显示名称'''

        UNION ALL SELECT
            3,
            'color',
            'VARCHAR(50) NOT NULL DEFAULT '''' COMMENT ''标签显示颜色'''

        UNION ALL SELECT
            4,
            'description',
            'TEXT NULL COMMENT ''标签说明'''

        UNION ALL SELECT
            5,
            'built_in',
            'TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否系统内置标签'''

        UNION ALL SELECT
            6,
            'enabled',
            'TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''标签是否启用'''

        UNION ALL SELECT
            7,
            'sort_order',
            'INT NOT NULL DEFAULT 0 COMMENT ''标签排序值'''

        UNION ALL SELECT
            8,
            'created_at',
            'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT ''创建时间'''

        UNION ALL SELECT
            9,
            'updated_at',
            'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT ''更新时间'''
    ) desired
    LEFT JOIN information_schema.columns existing_column
        ON existing_column.table_schema = DATABASE()
       AND existing_column.table_name =
            '${tablePrefix}user_tags'
       AND existing_column.column_name =
            desired.column_name
    WHERE existing_column.column_name IS NULL
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 八、补齐旧 user_tag_relations 表缺失字段
 * ========================================================= */

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `${tablePrefix}user_tag_relations` ',
            GROUP_CONCAT(
                CONCAT(
                    'ADD COLUMN `',
                    desired.column_name,
                    '` ',
                    desired.column_definition
                )
                ORDER BY desired.ordinal
                SEPARATOR ', '
            )
        )
    )
    FROM (
        SELECT
            1 AS ordinal,
            'user_id' AS column_name,
            'BIGINT NOT NULL DEFAULT 0 COMMENT ''用户 ID'''
                AS column_definition

        UNION ALL SELECT
            2,
            'tag_id',
            'BIGINT NOT NULL DEFAULT 0 COMMENT ''用户标签 ID'''

        UNION ALL SELECT
            3,
            'created_at',
            'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT ''标签绑定时间'''
    ) desired
    LEFT JOIN information_schema.columns existing_column
        ON existing_column.table_schema = DATABASE()
       AND existing_column.table_name =
            '${tablePrefix}user_tag_relations'
       AND existing_column.column_name =
            desired.column_name
    WHERE existing_column.column_name IS NULL
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 九、补齐旧 user_group_permissions 表缺失字段
 * ========================================================= */

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `${tablePrefix}user_group_permissions` ',
            GROUP_CONCAT(
                CONCAT(
                    'ADD COLUMN `',
                    desired.column_name,
                    '` ',
                    desired.column_definition
                )
                ORDER BY desired.ordinal
                SEPARATOR ', '
            )
        )
    )
    FROM (
        SELECT
            1 AS ordinal,
            'group_id' AS column_name,
            'BIGINT NOT NULL DEFAULT 0 COMMENT ''前台用户组 ID'''
                AS column_definition

        UNION ALL SELECT
            2,
            'permission_key',
            'VARCHAR(160) NOT NULL DEFAULT '''' COMMENT ''前台权限唯一标识'''

        UNION ALL SELECT
            3,
            'permission_value',
            'TEXT NULL COMMENT ''权限值或配置 JSON'''

        UNION ALL SELECT
            4,
            'created_at',
            'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT ''创建时间'''

        UNION ALL SELECT
            5,
            'updated_at',
            'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT ''更新时间'''
    ) desired
    LEFT JOIN information_schema.columns existing_column
        ON existing_column.table_schema = DATABASE()
       AND existing_column.table_name =
            '${tablePrefix}user_group_permissions'
       AND existing_column.column_name =
            desired.column_name
    WHERE existing_column.column_name IS NULL
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 十、补齐旧 user_bans 表缺失字段
 * ========================================================= */

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `${tablePrefix}user_bans` ',
            GROUP_CONCAT(
                CONCAT(
                    'ADD COLUMN `',
                    desired.column_name,
                    '` ',
                    desired.column_definition
                )
                ORDER BY desired.ordinal
                SEPARATOR ', '
            )
        )
    )
    FROM (
        SELECT
            1 AS ordinal,
            'user_id' AS column_name,
            'BIGINT NOT NULL DEFAULT 0 COMMENT ''被封禁用户 ID'''
                AS column_definition

        UNION ALL SELECT
            2,
            'ban_type',
            'VARCHAR(50) NOT NULL DEFAULT ''login'' COMMENT ''封禁类型'''

        UNION ALL SELECT
            3,
            'reason',
            'TEXT NULL COMMENT ''封禁原因'''

        UNION ALL SELECT
            4,
            'operator_id',
            'BIGINT NOT NULL DEFAULT 0 COMMENT ''执行封禁的管理员用户 ID'''

        UNION ALL SELECT
            5,
            'started_at',
            'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT ''封禁开始时间'''

        UNION ALL SELECT
            6,
            'expired_at',
            'DATETIME(3) NULL COMMENT ''封禁到期时间'''

        UNION ALL SELECT
            7,
            'enabled',
            'TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''封禁记录是否有效'''

        UNION ALL SELECT
            8,
            'created_at',
            'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT ''创建时间'''

        UNION ALL SELECT
            9,
            'updated_at',
            'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT ''更新时间'''
    ) desired
    LEFT JOIN information_schema.columns existing_column
        ON existing_column.table_schema = DATABASE()
       AND existing_column.table_name =
            '${tablePrefix}user_bans'
       AND existing_column.column_name =
            desired.column_name
    WHERE existing_column.column_name IS NULL
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 十一、补齐旧 ip_bans 表缺失字段
 * ========================================================= */

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `${tablePrefix}ip_bans` ',
            GROUP_CONCAT(
                CONCAT(
                    'ADD COLUMN `',
                    desired.column_name,
                    '` ',
                    desired.column_definition
                )
                ORDER BY desired.ordinal
                SEPARATOR ', '
            )
        )
    )
    FROM (
        SELECT
            1 AS ordinal,
            'ip_value' AS column_name,
            'VARCHAR(120) NOT NULL DEFAULT '''' COMMENT ''被封禁 IP 或规则文本'''
                AS column_definition

        UNION ALL SELECT
            2,
            'ban_type',
            'VARCHAR(50) NOT NULL DEFAULT ''access'' COMMENT ''IP 封禁类型'''

        UNION ALL SELECT
            3,
            'reason',
            'TEXT NULL COMMENT ''IP 封禁原因'''

        UNION ALL SELECT
            4,
            'operator_id',
            'BIGINT NOT NULL DEFAULT 0 COMMENT ''执行封禁的管理员用户 ID'''

        UNION ALL SELECT
            5,
            'started_at',
            'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT ''封禁开始时间'''

        UNION ALL SELECT
            6,
            'expired_at',
            'DATETIME(3) NULL COMMENT ''封禁到期时间'''

        UNION ALL SELECT
            7,
            'enabled',
            'TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''封禁记录是否有效'''

        UNION ALL SELECT
            8,
            'created_at',
            'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT ''创建时间'''

        UNION ALL SELECT
            9,
            'updated_at',
            'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT ''更新时间'''
    ) desired
    LEFT JOIN information_schema.columns existing_column
        ON existing_column.table_schema = DATABASE()
       AND existing_column.table_name =
            '${tablePrefix}ip_bans'
       AND existing_column.column_name =
            desired.column_name
    WHERE existing_column.column_name IS NULL
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 十二、补齐 user_tags 索引
 * ========================================================= */

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `${tablePrefix}user_tags` ',
            GROUP_CONCAT(
                CONCAT(
                    'ADD ',
                    desired.index_definition
                )
                ORDER BY desired.ordinal
                SEPARATOR ', '
            )
        )
    )
    FROM (
        SELECT
            1 AS ordinal,
            'uk_user_tags_tag_key' AS index_name,
            'UNIQUE KEY `uk_user_tags_tag_key` (`tag_key`)'
                AS index_definition

        UNION ALL SELECT
            2,
            'idx_user_tags_name',
            'KEY `idx_user_tags_name` (`name`)'

        UNION ALL SELECT
            3,
            'idx_user_tags_enabled_sort',
            'KEY `idx_user_tags_enabled_sort` (`enabled`, `sort_order`)'

        UNION ALL SELECT
            4,
            'idx_user_tags_built_in',
            'KEY `idx_user_tags_built_in` (`built_in`)'
    ) desired
    LEFT JOIN information_schema.statistics existing_index
        ON existing_index.table_schema = DATABASE()
       AND existing_index.table_name =
            '${tablePrefix}user_tags'
       AND existing_index.index_name =
            desired.index_name
    WHERE existing_index.index_name IS NULL
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 十三、补齐 user_tag_relations 索引
 * ========================================================= */

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `${tablePrefix}user_tag_relations` ',
            GROUP_CONCAT(
                CONCAT(
                    'ADD ',
                    desired.index_definition
                )
                ORDER BY desired.ordinal
                SEPARATOR ', '
            )
        )
    )
    FROM (
        SELECT
            1 AS ordinal,
            'uk_user_tag_relations_user_tag' AS index_name,
            'UNIQUE KEY `uk_user_tag_relations_user_tag` (`user_id`, `tag_id`)'
                AS index_definition

        UNION ALL SELECT
            2,
            'idx_user_tag_relations_user_id',
            'KEY `idx_user_tag_relations_user_id` (`user_id`)'

        UNION ALL SELECT
            3,
            'idx_user_tag_relations_tag_id',
            'KEY `idx_user_tag_relations_tag_id` (`tag_id`)'

        UNION ALL SELECT
            4,
            'idx_user_tag_relations_created_at',
            'KEY `idx_user_tag_relations_created_at` (`created_at`)'
    ) desired
    LEFT JOIN information_schema.statistics existing_index
        ON existing_index.table_schema = DATABASE()
       AND existing_index.table_name =
            '${tablePrefix}user_tag_relations'
       AND existing_index.index_name =
            desired.index_name
    WHERE existing_index.index_name IS NULL
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 十四、补齐 user_group_permissions 索引
 * ========================================================= */

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `${tablePrefix}user_group_permissions` ',
            GROUP_CONCAT(
                CONCAT(
                    'ADD ',
                    desired.index_definition
                )
                ORDER BY desired.ordinal
                SEPARATOR ', '
            )
        )
    )
    FROM (
        SELECT
            1 AS ordinal,
            'uk_user_group_permissions_group_key' AS index_name,
            'UNIQUE KEY `uk_user_group_permissions_group_key` (`group_id`, `permission_key`)'
                AS index_definition

        UNION ALL SELECT
            2,
            'idx_user_group_permissions_group_id',
            'KEY `idx_user_group_permissions_group_id` (`group_id`)'

        UNION ALL SELECT
            3,
            'idx_user_group_permissions_permission_key',
            'KEY `idx_user_group_permissions_permission_key` (`permission_key`)'
    ) desired
    LEFT JOIN information_schema.statistics existing_index
        ON existing_index.table_schema = DATABASE()
       AND existing_index.table_name =
            '${tablePrefix}user_group_permissions'
       AND existing_index.index_name =
            desired.index_name
    WHERE existing_index.index_name IS NULL
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 十五、补齐 user_bans 索引
 * ========================================================= */

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `${tablePrefix}user_bans` ',
            GROUP_CONCAT(
                CONCAT(
                    'ADD ',
                    desired.index_definition
                )
                ORDER BY desired.ordinal
                SEPARATOR ', '
            )
        )
    )
    FROM (
        SELECT
            1 AS ordinal,
            'idx_user_bans_user_enabled' AS index_name,
            'KEY `idx_user_bans_user_enabled` (`user_id`, `enabled`)'
                AS index_definition

        UNION ALL SELECT
            2,
            'idx_user_bans_type_enabled',
            'KEY `idx_user_bans_type_enabled` (`ban_type`, `enabled`)'

        UNION ALL SELECT
            3,
            'idx_user_bans_expired_at',
            'KEY `idx_user_bans_expired_at` (`expired_at`)'

        UNION ALL SELECT
            4,
            'idx_user_bans_operator_id',
            'KEY `idx_user_bans_operator_id` (`operator_id`)'

        UNION ALL SELECT
            5,
            'idx_user_bans_created_at',
            'KEY `idx_user_bans_created_at` (`created_at`)'
    ) desired
    LEFT JOIN information_schema.statistics existing_index
        ON existing_index.table_schema = DATABASE()
       AND existing_index.table_name =
            '${tablePrefix}user_bans'
       AND existing_index.index_name =
            desired.index_name
    WHERE existing_index.index_name IS NULL
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 十六、补齐 ip_bans 索引
 * ========================================================= */

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `${tablePrefix}ip_bans` ',
            GROUP_CONCAT(
                CONCAT(
                    'ADD ',
                    desired.index_definition
                )
                ORDER BY desired.ordinal
                SEPARATOR ', '
            )
        )
    )
    FROM (
        SELECT
            1 AS ordinal,
            'idx_ip_bans_value_enabled' AS index_name,
            'KEY `idx_ip_bans_value_enabled` (`ip_value`, `enabled`)'
                AS index_definition

        UNION ALL SELECT
            2,
            'idx_ip_bans_type_enabled',
            'KEY `idx_ip_bans_type_enabled` (`ban_type`, `enabled`)'

        UNION ALL SELECT
            3,
            'idx_ip_bans_expired_at',
            'KEY `idx_ip_bans_expired_at` (`expired_at`)'

        UNION ALL SELECT
            4,
            'idx_ip_bans_operator_id',
            'KEY `idx_ip_bans_operator_id` (`operator_id`)'

        UNION ALL SELECT
            5,
            'idx_ip_bans_created_at',
            'KEY `idx_ip_bans_created_at` (`created_at`)'
    ) desired
    LEFT JOIN information_schema.statistics existing_index
        ON existing_index.table_schema = DATABASE()
       AND existing_index.table_name =
            '${tablePrefix}ip_bans'
       AND existing_index.index_name =
            desired.index_name
    WHERE existing_index.index_name IS NULL
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 十七、统一存储引擎和字符集
 * ========================================================= */

ALTER TABLE `${tablePrefix}user_tags`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

ALTER TABLE `${tablePrefix}user_tag_relations`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

ALTER TABLE `${tablePrefix}user_group_permissions`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

ALTER TABLE `${tablePrefix}user_bans`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

ALTER TABLE `${tablePrefix}ip_bans`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;


/* =========================================================
 * 十八、清理当前连接临时变量
 * ========================================================= */

SET @aq_sql = NULL;


/* =========================================================
 * V3 完成
 * ========================================================= */