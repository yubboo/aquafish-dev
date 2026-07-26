/* Aquafish MySQL / MariaDB V14：市场软件包缓存与安装历史。 */

CREATE TABLE IF NOT EXISTS `${tablePrefix}market_packages` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '市场包主键 ID',
    `source_key` VARCHAR(120) NOT NULL COMMENT '市场源稳定标识',
    `package_key` VARCHAR(191) NOT NULL COMMENT '市场内软件包稳定标识',
    `package_type` VARCHAR(32) NOT NULL COMMENT '包类型：THEME、PLUGIN',
    `name` VARCHAR(191) NOT NULL COMMENT '软件包显示名称',
    `latest_version` VARCHAR(64) NOT NULL COMMENT '市场声明的最新版本',
    `publisher_name` VARCHAR(191) NOT NULL DEFAULT '' COMMENT '发布者显示名称',
    `summary` VARCHAR(1000) NULL COMMENT '软件包摘要',
    `manifest_json` LONGTEXT NOT NULL COMMENT '已验签市场清单 JSON',
    `signature_status` VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED' COMMENT '签名状态：VERIFIED、UNVERIFIED、INVALID',
    `package_url` VARCHAR(1000) NULL COMMENT '受信市场返回的下载地址',
    `package_hash` VARCHAR(64) NULL COMMENT '软件包 SHA-256 摘要',
    `published_at` DATETIME(3) NULL COMMENT '市场发布时间',
    `synced_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近同步时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_market_packages_source_package` (`source_key`, `package_key`),
    KEY `idx_market_packages_type_published` (`package_type`, `published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='受信应用市场的软件包元数据本地缓存表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}market_installations` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '市场安装事件主键 ID',
    `market_package_id` BIGINT NOT NULL COMMENT '关联市场包 ID',
    `target_type` VARCHAR(32) NOT NULL COMMENT '安装目标：THEME、PLUGIN',
    `target_key` VARCHAR(191) NOT NULL COMMENT '安装后的主题或插件稳定标识',
    `from_version` VARCHAR(64) NULL COMMENT '升级前版本，首次安装为空',
    `to_version` VARCHAR(64) NOT NULL COMMENT '目标版本',
    `operation_type` VARCHAR(32) NOT NULL COMMENT '操作：INSTALL、UPGRADE、ROLLBACK、UNINSTALL',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING、RUNNING、SUCCEEDED、FAILED',
    `operator_user_id` BIGINT NOT NULL COMMENT '操作管理员用户 ID',
    `package_hash` VARCHAR(64) NOT NULL COMMENT '实际使用安装包 SHA-256 摘要',
    `error_summary` VARCHAR(1000) NULL COMMENT '失败的脱敏摘要',
    `started_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '开始时间',
    `finished_at` DATETIME(3) NULL COMMENT '完成时间',
    PRIMARY KEY (`id`),
    KEY `idx_market_installations_target` (`target_type`, `target_key`, `started_at`),
    KEY `idx_market_installations_status` (`status`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='市场主题和插件安装、升级及回滚审计表';

INSERT IGNORE INTO `${tablePrefix}permissions`
    (`permission_key`, `name`, `permission_type`, `module_key`, `description`, `built_in`, `enabled`, `sort_order`)
VALUES
    ('market.view', '查看应用市场', 'menu', 'market', '允许浏览受信市场包。', 1, 1, 10),
    ('market.sync', '同步应用市场', 'action', 'market', '允许同步市场清单。', 1, 1, 20),
    ('market.install', '安装市场软件包', 'action', 'market', '允许安装或升级已验签主题和插件。', 1, 1, 30),
    ('market.history.view', '查看市场安装历史', 'action', 'market', '允许查看安装、升级和回滚记录。', 1, 1, 40);

INSERT IGNORE INTO `${tablePrefix}role_permissions` (`role_id`, `permission_id`, `status`)
SELECT r.`id`, p.`id`, 'ACTIVE' FROM `${tablePrefix}roles` r
JOIN `${tablePrefix}permissions` p ON p.`module_key` = 'market' WHERE r.`role_key` = 'super_admin';
INSERT IGNORE INTO `${tablePrefix}admin_group_permissions` (`group_id`, `permission_id`)
SELECT g.`id`, p.`id` FROM `${tablePrefix}admin_groups` g
JOIN `${tablePrefix}permissions` p ON p.`module_key` = 'market' WHERE g.`group_key` = 'super_admin';
