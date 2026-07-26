/*
 * Aquafish 数据库迁移
 *
 * 版本：
 * V2
 *
 * 名称：
 * user_profile_statistics_points
 *
 * 数据库：
 * MySQL 8.0+
 *
 * 作用：
 * 1. 建立用户扩展资料表；
 * 2. 建立用户资料字段定义表；
 * 3. 建立用户资料审核表；
 * 4. 建立用户统计快照表；
 * 5. 建立积分规则表；
 * 6. 建立积分流水表；
 * 7. 建立管理员积分奖惩审计表；
 * 8. 补齐旧数据库中缺失的字段；
 * 9. 补齐唯一约束和查询索引；
 * 10. 统一存储引擎、字符集和排序规则；
 * 11. 为已有用户初始化统计快照。
 *
 * 最终职责：
 *
 * user_profiles
 *     保存用户扩展资料。
 *
 * user_profile_fields
 *     保存后台可配置的资料字段定义。
 *
 * user_profile_audits
 *     保存资料修改和审核记录。
 *
 * user_statistics
 *     保存用户统计快照和当前积分余额。
 *
 * points_rules
 *     保存自动积分规则。
 *
 * points_logs
 *     保存每一次积分变化的不可变流水。
 *
 * points_adjustments
 *     保存管理员手工奖励或扣除积分的审计记录。
 *
 * 安全原则：
 * 1. 不删除任何旧表；
 * 2. 不删除任何旧字段；
 * 3. 不清空任何业务数据；
 * 4. 不修改现有用户积分；
 * 5. 不合并或删除重复资料；
 * 6. 所有真实表名通过 ${tablePrefix} 生成；
 * 7. 缺少字段时才执行 ADD COLUMN；
 * 8. 缺少索引时才执行 ADD INDEX；
 * 9. 如果旧数据库存在违反唯一约束的数据，迁移应停止，
 *    不允许静默删除或覆盖数据。
 */


/* =========================================================
 * 一、提高动态字段修复 SQL 的拼接长度
 * ========================================================= */

SET SESSION group_concat_max_len = 100000;


/* =========================================================
 * 二、用户扩展资料表
 * ========================================================= */

CREATE TABLE IF NOT EXISTS `${tablePrefix}user_profiles` (
    `id` BIGINT NOT NULL AUTO_INCREMENT
        COMMENT '用户扩展资料主键 ID',

    `user_id` BIGINT NOT NULL
        COMMENT '关联用户 ID',

    `real_name` VARCHAR(120) NOT NULL DEFAULT ''
        COMMENT '真实姓名',

    `nickname` VARCHAR(120) NOT NULL DEFAULT ''
        COMMENT '用户昵称',

    `gender` VARCHAR(32) NOT NULL DEFAULT 'unknown'
        COMMENT '性别标识',

    `birthday` VARCHAR(32) NOT NULL DEFAULT ''
        COMMENT '生日文本',

    `location` VARCHAR(255) NOT NULL DEFAULT ''
        COMMENT '所在地区',

    `bio` TEXT NULL
        COMMENT '个人简介',

    `signature` TEXT NULL
        COMMENT '个性签名',

    `website` VARCHAR(255) NOT NULL DEFAULT ''
        COMMENT '个人网站',

    `qq` VARCHAR(80) NOT NULL DEFAULT ''
        COMMENT 'QQ 号码',

    `wechat` VARCHAR(120) NOT NULL DEFAULT ''
        COMMENT '微信号',

    `profile_completed` INT NOT NULL DEFAULT 0
        COMMENT '资料完整度',

    `audit_status` VARCHAR(32) NOT NULL DEFAULT 'APPROVED'
        COMMENT '资料审核状态',

    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '创建时间',

    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3)
        COMMENT '更新时间',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_user_profiles_user_id` (`user_id`),

    KEY `idx_user_profiles_nickname` (`nickname`),

    KEY `idx_user_profiles_audit_status` (`audit_status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '用户扩展资料表';


/* =========================================================
 * 三、用户资料字段定义表
 * ========================================================= */

