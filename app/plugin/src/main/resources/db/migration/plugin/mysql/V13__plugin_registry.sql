/* Aquafish MySQL / MariaDB V13：插件登记、配置与授权。插件文件仍保存于 workdir/plugins。 */

CREATE TABLE IF NOT EXISTS `${tablePrefix}plugins` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '插件记录主键 ID',
    `plugin_key` VARCHAR(120) NOT NULL COMMENT '插件清单中的稳定唯一标识',
    `name` VARCHAR(191) NOT NULL COMMENT '插件显示名称',
    `version` VARCHAR(64) NOT NULL COMMENT '已安装插件版本',
    `provider_name` VARCHAR(191) NOT NULL DEFAULT '' COMMENT '插件提供方显示名称',
    `description` TEXT NULL COMMENT '插件说明',
    `main_class` VARCHAR(500) NOT NULL COMMENT '插件入口类全名',
    `package_hash` VARCHAR(64) NOT NULL COMMENT '安装包 SHA-256 摘要',
    `source_type` VARCHAR(32) NOT NULL DEFAULT 'UPLOAD' COMMENT '来源：BUILT_IN、UPLOAD、MARKET',
    `status` VARCHAR(32) NOT NULL DEFAULT 'INSTALLED' COMMENT '状态：INSTALLED、ENABLED、DISABLED、FAILED',
    `enabled_flag` SMALLINT NOT NULL DEFAULT 0 COMMENT '插件是否启用',
    `installed_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '安装时间',
    `enabled_at` DATETIME(3) NULL COMMENT '最近启用时间',
    `disabled_at` DATETIME(3) NULL COMMENT '最近停用时间',
    `last_error` VARCHAR(1000) NULL COMMENT '最近一次失败的脱敏摘要',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plugins_plugin_key` (`plugin_key`),
    KEY `idx_plugins_enabled_status` (`enabled_flag`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='已安装插件清单、版本和生命周期状态表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}plugin_settings` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '插件设置主键 ID',
    `plugin_id` BIGINT NOT NULL COMMENT '所属插件记录 ID',
    `setting_key` VARCHAR(191) NOT NULL COMMENT '插件声明的设置键',
    `setting_value` LONGTEXT NULL COMMENT '非敏感设置 JSON；密钥必须保存密文',
    `value_type` VARCHAR(32) NOT NULL DEFAULT 'STRING' COMMENT '值类型：STRING、NUMBER、BOOLEAN、JSON、SECRET',
    `secret_flag` SMALLINT NOT NULL DEFAULT 0 COMMENT '是否为应用层加密的敏感值',
    `encryption_key_version` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '敏感值加密密钥版本',
    `updated_by` BIGINT NULL COMMENT '最后修改管理员用户 ID',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plugin_settings_plugin_key` (`plugin_id`, `setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='插件结构化配置与加密敏感配置表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}plugin_permissions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '插件能力授权主键 ID',
    `plugin_id` BIGINT NOT NULL COMMENT '所属插件记录 ID',
    `capability_key` VARCHAR(191) NOT NULL COMMENT '插件申请的系统能力标识',
    `risk_level` VARCHAR(32) NOT NULL DEFAULT 'NORMAL' COMMENT '风险等级：NORMAL、SENSITIVE、CRITICAL',
    `granted_flag` SMALLINT NOT NULL DEFAULT 0 COMMENT '管理员是否已批准',
    `granted_by` BIGINT NULL COMMENT '批准管理员用户 ID',
    `granted_at` DATETIME(3) NULL COMMENT '批准时间',
    `revoked_at` DATETIME(3) NULL COMMENT '撤销时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '申请时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plugin_permissions_plugin_capability` (`plugin_id`, `capability_key`),
    KEY `idx_plugin_permissions_granted` (`granted_flag`, `risk_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='插件访问文件、网络和数据等系统能力授权表';

INSERT IGNORE INTO `${tablePrefix}permissions`
    (`permission_key`, `name`, `permission_type`, `module_key`, `description`, `built_in`, `enabled`, `sort_order`)
VALUES
    ('plugin.view', '查看插件', 'menu', 'plugin', '允许查看已安装插件。', 1, 1, 10),
    ('plugin.install', '安装插件', 'action', 'plugin', '允许校验并安装插件包。', 1, 1, 20),
    ('plugin.lifecycle.manage', '管理插件生命周期', 'action', 'plugin', '允许启用、停用和卸载插件。', 1, 1, 30),
    ('plugin.settings.manage', '管理插件设置', 'action', 'plugin', '允许修改插件配置。', 1, 1, 40),
    ('plugin.permission.manage', '审批插件能力', 'action', 'plugin', '允许批准或撤销插件系统能力。', 1, 1, 50);

INSERT IGNORE INTO `${tablePrefix}role_permissions` (`role_id`, `permission_id`, `status`)
SELECT r.`id`, p.`id`, 'ACTIVE' FROM `${tablePrefix}roles` r
JOIN `${tablePrefix}permissions` p ON p.`module_key` = 'plugin' WHERE r.`role_key` = 'super_admin';
INSERT IGNORE INTO `${tablePrefix}admin_group_permissions` (`group_id`, `permission_id`)
SELECT g.`id`, p.`id` FROM `${tablePrefix}admin_groups` g
JOIN `${tablePrefix}permissions` p ON p.`module_key` = 'plugin' WHERE g.`group_key` = 'super_admin';
