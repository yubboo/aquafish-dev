/* Aquafish MySQL / MariaDB V12：主题登记与站点级主题设置。主题文件仍保存于 workdir/themes。 */

CREATE TABLE IF NOT EXISTS `${tablePrefix}themes` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主题记录主键 ID',
    `theme_key` VARCHAR(120) NOT NULL COMMENT '主题清单中的稳定唯一标识',
    `name` VARCHAR(191) NOT NULL COMMENT '主题显示名称',
    `version` VARCHAR(64) NOT NULL COMMENT '已安装主题版本',
    `author_name` VARCHAR(191) NOT NULL DEFAULT '' COMMENT '主题作者显示名称',
    `description` TEXT NULL COMMENT '主题说明',
    `parent_theme_key` VARCHAR(120) NULL COMMENT '父主题稳定标识',
    `package_hash` VARCHAR(64) NOT NULL COMMENT '安装包 SHA-256 摘要',
    `source_type` VARCHAR(32) NOT NULL DEFAULT 'UPLOAD' COMMENT '来源：BUILT_IN、UPLOAD、MARKET',
    `status` VARCHAR(32) NOT NULL DEFAULT 'INSTALLED' COMMENT '状态：INSTALLED、ACTIVE、DISABLED、BROKEN',
    `active_flag` SMALLINT NOT NULL DEFAULT 0 COMMENT '是否为当前启用主题',
    `installed_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '安装时间',
    `activated_at` DATETIME(3) NULL COMMENT '最近启用时间',
    `last_error` VARCHAR(1000) NULL COMMENT '最近一次加载失败的脱敏摘要',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_themes_theme_key` (`theme_key`),
    KEY `idx_themes_active_status` (`active_flag`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='已安装主题清单、版本和启用状态表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}theme_settings` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主题设置主键 ID',
    `theme_id` BIGINT NOT NULL COMMENT '所属主题记录 ID',
    `site_scope` VARCHAR(120) NOT NULL DEFAULT 'default' COMMENT '站点或租户范围标识',
    `setting_key` VARCHAR(191) NOT NULL COMMENT '主题清单声明的设置键',
    `setting_value` LONGTEXT NULL COMMENT '通过主题设置校验后的 JSON 值',
    `value_type` VARCHAR(32) NOT NULL DEFAULT 'STRING' COMMENT '值类型：STRING、NUMBER、BOOLEAN、JSON',
    `updated_by` BIGINT NULL COMMENT '最后修改管理员用户 ID',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_theme_settings_scope_key` (`theme_id`, `site_scope`, `setting_key`),
    KEY `idx_theme_settings_scope` (`site_scope`, `theme_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='主题按站点范围保存的结构化设置表';

INSERT IGNORE INTO `${tablePrefix}permissions`
    (`permission_key`, `name`, `permission_type`, `module_key`, `description`, `built_in`, `enabled`, `sort_order`)
VALUES
    ('theme.view', '查看主题', 'menu', 'theme', '允许查看已安装主题。', 1, 1, 10),
    ('theme.install', '安装主题', 'action', 'theme', '允许校验并安装主题包。', 1, 1, 20),
    ('theme.activate', '启用主题', 'action', 'theme', '允许启用、切换和回滚主题。', 1, 1, 30),
    ('theme.settings.manage', '管理主题设置', 'action', 'theme', '允许修改当前主题的站点设置。', 1, 1, 40),
    ('theme.uninstall', '卸载主题', 'action', 'theme', '允许卸载未启用且未被依赖的主题。', 1, 1, 50);

INSERT IGNORE INTO `${tablePrefix}role_permissions` (`role_id`, `permission_id`, `status`)
SELECT r.`id`, p.`id`, 'ACTIVE'
FROM `${tablePrefix}roles` r JOIN `${tablePrefix}permissions` p ON p.`module_key` = 'theme'
WHERE r.`role_key` = 'super_admin';
INSERT IGNORE INTO `${tablePrefix}admin_group_permissions` (`group_id`, `permission_id`)
SELECT g.`id`, p.`id`
FROM `${tablePrefix}admin_groups` g JOIN `${tablePrefix}permissions` p ON p.`module_key` = 'theme'
WHERE g.`group_key` = 'super_admin';