CREATE TABLE IF NOT EXISTS `${tablePrefix}user_profile_fields` (
    `id` BIGINT NOT NULL AUTO_INCREMENT
        COMMENT '资料字段主键 ID',

    `field_key` VARCHAR(120) NOT NULL
        COMMENT '资料字段唯一标识',

    `name` VARCHAR(120) NOT NULL
        COMMENT '字段显示名称',

    `field_type` VARCHAR(50) NOT NULL DEFAULT 'text'
        COMMENT '字段类型',

    `placeholder` VARCHAR(255) NOT NULL DEFAULT ''
        COMMENT '输入框提示文本',

    `description` TEXT NULL
        COMMENT '字段说明',

    `required_flag` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '是否必填',

    `editable_flag` TINYINT(1) NOT NULL DEFAULT 1
        COMMENT '用户是否可以编辑',

    `public_flag` TINYINT(1) NOT NULL DEFAULT 1
        COMMENT '是否允许公开展示',

    `audit_required` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '修改后是否需要审核',

    `sort_order` INT NOT NULL DEFAULT 0
        COMMENT '排序值',

    `enabled` TINYINT(1) NOT NULL DEFAULT 1
        COMMENT '是否启用',

    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '创建时间',

    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3)
        COMMENT '更新时间',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_user_profile_fields_field_key` (`field_key`),

    KEY `idx_user_profile_fields_enabled` (`enabled`),

    KEY `idx_user_profile_fields_sort_order` (`sort_order`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '后台可配置用户资料字段定义表';


/* =========================================================
 * 四、用户资料审核表
 * ========================================================= */

CREATE TABLE IF NOT EXISTS `${tablePrefix}user_profile_audits` (
    `id` BIGINT NOT NULL AUTO_INCREMENT
        COMMENT '资料审核记录主键 ID',

    `user_id` BIGINT NOT NULL
        COMMENT '被审核用户 ID',

    `field_key` VARCHAR(120) NOT NULL DEFAULT ''
        COMMENT '被修改的资料字段标识',

    `old_value` TEXT NULL
        COMMENT '修改前的值',

    `new_value` TEXT NULL
        COMMENT '修改后的值',

    `audit_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        COMMENT '审核状态',

    `audit_message` TEXT NULL
        COMMENT '审核说明',

    `operator_id` BIGINT NOT NULL DEFAULT 0
        COMMENT '审核管理员用户 ID',

    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '提交审核时间',

    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3)
        COMMENT '审核更新时间',

    PRIMARY KEY (`id`),

    KEY `idx_user_profile_audits_user_id` (`user_id`),

    KEY `idx_user_profile_audits_field_key` (`field_key`),

    KEY `idx_user_profile_audits_status` (`audit_status`),

    KEY `idx_user_profile_audits_operator_id` (`operator_id`),

    KEY `idx_user_profile_audits_created_at` (`created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '用户资料修改审核记录表';


/* =========================================================
 * 五、用户统计快照表
 * ========================================================= */

/*
 * 当前积分余额统一保存在 points。
 *
 * points_logs 保存变化流水，
 * points_adjustments 保存管理员手工操作审计。
 */
CREATE TABLE IF NOT EXISTS `${tablePrefix}user_statistics` (
    `id` BIGINT NOT NULL AUTO_INCREMENT
        COMMENT '用户统计记录主键 ID',

    `user_id` BIGINT NOT NULL
        COMMENT '关联用户 ID',

    `posts_count` BIGINT NOT NULL DEFAULT 0
        COMMENT '发帖总数',

    `threads_count` BIGINT NOT NULL DEFAULT 0
        COMMENT '主题总数',

    `comments_count` BIGINT NOT NULL DEFAULT 0
        COMMENT '评论总数',

    `followers_count` BIGINT NOT NULL DEFAULT 0
        COMMENT '粉丝数量',

    `following_count` BIGINT NOT NULL DEFAULT 0
        COMMENT '关注数量',

    `friends_count` BIGINT NOT NULL DEFAULT 0
        COMMENT '好友数量',

    `points` BIGINT NOT NULL DEFAULT 0
        COMMENT '当前积分余额快照',

    `credits` BIGINT NOT NULL DEFAULT 0
        COMMENT '预留综合信用或经验值',

    `last_active_at` DATETIME(3) NULL
        COMMENT '最近活跃时间',

    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '创建时间',

    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3)
        COMMENT '更新时间',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_user_statistics_user_id` (`user_id`),

    KEY `idx_user_statistics_points` (`points`),

    KEY `idx_user_statistics_last_active_at` (`last_active_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '用户统计数据和积分余额快照表';


/* =========================================================
 * 六、积分规则表
 * ========================================================= */

CREATE TABLE IF NOT EXISTS `${tablePrefix}points_rules` (
    `id` BIGINT NOT NULL AUTO_INCREMENT
        COMMENT '积分规则主键 ID',

    `rule_key` VARCHAR(160) NOT NULL
        COMMENT '积分规则唯一标识',

    `name` VARCHAR(160) NOT NULL
        COMMENT '积分规则名称',

    `scene` VARCHAR(120) NOT NULL DEFAULT ''
        COMMENT '规则触发场景',

    `points_delta` BIGINT NOT NULL DEFAULT 0
        COMMENT '规则触发时的积分变化值',

    `daily_limit` BIGINT NOT NULL DEFAULT 0
        COMMENT '每日最多触发次数，0 表示不限制',

    `enabled` TINYINT(1) NOT NULL DEFAULT 1
        COMMENT '规则是否启用',

    `description` TEXT NULL
        COMMENT '积分规则说明',

    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '创建时间',

    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3)
        COMMENT '更新时间',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_points_rules_rule_key` (`rule_key`),

    KEY `idx_points_rules_scene` (`scene`),

    KEY `idx_points_rules_enabled` (`enabled`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '自动积分变化规则表';


/* =========================================================
 * 七、积分流水表
 * ========================================================= */

CREATE TABLE IF NOT EXISTS `${tablePrefix}points_logs` (
    `id` BIGINT NOT NULL AUTO_INCREMENT
        COMMENT '积分流水主键 ID',

    `user_id` BIGINT NOT NULL
        COMMENT '积分所属用户 ID',

    `rule_key` VARCHAR(160) NOT NULL DEFAULT ''
        COMMENT '触发积分变化的规则标识',

    `points_delta` BIGINT NOT NULL DEFAULT 0
        COMMENT '本次积分变化值，可正可负',

    `balance_after` BIGINT NOT NULL DEFAULT 0
        COMMENT '本次变化后的积分余额',

    `source_type` VARCHAR(120) NOT NULL DEFAULT ''
        COMMENT '积分来源业务类型',

    `source_id` BIGINT NOT NULL DEFAULT 0
        COMMENT '积分来源业务记录 ID',

    `remark` TEXT NULL
        COMMENT '积分变化说明',

    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '积分变化时间',

    PRIMARY KEY (`id`),

    KEY `idx_points_logs_user_created`
        (`user_id`, `created_at`),

    KEY `idx_points_logs_rule_key`
        (`rule_key`),

    KEY `idx_points_logs_source`
        (`source_type`, `source_id`),

    KEY `idx_points_logs_created_at`
        (`created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '用户积分变化明细流水表';


/* =========================================================
 * 八、管理员积分奖惩审计表
 * ========================================================= */

CREATE TABLE IF NOT EXISTS `${tablePrefix}points_adjustments` (
    `id` BIGINT NOT NULL AUTO_INCREMENT
        COMMENT '管理员积分调整记录主键 ID',

    `user_id` BIGINT NOT NULL
        COMMENT '被调整积分的用户 ID',

    `operator_id` BIGINT NOT NULL DEFAULT 0
        COMMENT '执行调整的管理员用户 ID',

    `points_delta` BIGINT NOT NULL DEFAULT 0
        COMMENT '管理员奖励或扣除的积分值',

    `reason` TEXT NULL
        COMMENT '积分调整原因',

    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '调整时间',

    PRIMARY KEY (`id`),

    KEY `idx_points_adjustments_user_created`
        (`user_id`, `created_at`),

    KEY `idx_points_adjustments_operator_id`
        (`operator_id`),

    KEY `idx_points_adjustments_created_at`
        (`created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '后台管理员手工积分奖惩审计表';


/* =========================================================
 * 九、补齐旧 user_profiles 表缺失字段
 * ========================================================= */

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `${tablePrefix}user_profiles` ',
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
            'BIGINT NOT NULL COMMENT ''关联用户 ID'''
                AS column_definition

        UNION ALL SELECT
            2,
            'real_name',
            'VARCHAR(120) NOT NULL DEFAULT '''' COMMENT ''真实姓名'''

        UNION ALL SELECT
            3,
            'nickname',
            'VARCHAR(120) NOT NULL DEFAULT '''' COMMENT ''用户昵称'''

        UNION ALL SELECT
            4,
            'gender',
            'VARCHAR(32) NOT NULL DEFAULT ''unknown'' COMMENT ''性别标识'''

        UNION ALL SELECT
            5,
            'birthday',
            'VARCHAR(32) NOT NULL DEFAULT '''' COMMENT ''生日文本'''

        UNION ALL SELECT
            6,
            'location',
            'VARCHAR(255) NOT NULL DEFAULT '''' COMMENT ''所在地区'''

        UNION ALL SELECT
            7,
            'bio',
            'TEXT NULL COMMENT ''个人简介'''

        UNION ALL SELECT
            8,
            'signature',
            'TEXT NULL COMMENT ''个性签名'''

        UNION ALL SELECT
            9,
            'website',
            'VARCHAR(255) NOT NULL DEFAULT '''' COMMENT ''个人网站'''

        UNION ALL SELECT
            10,
            'qq',
            'VARCHAR(80) NOT NULL DEFAULT '''' COMMENT ''QQ 号码'''

        UNION ALL SELECT
            11,
            'wechat',
            'VARCHAR(120) NOT NULL DEFAULT '''' COMMENT ''微信号'''

        UNION ALL SELECT
            12,
            'profile_completed',
            'INT NOT NULL DEFAULT 0 COMMENT ''资料完整度'''

        UNION ALL SELECT
            13,
            'audit_status',
            'VARCHAR(32) NOT NULL DEFAULT ''APPROVED'' COMMENT ''资料审核状态'''

        UNION ALL SELECT
            14,
            'created_at',
            'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT ''创建时间'''

        UNION ALL SELECT
            15,
            'updated_at',
            'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT ''更新时间'''
    ) desired
    LEFT JOIN information_schema.columns existing_column
        ON existing_column.table_schema = DATABASE()
       AND existing_column.table_name =
            '${tablePrefix}user_profiles'
       AND existing_column.column_name =
            desired.column_name
    WHERE existing_column.column_name IS NULL
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 十、补齐旧 user_profile_fields 表缺失字段
 * ========================================================= */

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `${tablePrefix}user_profile_fields` ',
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
            'field_key' AS column_name,
            'VARCHAR(120) NOT NULL COMMENT ''资料字段唯一标识'''
                AS column_definition

        UNION ALL SELECT
            2,
            'name',
            'VARCHAR(120) NOT NULL COMMENT ''字段显示名称'''

        UNION ALL SELECT
            3,
            'field_type',
            'VARCHAR(50) NOT NULL DEFAULT ''text'' COMMENT ''字段类型'''

        UNION ALL SELECT
            4,
            'placeholder',
            'VARCHAR(255) NOT NULL DEFAULT '''' COMMENT ''输入框提示文本'''

        UNION ALL SELECT
            5,
            'description',
            'TEXT NULL COMMENT ''字段说明'''

        UNION ALL SELECT
            6,
            'required_flag',
            'TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否必填'''

        UNION ALL SELECT
            7,
            'editable_flag',
            'TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''用户是否可以编辑'''

        UNION ALL SELECT
            8,
            'public_flag',
            'TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''是否公开展示'''

        UNION ALL SELECT
            9,
            'audit_required',
            'TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''修改后是否需要审核'''

        UNION ALL SELECT
            10,
            'sort_order',
            'INT NOT NULL DEFAULT 0 COMMENT ''排序值'''

        UNION ALL SELECT
            11,
            'enabled',
            'TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''是否启用'''

        UNION ALL SELECT
            12,
            'created_at',
            'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT ''创建时间'''

        UNION ALL SELECT
            13,
            'updated_at',
            'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT ''更新时间'''
    ) desired
    LEFT JOIN information_schema.columns existing_column
        ON existing_column.table_schema = DATABASE()
       AND existing_column.table_name =
            '${tablePrefix}user_profile_fields'
       AND existing_column.column_name =
            desired.column_name
    WHERE existing_column.column_name IS NULL
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 十一、补齐旧 user_profile_audits 表缺失字段
 * ========================================================= */

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `${tablePrefix}user_profile_audits` ',
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
            'BIGINT NOT NULL COMMENT ''被审核用户 ID'''
                AS column_definition

        UNION ALL SELECT
            2,
            'field_key',
            'VARCHAR(120) NOT NULL DEFAULT '''' COMMENT ''被修改字段标识'''

        UNION ALL SELECT
            3,
            'old_value',
            'TEXT NULL COMMENT ''修改前的值'''

        UNION ALL SELECT
            4,
            'new_value',
            'TEXT NULL COMMENT ''修改后的值'''

        UNION ALL SELECT
            5,
            'audit_status',
            'VARCHAR(32) NOT NULL DEFAULT ''PENDING'' COMMENT ''审核状态'''

        UNION ALL SELECT
            6,
            'audit_message',
            'TEXT NULL COMMENT ''审核说明'''

        UNION ALL SELECT
            7,
            'operator_id',
            'BIGINT NOT NULL DEFAULT 0 COMMENT ''审核管理员用户 ID'''

        UNION ALL SELECT
            8,
            'created_at',
            'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT ''提交审核时间'''

        UNION ALL SELECT
            9,
            'updated_at',
            'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT ''审核更新时间'''
    ) desired
    LEFT JOIN information_schema.columns existing_column
        ON existing_column.table_schema = DATABASE()
       AND existing_column.table_name =
            '${tablePrefix}user_profile_audits'
       AND existing_column.column_name =
            desired.column_name
    WHERE existing_column.column_name IS NULL
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 十二、补齐旧 user_statistics 表缺失字段
 * ========================================================= */

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `${tablePrefix}user_statistics` ',
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
            'BIGINT NOT NULL COMMENT ''关联用户 ID'''
                AS column_definition

        UNION ALL SELECT
            2,
            'posts_count',
            'BIGINT NOT NULL DEFAULT 0 COMMENT ''发帖总数'''

        UNION ALL SELECT
            3,
            'threads_count',
            'BIGINT NOT NULL DEFAULT 0 COMMENT ''主题总数'''

        UNION ALL SELECT
            4,
            'comments_count',
            'BIGINT NOT NULL DEFAULT 0 COMMENT ''评论总数'''

        UNION ALL SELECT
            5,
            'followers_count',
            'BIGINT NOT NULL DEFAULT 0 COMMENT ''粉丝数量'''

        UNION ALL SELECT
            6,
            'following_count',
            'BIGINT NOT NULL DEFAULT 0 COMMENT ''关注数量'''

        UNION ALL SELECT
            7,
            'friends_count',
            'BIGINT NOT NULL DEFAULT 0 COMMENT ''好友数量'''

        UNION ALL SELECT
            8,
            'points',
            'BIGINT NOT NULL DEFAULT 0 COMMENT ''当前积分余额快照'''

        UNION ALL SELECT
            9,
            'credits',
            'BIGINT NOT NULL DEFAULT 0 COMMENT ''综合信用或经验值'''

        UNION ALL SELECT
            10,
            'last_active_at',
            'DATETIME(3) NULL COMMENT ''最近活跃时间'''

        UNION ALL SELECT
            11,
            'created_at',
            'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT ''创建时间'''

        UNION ALL SELECT
            12,
            'updated_at',
            'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT ''更新时间'''
    ) desired
    LEFT JOIN information_schema.columns existing_column
        ON existing_column.table_schema = DATABASE()
       AND existing_column.table_name =
            '${tablePrefix}user_statistics'
       AND existing_column.column_name =
            desired.column_name
    WHERE existing_column.column_name IS NULL
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 十三、补齐旧 points_rules 表缺失字段
 * ========================================================= */

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `${tablePrefix}points_rules` ',
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
            'rule_key' AS column_name,
            'VARCHAR(160) NOT NULL COMMENT ''积分规则唯一标识'''
                AS column_definition

        UNION ALL SELECT
            2,
            'name',
            'VARCHAR(160) NOT NULL COMMENT ''积分规则名称'''

        UNION ALL SELECT
            3,
            'scene',
            'VARCHAR(120) NOT NULL DEFAULT '''' COMMENT ''规则触发场景'''

        UNION ALL SELECT
            4,
            'points_delta',
            'BIGINT NOT NULL DEFAULT 0 COMMENT ''积分变化值'''

        UNION ALL SELECT
            5,
            'daily_limit',
            'BIGINT NOT NULL DEFAULT 0 COMMENT ''每日触发次数限制'''

        UNION ALL SELECT
            6,
            'enabled',
            'TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''是否启用'''

        UNION ALL SELECT
            7,
            'description',
            'TEXT NULL COMMENT ''积分规则说明'''

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
            '${tablePrefix}points_rules'
       AND existing_column.column_name =
            desired.column_name
    WHERE existing_column.column_name IS NULL
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 十四、补齐旧 points_logs 表缺失字段
 * ========================================================= */

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `${tablePrefix}points_logs` ',
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
            'BIGINT NOT NULL COMMENT ''积分所属用户 ID'''
                AS column_definition

        UNION ALL SELECT
            2,
            'rule_key',
            'VARCHAR(160) NOT NULL DEFAULT '''' COMMENT ''积分规则标识'''

        UNION ALL SELECT
            3,
            'points_delta',
            'BIGINT NOT NULL DEFAULT 0 COMMENT ''本次积分变化值'''

        UNION ALL SELECT
            4,
            'balance_after',
            'BIGINT NOT NULL DEFAULT 0 COMMENT ''变化后的积分余额'''

        UNION ALL SELECT
            5,
            'source_type',
            'VARCHAR(120) NOT NULL DEFAULT '''' COMMENT ''来源业务类型'''

        UNION ALL SELECT
            6,
            'source_id',
            'BIGINT NOT NULL DEFAULT 0 COMMENT ''来源业务记录 ID'''

        UNION ALL SELECT
            7,
            'remark',
            'TEXT NULL COMMENT ''积分变化说明'''

        UNION ALL SELECT
            8,
            'created_at',
            'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT ''积分变化时间'''
    ) desired
    LEFT JOIN information_schema.columns existing_column
        ON existing_column.table_schema = DATABASE()
       AND existing_column.table_name =
            '${tablePrefix}points_logs'
       AND existing_column.column_name =
            desired.column_name
    WHERE existing_column.column_name IS NULL
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 十五、补齐旧 points_adjustments 表缺失字段
 * ========================================================= */

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `${tablePrefix}points_adjustments` ',
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
            'BIGINT NOT NULL COMMENT ''被调整积分的用户 ID'''
                AS column_definition

        UNION ALL SELECT
            2,
            'operator_id',
            'BIGINT NOT NULL DEFAULT 0 COMMENT ''执行调整的管理员用户 ID'''

        UNION ALL SELECT
            3,
            'points_delta',
            'BIGINT NOT NULL DEFAULT 0 COMMENT ''奖励或扣除的积分值'''

        UNION ALL SELECT
            4,
            'reason',
            'TEXT NULL COMMENT ''积分调整原因'''

        UNION ALL SELECT
            5,
            'created_at',
            'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT ''调整时间'''
    ) desired
    LEFT JOIN information_schema.columns existing_column
        ON existing_column.table_schema = DATABASE()
       AND existing_column.table_name =
            '${tablePrefix}points_adjustments'
       AND existing_column.column_name =
            desired.column_name
    WHERE existing_column.column_name IS NULL
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 十六、补齐用户资料表索引
 * ========================================================= */

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `${tablePrefix}user_profiles` ',
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
            'uk_user_profiles_user_id' AS index_name,
            'UNIQUE KEY `uk_user_profiles_user_id` (`user_id`)'
                AS index_definition

        UNION ALL SELECT
            2,
            'idx_user_profiles_nickname',
            'KEY `idx_user_profiles_nickname` (`nickname`)'

        UNION ALL SELECT
            3,
            'idx_user_profiles_audit_status',
            'KEY `idx_user_profiles_audit_status` (`audit_status`)'
    ) desired
    LEFT JOIN information_schema.statistics existing_index
        ON existing_index.table_schema = DATABASE()
       AND existing_index.table_name =
            '${tablePrefix}user_profiles'
       AND existing_index.index_name =
            desired.index_name
    WHERE existing_index.index_name IS NULL
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 十七、补齐资料字段表索引
 * ========================================================= */

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `${tablePrefix}user_profile_fields` ',
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
            'uk_user_profile_fields_field_key' AS index_name,
            'UNIQUE KEY `uk_user_profile_fields_field_key` (`field_key`)'
                AS index_definition

        UNION ALL SELECT
            2,
            'idx_user_profile_fields_enabled',
            'KEY `idx_user_profile_fields_enabled` (`enabled`)'

        UNION ALL SELECT
            3,
            'idx_user_profile_fields_sort_order',
            'KEY `idx_user_profile_fields_sort_order` (`sort_order`)'
    ) desired
    LEFT JOIN information_schema.statistics existing_index
        ON existing_index.table_schema = DATABASE()
       AND existing_index.table_name =
            '${tablePrefix}user_profile_fields'
       AND existing_index.index_name =
            desired.index_name
    WHERE existing_index.index_name IS NULL
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 十八、补齐资料审核表索引
 * ========================================================= */

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `${tablePrefix}user_profile_audits` ',
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
            'idx_user_profile_audits_user_id' AS index_name,
            'KEY `idx_user_profile_audits_user_id` (`user_id`)'
                AS index_definition

        UNION ALL SELECT
            2,
            'idx_user_profile_audits_field_key',
            'KEY `idx_user_profile_audits_field_key` (`field_key`)'

        UNION ALL SELECT
            3,
            'idx_user_profile_audits_status',
            'KEY `idx_user_profile_audits_status` (`audit_status`)'

        UNION ALL SELECT
            4,
            'idx_user_profile_audits_operator_id',
            'KEY `idx_user_profile_audits_operator_id` (`operator_id`)'

        UNION ALL SELECT
            5,
            'idx_user_profile_audits_created_at',
            'KEY `idx_user_profile_audits_created_at` (`created_at`)'
    ) desired
    LEFT JOIN information_schema.statistics existing_index
        ON existing_index.table_schema = DATABASE()
       AND existing_index.table_name =
            '${tablePrefix}user_profile_audits'
       AND existing_index.index_name =
            desired.index_name
    WHERE existing_index.index_name IS NULL
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 十九、补齐用户统计表索引
 * ========================================================= */

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `${tablePrefix}user_statistics` ',
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
            'uk_user_statistics_user_id' AS index_name,
            'UNIQUE KEY `uk_user_statistics_user_id` (`user_id`)'
                AS index_definition

        UNION ALL SELECT
            2,
            'idx_user_statistics_points',
            'KEY `idx_user_statistics_points` (`points`)'

        UNION ALL SELECT
            3,
            'idx_user_statistics_last_active_at',
            'KEY `idx_user_statistics_last_active_at` (`last_active_at`)'
    ) desired
    LEFT JOIN information_schema.statistics existing_index
        ON existing_index.table_schema = DATABASE()
       AND existing_index.table_name =
            '${tablePrefix}user_statistics'
       AND existing_index.index_name =
            desired.index_name
    WHERE existing_index.index_name IS NULL
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 二十、补齐积分规则表索引
 * ========================================================= */

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `${tablePrefix}points_rules` ',
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
            'uk_points_rules_rule_key' AS index_name,
            'UNIQUE KEY `uk_points_rules_rule_key` (`rule_key`)'
                AS index_definition

        UNION ALL SELECT
            2,
            'idx_points_rules_scene',
            'KEY `idx_points_rules_scene` (`scene`)'

        UNION ALL SELECT
            3,
            'idx_points_rules_enabled',
            'KEY `idx_points_rules_enabled` (`enabled`)'
    ) desired
    LEFT JOIN information_schema.statistics existing_index
        ON existing_index.table_schema = DATABASE()
       AND existing_index.table_name =
            '${tablePrefix}points_rules'
       AND existing_index.index_name =
            desired.index_name
    WHERE existing_index.index_name IS NULL
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 二十一、补齐积分流水表索引
 * ========================================================= */

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `${tablePrefix}points_logs` ',
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
            'idx_points_logs_user_created' AS index_name,
            'KEY `idx_points_logs_user_created` (`user_id`, `created_at`)'
                AS index_definition

        UNION ALL SELECT
            2,
            'idx_points_logs_rule_key',
            'KEY `idx_points_logs_rule_key` (`rule_key`)'

        UNION ALL SELECT
            3,
            'idx_points_logs_source',
            'KEY `idx_points_logs_source` (`source_type`, `source_id`)'

        UNION ALL SELECT
            4,
            'idx_points_logs_created_at',
            'KEY `idx_points_logs_created_at` (`created_at`)'
    ) desired
    LEFT JOIN information_schema.statistics existing_index
        ON existing_index.table_schema = DATABASE()
       AND existing_index.table_name =
            '${tablePrefix}points_logs'
       AND existing_index.index_name =
            desired.index_name
    WHERE existing_index.index_name IS NULL
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 二十二、补齐管理员积分调整表索引
 * ========================================================= */

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `${tablePrefix}points_adjustments` ',
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
            'idx_points_adjustments_user_created' AS index_name,
            'KEY `idx_points_adjustments_user_created` (`user_id`, `created_at`)'
                AS index_definition

        UNION ALL SELECT
            2,
            'idx_points_adjustments_operator_id',
            'KEY `idx_points_adjustments_operator_id` (`operator_id`)'

        UNION ALL SELECT
            3,
            'idx_points_adjustments_created_at',
            'KEY `idx_points_adjustments_created_at` (`created_at`)'
    ) desired
    LEFT JOIN information_schema.statistics existing_index
        ON existing_index.table_schema = DATABASE()
       AND existing_index.table_name =
            '${tablePrefix}points_adjustments'
       AND existing_index.index_name =
            desired.index_name
    WHERE existing_index.index_name IS NULL
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 二十三、统一表存储引擎和字符集
 * ========================================================= */

