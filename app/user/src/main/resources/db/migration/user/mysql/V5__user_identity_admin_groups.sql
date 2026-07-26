/* Aquafish MySQL/MariaDB V5：用户公开身份与后台管理组正式结构。 */

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `${tablePrefix}users` ADD COLUMN `public_id` VARCHAR(64) NULL COMMENT ''用户对外公开稳定编号''',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = '${tablePrefix}users'
      AND column_name = 'public_id'
);
PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `${tablePrefix}users` ADD COLUMN `register_source` VARCHAR(64) NOT NULL DEFAULT ''legacy'' COMMENT ''账号注册来源''',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = '${tablePrefix}users'
      AND column_name = 'register_source'
);
PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `${tablePrefix}users` ADD COLUMN `register_ip` VARCHAR(45) NULL COMMENT ''注册来源 IP''',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = '${tablePrefix}users'
      AND column_name = 'register_ip'
);
PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;

UPDATE `${tablePrefix}users`
SET `public_id` = CONCAT('AQUA_', LPAD(HEX(`id`), 16, '0'))
WHERE `public_id` IS NULL OR TRIM(`public_id`) = '';

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `${tablePrefix}users` ADD UNIQUE KEY `uk_users_public_id` (`public_id`)',
        'SELECT 1'
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = '${tablePrefix}users'
      AND index_name = 'uk_users_public_id'
);
PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;

CREATE TABLE IF NOT EXISTS `${tablePrefix}admin_groups` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '管理组主键 ID',
    `group_key` VARCHAR(120) NOT NULL COMMENT '管理组稳定标识',
    `name` VARCHAR(120) NOT NULL COMMENT '管理组名称',
    `description` TEXT NULL COMMENT '管理组职责说明',
    `built_in` SMALLINT NOT NULL DEFAULT 0 COMMENT '是否为系统内置管理组',
    `enabled` SMALLINT NOT NULL DEFAULT 1 COMMENT '管理组是否启用',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '后台显示顺序',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY `uk_admin_groups_group_key` (`group_key`),
    KEY `idx_admin_groups_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='后台管理员分组表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}admin_group_users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '管理组成员主键 ID',
    `group_id` BIGINT NOT NULL COMMENT '管理组 ID',
    `user_id` BIGINT NOT NULL COMMENT '管理员用户 ID',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入时间',
    UNIQUE KEY `uk_admin_group_users_group_user` (`group_id`, `user_id`),
    KEY `idx_admin_group_users_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='管理组与管理员用户关联表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}admin_group_permissions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '管理组权限主键 ID',
    `group_id` BIGINT NOT NULL COMMENT '管理组 ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限节点 ID',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间',
    UNIQUE KEY `uk_admin_group_permissions_group_permission`
        (`group_id`, `permission_id`),
    KEY `idx_admin_group_permissions_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='管理组与权限节点关联表';

INSERT IGNORE INTO `${tablePrefix}admin_groups`
    (`group_key`, `name`, `description`, `built_in`, `enabled`, `sort_order`)
VALUES
    ('super_admin', '超级管理员', '拥有后台全部权限。', 1, 1, 1),
    ('admin', '系统管理员', '负责系统基础配置和用户管理。', 1, 1, 10),
    ('content_auditor', '内容审核员', '负责内容、帖子、评论和资料审核。', 1, 1, 20),
    ('operator', '运营人员', '负责推荐、标签、积分和用户运营。', 1, 1, 30),
    ('moderator_admin', '版主管理员', '负责论坛版块和版主体系管理。', 1, 1, 40);

INSERT IGNORE INTO `${tablePrefix}admin_group_users` (`group_id`, `user_id`)
SELECT g.id, ur.user_id
FROM `${tablePrefix}admin_groups` g
JOIN `${tablePrefix}roles` r ON r.role_key = 'super_admin'
JOIN `${tablePrefix}user_roles` ur ON ur.role_id = r.id
WHERE g.group_key = 'super_admin';
