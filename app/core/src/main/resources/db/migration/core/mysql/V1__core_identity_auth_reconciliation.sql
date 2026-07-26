/*
 * Aquafish 数据库迁移
 *
 * 版本：
 * V1
 *
 * 名称：
 * core_identity_auth_reconciliation
 *
 * 数据库：
 * MySQL 8.0+
 *
 * 作用：
 * 1. 统一数据库默认字符集为 utf8mb4；
 * 2. 建立核心系统和身份认证基础表；
 * 3. 补齐旧 users 表缺失的登录摘要字段；
 * 4. 建立 user_login_logs 登录审计表；
 * 5. 建立 role_permissions 标准 RBAC 关联表；
 * 6. 建立 admin_operation_logs 后台操作日志表；
 * 7. 统一相关表为 InnoDB；
 * 8. 统一相关表排序规则为 utf8mb4_unicode_ci；
 * 9. 补齐登录和权限关联所需索引；
 * 10. 写入安装所需的默认用户组和角色。
 *
 * 安全原则：
 * 1. 不删除任何旧表；
 * 2. 不删除任何旧字段；
 * 3. 不清空任何业务数据；
 * 4. 不自动修改管理员密码；
 * 5. 不自动覆盖现有角色数据；
 * 6. 所有表名统一使用 ${tablePrefix}；
 * 7. 已存在的表、字段和索引会被保留；
 * 8. 本文件由 Flyway 执行，禁止在 Navicat 中手工整段运行。
 */


/* =========================================================
 * 一、数据库默认字符集
 * ========================================================= */

/*
 * ${flyway:database} 是 Flyway 内置数据库名占位符。
 *
 * 这里只修改数据库以后新建表和新建字段的默认字符集，
 * 后面仍然会逐张转换本迁移涉及的核心表。
 */
ALTER DATABASE `${flyway:database}`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;


/* =========================================================
 * 二、系统设置表
 * ========================================================= */