ALTER TABLE `${tablePrefix}user_profiles`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

ALTER TABLE `${tablePrefix}user_profile_fields`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

ALTER TABLE `${tablePrefix}user_profile_audits`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

ALTER TABLE `${tablePrefix}user_statistics`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

ALTER TABLE `${tablePrefix}points_rules`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

ALTER TABLE `${tablePrefix}points_logs`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

ALTER TABLE `${tablePrefix}points_adjustments`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;


/* =========================================================
 * 二十四、为已有用户初始化统计快照
 * ========================================================= */

/*
 * 只为没有统计记录的用户创建默认快照。
 *
 * 不会覆盖现有：
 * 1. 积分；
 * 2. 发帖数；
 * 3. 主题数；
 * 4. 评论数；
 * 5. 关注数据；
 * 6. 活跃时间。
 */
INSERT INTO `${tablePrefix}user_statistics` (
    `user_id`,
    `posts_count`,
    `threads_count`,
    `comments_count`,
    `followers_count`,
    `following_count`,
    `friends_count`,
    `points`,
    `credits`
)
SELECT
    existing_user.`id`,
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    0
FROM `${tablePrefix}users` existing_user
WHERE NOT EXISTS (
    SELECT 1
    FROM `${tablePrefix}user_statistics`
        existing_statistics
    WHERE existing_statistics.`user_id` =
        existing_user.`id`
);


/* =========================================================
 * 二十五、清理当前连接临时变量
 * ========================================================= */

SET @aq_sql = NULL;


/* =========================================================
 * V2 完成
 * ========================================================= */