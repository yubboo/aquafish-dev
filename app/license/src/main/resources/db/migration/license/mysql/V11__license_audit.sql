/*
 * Aquafish MySQL / MariaDB V11：实例授权审计。
 *
 * 完整 AQF1/AQO1 签名载荷仍由 LicenseFileStore 保存到 workdir。
 * 数据库只保存摘要、状态、权益和校验事件，避免把可复制授权码散落到数据库备份。
 */

CREATE TABLE IF NOT EXISTS `${tablePrefix}license_activations` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '激活记录主键 ID',
    `instance_id` VARCHAR(64) NOT NULL COMMENT '安装实例稳定设备码',
    `license_id` VARCHAR(120) NOT NULL COMMENT '授权方签发的授权编号',
    `license_type` VARCHAR(32) NOT NULL COMMENT '授权类型：COMMUNITY、COMMERCIAL、TRIAL',
    `subject_name` VARCHAR(191) NULL COMMENT '授权主体显示名称',
    `payload_hash` VARCHAR(64) NOT NULL COMMENT '完整签名授权载荷 SHA-256 摘要',
    `signature_key_id` VARCHAR(120) NOT NULL DEFAULT '' COMMENT '验签公钥版本标识',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE、EXPIRED、REVOKED、INVALID',
    `issued_at` DATETIME(3) NULL COMMENT '授权签发时间',
    `not_before` DATETIME(3) NULL COMMENT '授权开始生效时间',
    `expires_at` DATETIME(3) NULL COMMENT '授权到期时间，永久授权可为空',
    `activated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '本实例激活时间',
    `last_validated_at` DATETIME(3) NULL COMMENT '最近成功校验时间',
    `revoked_at` DATETIME(3) NULL COMMENT '本地记录撤销时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_license_activations_instance_license` (`instance_id`, `license_id`),
    KEY `idx_license_activations_status_expiry` (`status`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='当前实例的授权激活摘要与有效期审计表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}license_entitlements` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '授权权益主键 ID',
    `activation_id` BIGINT NOT NULL COMMENT '所属激活记录 ID',
    `feature_key` VARCHAR(120) NOT NULL COMMENT '受控功能稳定标识',
    `entitlement_type` VARCHAR(32) NOT NULL DEFAULT 'BOOLEAN' COMMENT '权益值类型：BOOLEAN、QUOTA、TEXT',
    `entitlement_value` VARCHAR(500) NOT NULL DEFAULT 'true' COMMENT '签名载荷解析后的权益值',
    `starts_at` DATETIME(3) NULL COMMENT '权益开始时间',
    `expires_at` DATETIME(3) NULL COMMENT '权益到期时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_license_entitlements_activation_feature` (`activation_id`, `feature_key`),
    KEY `idx_license_entitlements_feature` (`feature_key`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='从已验签授权中解析出的功能权益表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}license_validation_events` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '校验事件主键 ID',
    `activation_id` BIGINT NULL COMMENT '关联激活记录 ID，未激活校验可为空',
    `validation_source` VARCHAR(32) NOT NULL COMMENT '校验来源：LOCAL、ONLINE、STARTUP、ADMIN',
    `result_code` VARCHAR(64) NOT NULL COMMENT '脱敏校验结果编码',
    `success_flag` SMALLINT NOT NULL DEFAULT 0 COMMENT '是否校验通过',
    `payload_hash` VARCHAR(64) NULL COMMENT '本次被校验授权载荷摘要',
    `detail_summary` VARCHAR(1000) NULL COMMENT '不包含授权码和密钥的结果摘要',
    `checked_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '校验时间',
    PRIMARY KEY (`id`),
    KEY `idx_license_validation_events_activation` (`activation_id`, `checked_at`),
    KEY `idx_license_validation_events_result` (`success_flag`, `result_code`, `checked_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='本地和在线授权校验的不可覆盖审计事件表';

INSERT IGNORE INTO `${tablePrefix}permissions`
    (`permission_key`, `name`, `permission_type`, `module_key`, `description`, `built_in`, `enabled`, `sort_order`)
VALUES
    ('license.view', '查看系统授权', 'menu', 'license', '允许查看脱敏授权状态和设备码。', 1, 1, 10),
    ('license.activate', '激活系统授权', 'action', 'license', '允许导入或在线激活实例授权。', 1, 1, 20),
    ('license.validate', '重新校验授权', 'action', 'license', '允许触发本地或在线授权校验。', 1, 1, 30),
    ('license.audit.view', '查看授权审计', 'action', 'license', '允许查看脱敏的授权校验历史。', 1, 1, 40);

INSERT IGNORE INTO `${tablePrefix}role_permissions` (`role_id`, `permission_id`, `status`)
SELECT r.`id`, p.`id`, 'ACTIVE'
FROM `${tablePrefix}roles` r
JOIN `${tablePrefix}permissions` p ON p.`module_key` = 'license'
WHERE r.`role_key` = 'super_admin';

INSERT IGNORE INTO `${tablePrefix}admin_group_permissions` (`group_id`, `permission_id`)
SELECT g.`id`, p.`id`
FROM `${tablePrefix}admin_groups` g
JOIN `${tablePrefix}permissions` p ON p.`module_key` = 'license'
WHERE g.`group_key` = 'super_admin';