CREATE TABLE IF NOT EXISTS `${tablePrefix}options` (
    `id` BIGINT NOT NULL AUTO_INCREMENT
        COMMENT '系统设置主键 ID',

    `option_key` VARCHAR(191) NOT NULL
        COMMENT '设置项唯一键',

    `option_value` LONGTEXT NULL
        COMMENT '设置项值',

    `option_group` VARCHAR(64) NOT NULL DEFAULT 'general'
        COMMENT '设置分组',

    `autoload` TINYINT(1) NOT NULL DEFAULT 1
        COMMENT '是否在系统启动时自动加载',

    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '创建时间',

    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3)
        COMMENT '更新时间',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_options_option_key` (`option_key`),

    KEY `idx_options_group` (`option_group`),

    KEY `idx_options_autoload` (`autoload`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Aquafish 系统设置表';


/* =========================================================
 * 三、用户组表
 * ========================================================= */

CREATE TABLE IF NOT EXISTS `${tablePrefix}user_groups` (
    `id` BIGINT NOT NULL AUTO_INCREMENT
        COMMENT '用户组主键 ID',

    `group_key` VARCHAR(64) NOT NULL
        COMMENT '用户组唯一标识',

    `name` VARCHAR(100) NOT NULL
        COMMENT '用户组名称',

    `description` VARCHAR(500) NULL
        COMMENT '用户组说明',

    `sort_order` INT NOT NULL DEFAULT 100
        COMMENT '排序值，越小越靠前',

    `is_default` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '是否为默认用户组',

    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '创建时间',

    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3)
        COMMENT '更新时间',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_user_groups_group_key` (`group_key`),

    KEY `idx_user_groups_sort_order` (`sort_order`),

    KEY `idx_user_groups_is_default` (`is_default`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '前台用户等级和会员组表';


/* =========================================================
 * 四、用户主表
 * ========================================================= */

/*
 * users 保持紧凑：
 *
 * 只保存账号、密码、展示信息、状态、用户组和最近登录摘要。
 *
 * 用户扩展资料继续放在 user_profiles，
 * 统计数据继续放在 user_statistics。
 */
CREATE TABLE IF NOT EXISTS `${tablePrefix}users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT
        COMMENT '用户主键 ID',

    `username` VARCHAR(64) NOT NULL
        COMMENT '用户名，可用于登录',

    `email` VARCHAR(191) NULL
        COMMENT '邮箱地址，可用于登录',

    `password_hash` VARCHAR(255) NOT NULL
        COMMENT '密码哈希，严禁保存明文密码',

    `display_name` VARCHAR(100) NULL
        COMMENT '用户展示名称',

    `avatar` VARCHAR(500) NULL
        COMMENT '头像地址',

    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
        COMMENT '账号状态',

    `group_id` BIGINT NULL
        COMMENT '前台用户组 ID',

    `last_login_at` DATETIME(3) NULL
        COMMENT '最近一次登录成功时间',

    `last_login_ip` VARCHAR(45) NULL
        COMMENT '最近一次登录成功 IP',

    `last_user_agent` VARCHAR(500) NULL
        COMMENT '最近一次登录使用的 User-Agent',

    `login_count` BIGINT NOT NULL DEFAULT 0
        COMMENT '累计登录成功次数',

    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '创建时间',

    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3)
        COMMENT '更新时间',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_users_username` (`username`),

    UNIQUE KEY `uk_users_email` (`email`),

    KEY `idx_users_status` (`status`),

    KEY `idx_users_group_id` (`group_id`),

    KEY `idx_users_last_login_at` (`last_login_at`),

    KEY `idx_users_last_login_ip` (`last_login_ip`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Aquafish 用户主表';


/* =========================================================
 * 五、角色表
 * ========================================================= */

CREATE TABLE IF NOT EXISTS `${tablePrefix}roles` (
    `id` BIGINT NOT NULL AUTO_INCREMENT
        COMMENT '角色主键 ID',

    `role_key` VARCHAR(64) NOT NULL
        COMMENT '角色唯一标识',

    `name` VARCHAR(100) NOT NULL
        COMMENT '角色名称',

    `description` VARCHAR(500) NULL
        COMMENT '角色说明',

    `built_in` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '是否为系统内置角色',

    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '创建时间',

    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3)
        COMMENT '更新时间',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_roles_role_key` (`role_key`),

    KEY `idx_roles_built_in` (`built_in`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '系统角色表';


/* =========================================================
 * 六、权限节点表
 * ========================================================= */

CREATE TABLE IF NOT EXISTS `${tablePrefix}permissions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT
        COMMENT '权限主键 ID',

    `permission_key` VARCHAR(180) NOT NULL
        COMMENT '权限唯一标识',

    `name` VARCHAR(160) NOT NULL
        COMMENT '权限名称',

    `permission_type` VARCHAR(50) NOT NULL DEFAULT 'api'
        COMMENT '权限类型，例如 api、menu、action',

    `module_key` VARCHAR(120) NOT NULL DEFAULT ''
        COMMENT '所属模块标识',

    `description` TEXT NULL
        COMMENT '权限说明',

    `built_in` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '是否为系统内置权限',

    `enabled` TINYINT(1) NOT NULL DEFAULT 1
        COMMENT '是否启用',

    `sort_order` INT NOT NULL DEFAULT 0
        COMMENT '排序值',

    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '创建时间',

    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3)
        COMMENT '更新时间',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_permissions_permission_key` (`permission_key`),

    KEY `idx_permissions_module_key` (`module_key`),

    KEY `idx_permissions_type` (`permission_type`),

    KEY `idx_permissions_enabled` (`enabled`),

    KEY `idx_permissions_sort_order` (`sort_order`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '系统权限节点表';


/* =========================================================
 * 七、用户角色关联表
 * ========================================================= */

CREATE TABLE IF NOT EXISTS `${tablePrefix}user_roles` (
    `id` BIGINT NOT NULL AUTO_INCREMENT
        COMMENT '关联记录主键 ID',

    `user_id` BIGINT NOT NULL
        COMMENT '用户 ID',

    `role_id` BIGINT NOT NULL
        COMMENT '角色 ID',

    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '绑定时间',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_user_roles_user_role` (`user_id`, `role_id`),

    KEY `idx_user_roles_user_id` (`user_id`),

    KEY `idx_user_roles_role_id` (`role_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '用户和系统角色关联表';


/* =========================================================
 * 八、角色权限关联表
 * ========================================================= */

CREATE TABLE IF NOT EXISTS `${tablePrefix}role_permissions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT
        COMMENT '关联记录主键 ID',

    `role_id` BIGINT NOT NULL
        COMMENT '角色 ID',

    `permission_id` BIGINT NOT NULL
        COMMENT '权限 ID',

    `granted_by` BIGINT NULL
        COMMENT '授予权限的管理员用户 ID',

    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
        COMMENT '授权状态',

    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '创建时间',

    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3)
        COMMENT '更新时间',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_role_permissions_role_permission`
        (`role_id`, `permission_id`),

    KEY `idx_role_permissions_role_id` (`role_id`),

    KEY `idx_role_permissions_permission_id` (`permission_id`),

    KEY `idx_role_permissions_granted_by` (`granted_by`),

    KEY `idx_role_permissions_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '标准 RBAC 角色权限关联表';


/* =========================================================
 * 九、用户登录审计日志表
 * ========================================================= */

CREATE TABLE IF NOT EXISTS `${tablePrefix}user_login_logs` (
    `id` BIGINT NOT NULL AUTO_INCREMENT
        COMMENT '登录日志主键 ID',

    `user_id` BIGINT NULL
        COMMENT '用户 ID，账号不存在时可以为空',

    `login_name` VARCHAR(191) NOT NULL DEFAULT ''
        COMMENT '本次登录使用的用户名或邮箱',

    `login_result` VARCHAR(32) NOT NULL
        COMMENT '登录结果，例如 SUCCESS 或 FAILED',

    `failure_reason` VARCHAR(500) NULL
        COMMENT '登录失败原因',

    `ip_address` VARCHAR(45) NULL
        COMMENT '系统识别出的客户端 IP',

    `remote_address` VARCHAR(45) NULL
        COMMENT '请求直接来源 IP',

    `x_forwarded_for` VARCHAR(500) NULL
        COMMENT '代理链 X-Forwarded-For',

    `x_real_ip` VARCHAR(45) NULL
        COMMENT '代理传递的 X-Real-IP',

    `user_agent` VARCHAR(500) NULL
        COMMENT '浏览器 User-Agent',

    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '登录发生时间',

    PRIMARY KEY (`id`),

    KEY `idx_user_login_logs_user_id` (`user_id`),

    KEY `idx_user_login_logs_login_name` (`login_name`),

    KEY `idx_user_login_logs_ip` (`ip_address`),

    KEY `idx_user_login_logs_result` (`login_result`),

    KEY `idx_user_login_logs_created_at` (`created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '用户登录成功和失败审计日志表';


/* =========================================================
 * 十、后台操作日志表
 * ========================================================= */

CREATE TABLE IF NOT EXISTS `${tablePrefix}admin_operation_logs` (
    `id` BIGINT NOT NULL AUTO_INCREMENT
        COMMENT '后台操作日志主键 ID',

    `operator_id` BIGINT NOT NULL DEFAULT 0
        COMMENT '后台操作人用户 ID',

    `action_key` VARCHAR(180) NOT NULL
        COMMENT '操作动作唯一标识',

    `target_type` VARCHAR(80) NOT NULL DEFAULT ''
        COMMENT '操作目标类型',

    `target_id` BIGINT NOT NULL DEFAULT 0
        COMMENT '操作目标 ID',

    `summary` VARCHAR(255) NOT NULL DEFAULT ''
        COMMENT '操作摘要',

    `detail` TEXT NULL
        COMMENT '操作详细内容',

    `ip` VARCHAR(120) NOT NULL DEFAULT ''
        COMMENT '操作来源 IP',

    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '操作发生时间',

    PRIMARY KEY (`id`),

    KEY `idx_admin_operation_logs_operator_id` (`operator_id`),

    KEY `idx_admin_operation_logs_action_key` (`action_key`),

    KEY `idx_admin_operation_logs_target`
        (`target_type`, `target_id`),

    KEY `idx_admin_operation_logs_created_at` (`created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '后台管理操作审计日志表';


/* =========================================================
 * 十一、安装日志表
 * ========================================================= */

CREATE TABLE IF NOT EXISTS `${tablePrefix}install_logs` (
    `id` BIGINT NOT NULL AUTO_INCREMENT
        COMMENT '安装日志主键 ID',

    `level` VARCHAR(32) NOT NULL DEFAULT 'INFO'
        COMMENT '日志级别',

    `message` VARCHAR(500) NOT NULL
        COMMENT '日志消息',

    `context` LONGTEXT NULL
        COMMENT '日志上下文',

    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '创建时间',

    PRIMARY KEY (`id`),

    KEY `idx_install_logs_level` (`level`),

    KEY `idx_install_logs_created_at` (`created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Aquafish 安装和升级日志表';


/* =========================================================
 * 十二、补齐旧 users 表登录字段
 * ========================================================= */

/*
 * MySQL 8.0.12 不支持：
 *
 * ADD COLUMN IF NOT EXISTS
 *
 * 因此先检查 information_schema.columns，
 * 再决定执行 ALTER TABLE 还是空操作。
 */


/* ---------- last_login_at ---------- */

SET @aq_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = '${tablePrefix}users'
              AND column_name = 'last_login_at'
        ),
        'SELECT 1',
        'ALTER TABLE `${tablePrefix}users`
         ADD COLUMN `last_login_at` DATETIME(3) NULL
         COMMENT ''最近一次登录成功时间'''
    )
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* ---------- last_login_ip ---------- */

SET @aq_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = '${tablePrefix}users'
              AND column_name = 'last_login_ip'
        ),
        'SELECT 1',
        'ALTER TABLE `${tablePrefix}users`
         ADD COLUMN `last_login_ip` VARCHAR(45) NULL
         COMMENT ''最近一次登录成功 IP'''
    )
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* ---------- last_user_agent ---------- */

SET @aq_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = '${tablePrefix}users'
              AND column_name = 'last_user_agent'
        ),
        'SELECT 1',
        'ALTER TABLE `${tablePrefix}users`
         ADD COLUMN `last_user_agent` VARCHAR(500) NULL
         COMMENT ''最近一次登录使用的 User-Agent'''
    )
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* ---------- login_count ---------- */

SET @aq_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = '${tablePrefix}users'
              AND column_name = 'login_count'
        ),
        'SELECT 1',
        'ALTER TABLE `${tablePrefix}users`
         ADD COLUMN `login_count` BIGINT NOT NULL DEFAULT 0
         COMMENT ''累计登录成功次数'''
    )
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 十三、补齐 users 登录查询索引
 * ========================================================= */


/* ---------- last_login_at 索引 ---------- */

SET @aq_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = '${tablePrefix}users'
              AND index_name = 'idx_users_last_login_at'
        ),
        'SELECT 1',
        'ALTER TABLE `${tablePrefix}users`
         ADD INDEX `idx_users_last_login_at` (`last_login_at`)'
    )
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* ---------- last_login_ip 索引 ---------- */

SET @aq_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = '${tablePrefix}users'
              AND index_name = 'idx_users_last_login_ip'
        ),
        'SELECT 1',
        'ALTER TABLE `${tablePrefix}users`
         ADD INDEX `idx_users_last_login_ip` (`last_login_ip`)'
    )
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 十四、补齐登录日志索引
 * ========================================================= */


/* ---------- user_id ---------- */

SET @aq_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = '${tablePrefix}user_login_logs'
              AND index_name = 'idx_user_login_logs_user_id'
        ),
        'SELECT 1',
        'ALTER TABLE `${tablePrefix}user_login_logs`
         ADD INDEX `idx_user_login_logs_user_id` (`user_id`)'
    )
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* ---------- login_name ---------- */

SET @aq_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = '${tablePrefix}user_login_logs'
              AND index_name = 'idx_user_login_logs_login_name'
        ),
        'SELECT 1',
        'ALTER TABLE `${tablePrefix}user_login_logs`
         ADD INDEX `idx_user_login_logs_login_name` (`login_name`)'
    )
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* ---------- ip_address ---------- */

SET @aq_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = '${tablePrefix}user_login_logs'
              AND index_name = 'idx_user_login_logs_ip'
        ),
        'SELECT 1',
        'ALTER TABLE `${tablePrefix}user_login_logs`
         ADD INDEX `idx_user_login_logs_ip` (`ip_address`)'
    )
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* ---------- login_result ---------- */

SET @aq_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = '${tablePrefix}user_login_logs'
              AND index_name = 'idx_user_login_logs_result'
        ),
        'SELECT 1',
        'ALTER TABLE `${tablePrefix}user_login_logs`
         ADD INDEX `idx_user_login_logs_result` (`login_result`)'
    )
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* ---------- created_at ---------- */

SET @aq_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = '${tablePrefix}user_login_logs'
              AND index_name = 'idx_user_login_logs_created_at'
        ),
        'SELECT 1',
        'ALTER TABLE `${tablePrefix}user_login_logs`
         ADD INDEX `idx_user_login_logs_created_at` (`created_at`)'
    )
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 十五、补齐角色权限关联索引
 * ========================================================= */


/* ---------- role_id ---------- */

SET @aq_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = '${tablePrefix}role_permissions'
              AND index_name = 'idx_role_permissions_role_id'
        ),
        'SELECT 1',
        'ALTER TABLE `${tablePrefix}role_permissions`
         ADD INDEX `idx_role_permissions_role_id` (`role_id`)'
    )
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* ---------- permission_id ---------- */

SET @aq_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = '${tablePrefix}role_permissions'
              AND index_name = 'idx_role_permissions_permission_id'
        ),
        'SELECT 1',
        'ALTER TABLE `${tablePrefix}role_permissions`
         ADD INDEX `idx_role_permissions_permission_id` (`permission_id`)'
    )
);

PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;


/* =========================================================
 * 十六、统一核心表存储引擎和字符集
 * ========================================================= */

/*
 * CONVERT TO CHARACTER SET 会转换已有字符串字段，
 * 不只是改变新字段的默认字符集。
 */

ALTER TABLE `${tablePrefix}options`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

ALTER TABLE `${tablePrefix}user_groups`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

ALTER TABLE `${tablePrefix}users`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

ALTER TABLE `${tablePrefix}roles`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

ALTER TABLE `${tablePrefix}permissions`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

ALTER TABLE `${tablePrefix}user_roles`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

ALTER TABLE `${tablePrefix}role_permissions`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

ALTER TABLE `${tablePrefix}user_login_logs`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

ALTER TABLE `${tablePrefix}admin_operation_logs`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

ALTER TABLE `${tablePrefix}install_logs`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;


/* =========================================================
 * 十七、写入安装必需的默认数据
 * ========================================================= */

/*
 * INSERT IGNORE 只在对应唯一键不存在时插入，
 * 不会覆盖后台已经修改过的角色名称和说明。
 */

INSERT IGNORE INTO `${tablePrefix}user_groups` (
    `group_key`,
    `name`,
    `description`,
    `sort_order`,
    `is_default`
) VALUES (
    'member',
    '普通用户',
    'Aquafish 系统默认普通用户组',
    100,
    1
);

INSERT IGNORE INTO `${tablePrefix}roles` (
    `role_key`,
    `name`,
    `description`,
    `built_in`
) VALUES (
    'super_admin',
    '超级管理员',
    '拥有 Aquafish 全部后台权限',
    1
);

INSERT IGNORE INTO `${tablePrefix}roles` (
    `role_key`,
    `name`,
    `description`,
    `built_in`
) VALUES (
    'admin',
    '管理员',
    'Aquafish 后台管理员角色',
    1
);

INSERT IGNORE INTO `${tablePrefix}roles` (
    `role_key`,
    `name`,
    `description`,
    `built_in`
) VALUES (
    'user',
    '普通用户',
    'Aquafish 前台普通用户角色',
    1
);


/* =========================================================
 * 十八、清理当前连接使用的临时变量
 * ========================================================= */

SET @aq_sql = NULL;


/* =========================================================
 * V1 完成
 * ========================================================= */