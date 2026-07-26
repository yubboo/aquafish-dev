/*
 * Aquafish MySQL / MariaDB V7：系统模块与通用媒体。
 *
 * 系统设置继续使用 options.option_group 分类，不再重复创建多套设置表。
 * 媒体文件只保存元数据和用途关系，真实二进制内容由 storage 模块管理。
 */

CREATE TABLE IF NOT EXISTS `${tablePrefix}system_modules` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块记录主键 ID',
    `module_key` VARCHAR(64) NOT NULL COMMENT '稳定模块标识',
    `name` VARCHAR(120) NOT NULL COMMENT '模块显示名称',
    `version` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '当前安装版本',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：INSTALLED、ENABLED、DISABLED、FAILED',
    `built_in` SMALLINT NOT NULL DEFAULT 0 COMMENT '是否为内置模块',
    `required_flag` SMALLINT NOT NULL DEFAULT 0 COMMENT '是否为不可停用的系统必需模块',
    `config_json` LONGTEXT NULL COMMENT '模块非敏感配置 JSON',
    `installed_at` DATETIME(3) NULL COMMENT '安装时间',
    `enabled_at` DATETIME(3) NULL COMMENT '最近启用时间',
    `disabled_at` DATETIME(3) NULL COMMENT '最近停用时间',
    `last_error` VARCHAR(1000) NULL COMMENT '最近一次失败的脱敏摘要',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_system_modules_module_key` (`module_key`),
    KEY `idx_system_modules_status` (`status`, `module_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Aquafish 模块安装状态与版本登记表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}media_assets` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '媒体资源主键 ID',
    `public_id` VARCHAR(36) NOT NULL COMMENT '对外使用的不可猜测 UUID',
    `owner_user_id` BIGINT NULL COMMENT '上传用户 ID，系统文件可为空',
    `storage_provider` VARCHAR(64) NOT NULL DEFAULT 'local' COMMENT '存储提供商标识',
    `bucket_name` VARCHAR(191) NOT NULL DEFAULT '' COMMENT '对象存储桶，本地存储为空',
    `object_key` VARCHAR(500) NOT NULL COMMENT '存储系统中的对象键',
    `original_name` VARCHAR(255) NOT NULL COMMENT '用户上传时的原始文件名',
    `media_type` VARCHAR(120) NOT NULL COMMENT 'MIME 类型',
    `file_extension` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '规范化文件扩展名',
    `size_bytes` BIGINT NOT NULL DEFAULT 0 COMMENT '文件字节数',
    `width` INT NULL COMMENT '图片或视频宽度',
    `height` INT NULL COMMENT '图片或视频高度',
    `checksum_sha256` VARCHAR(64) NOT NULL COMMENT '文件 SHA-256 摘要',
    `visibility` VARCHAR(32) NOT NULL DEFAULT 'PUBLIC' COMMENT '可见性：PUBLIC、PRIVATE',
    `status` VARCHAR(32) NOT NULL DEFAULT 'READY' COMMENT '状态：UPLOADING、READY、QUARANTINED、DELETED',
    `metadata_json` LONGTEXT NULL COMMENT '尺寸、编码等扩展元数据 JSON',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at` DATETIME(3) NULL COMMENT '软删除时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_media_assets_public_id` (`public_id`),
    UNIQUE KEY `uk_media_assets_storage_object`
        (`storage_provider`, `bucket_name`, `object_key`),
    KEY `idx_media_assets_owner_created` (`owner_user_id`, `created_at`),
    KEY `idx_media_assets_status_created` (`status`, `created_at`),
    KEY `idx_media_assets_checksum` (`checksum_sha256`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='上传文件与对象存储资源元数据表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}media_usages` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '媒体用途主键 ID',
    `asset_id` BIGINT NOT NULL COMMENT '媒体资源 ID',
    `target_type` VARCHAR(64) NOT NULL COMMENT '使用方类型：USER、ARTICLE、PAGE、FORUM_POST 等',
    `target_id` BIGINT NOT NULL COMMENT '使用方业务 ID',
    `usage_type` VARCHAR(64) NOT NULL COMMENT '用途：AVATAR、COVER、ATTACHMENT、INLINE 等',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '同一目标中的显示顺序',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_media_usages_asset_target`
        (`asset_id`, `target_type`, `target_id`, `usage_type`),
    KEY `idx_media_usages_target`
        (`target_type`, `target_id`, `usage_type`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='媒体资源与用户、内容、帖子之间的用途关系表';

INSERT INTO `${tablePrefix}system_modules`
    (`module_key`, `name`, `status`, `built_in`, `required_flag`, `installed_at`, `enabled_at`)
VALUES
    ('core', '系统内核', 'ENABLED', 1, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    ('setup', '安装与升级', 'ENABLED', 1, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    ('admin', '后台管理', 'ENABLED', 1, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    ('user', '用户中心', 'ENABLED', 1, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    ('forum', '论坛', 'ENABLED', 1, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    ('content', '内容与博客', 'ENABLED', 1, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    ('theme', '主题', 'ENABLED', 1, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    ('plugin', '插件', 'ENABLED', 1, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    ('license', '系统授权', 'ENABLED', 1, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    ('market', '应用市场', 'ENABLED', 1, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    ('search', '站内搜索', 'ENABLED', 1, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    ('ai', 'AI 能力', 'ENABLED', 1, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `built_in` = VALUES(`built_in`),
    `required_flag` = VALUES(`required_flag`);

INSERT IGNORE INTO `${tablePrefix}permissions`
    (`permission_key`, `name`, `permission_type`, `module_key`, `description`, `built_in`, `enabled`, `sort_order`)
VALUES
    ('admin.dashboard.view', '查看后台控制台', 'menu', 'admin', '允许进入后台控制台。', 1, 1, 10),
    ('system.module.view', '查看系统模块', 'action', 'core', '允许查看模块安装状态。', 1, 1, 20),
    ('system.module.manage', '管理系统模块', 'action', 'core', '允许启用、停用和升级可选模块。', 1, 1, 30),
    ('system.settings.view', '查看系统设置', 'menu', 'core', '允许查看站点、固定链接、邮件、存储和安全设置。', 1, 1, 40),
    ('system.settings.update', '修改系统设置', 'action', 'core', '允许修改系统设置。', 1, 1, 50),
    ('system.logs.view', '查看系统日志', 'action', 'core', '允许查看安装日志、登录日志和操作审计。', 1, 1, 60),
    ('media.asset.view', '查看媒体资源', 'action', 'core', '允许查看有权访问的媒体资源。', 1, 1, 70),
    ('media.asset.upload', '上传媒体资源', 'action', 'core', '允许上传媒体文件。', 1, 1, 80),
    ('media.asset.manage', '管理媒体资源', 'action', 'core', '允许审核、删除和恢复媒体资源。', 1, 1, 90);

INSERT IGNORE INTO `${tablePrefix}role_permissions`
    (`role_id`, `permission_id`, `status`)
SELECT r.`id`, p.`id`, 'ACTIVE'
FROM `${tablePrefix}roles` r
JOIN `${tablePrefix}permissions` p ON p.`module_key` IN ('admin', 'core')
WHERE r.`role_key` = 'super_admin';

INSERT IGNORE INTO `${tablePrefix}admin_group_permissions`
    (`group_id`, `permission_id`)
SELECT g.`id`, p.`id`
FROM `${tablePrefix}admin_groups` g
JOIN `${tablePrefix}permissions` p ON p.`module_key` IN ('admin', 'core')
WHERE g.`group_key` = 'super_admin';
