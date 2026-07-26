/* Aquafish MySQL / MariaDB V8：用户安全、关系与认证。 */

/* 修复 V5 已落库但缺失的 Navicat 表注释和字段注释。 */
ALTER TABLE `${tablePrefix}users`
    MODIFY COLUMN `public_id` VARCHAR(64) NOT NULL COMMENT '用户对外公开稳定编号',
    MODIFY COLUMN `register_source` VARCHAR(64) NOT NULL DEFAULT 'legacy' COMMENT '账号注册来源',
    MODIFY COLUMN `register_ip` VARCHAR(45) NULL COMMENT '注册来源 IP';

ALTER TABLE `${tablePrefix}admin_groups`
    COMMENT='后台管理员分组表',
    MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '管理组主键 ID',
    MODIFY COLUMN `group_key` VARCHAR(120) NOT NULL COMMENT '管理组稳定标识',
    MODIFY COLUMN `name` VARCHAR(120) NOT NULL COMMENT '管理组名称',
    MODIFY COLUMN `description` TEXT NULL COMMENT '管理组职责说明',
    MODIFY COLUMN `built_in` SMALLINT NOT NULL DEFAULT 0 COMMENT '是否为系统内置管理组',
    MODIFY COLUMN `enabled` SMALLINT NOT NULL DEFAULT 1 COMMENT '管理组是否启用',
    MODIFY COLUMN `sort_order` INT NOT NULL DEFAULT 0 COMMENT '后台显示顺序',
    MODIFY COLUMN `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间';

ALTER TABLE `${tablePrefix}admin_group_users`
    COMMENT='管理组与管理员用户关联表',
    MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '管理组成员主键 ID',
    MODIFY COLUMN `group_id` BIGINT NOT NULL COMMENT '管理组 ID',
    MODIFY COLUMN `user_id` BIGINT NOT NULL COMMENT '管理员用户 ID',
    MODIFY COLUMN `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入时间';

ALTER TABLE `${tablePrefix}admin_group_permissions`
    COMMENT='管理组与权限节点关联表',
    MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '管理组权限主键 ID',
    MODIFY COLUMN `group_id` BIGINT NOT NULL COMMENT '管理组 ID',
    MODIFY COLUMN `permission_id` BIGINT NOT NULL COMMENT '权限节点 ID',
    MODIFY COLUMN `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间';

SET @aq_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `${tablePrefix}admin_group_permissions` ADD KEY `idx_admin_group_permissions_permission_id` (`permission_id`)',
        'SELECT 1'
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = '${tablePrefix}admin_group_permissions'
      AND index_name = 'idx_admin_group_permissions_permission_id'
);
PREPARE aq_stmt FROM @aq_sql;
EXECUTE aq_stmt;
DEALLOCATE PREPARE aq_stmt;

CREATE TABLE IF NOT EXISTS `${tablePrefix}user_sessions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '会话主键 ID',
    `user_id` BIGINT NOT NULL COMMENT '登录用户 ID',
    `session_type` VARCHAR(32) NOT NULL DEFAULT 'WEB' COMMENT '会话类型：WEB、ADMIN、API',
    `token_hash` VARCHAR(64) NOT NULL COMMENT '会话令牌 SHA-256 摘要，禁止保存原始令牌',
    `ip_address` VARCHAR(45) NULL COMMENT '最近请求 IP',
    `user_agent` VARCHAR(500) NULL COMMENT '浏览器或客户端标识',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_seen_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近活动时间',
    `expires_at` DATETIME(3) NOT NULL COMMENT '过期时间',
    `revoked_at` DATETIME(3) NULL COMMENT '主动失效时间',
    `revoke_reason` VARCHAR(255) NULL COMMENT '失效原因',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_sessions_token_hash` (`token_hash`),
    KEY `idx_user_sessions_user_active` (`user_id`, `revoked_at`, `expires_at`),
    KEY `idx_user_sessions_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='前台、后台和 API 登录会话表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}user_oauth_accounts` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '第三方账号绑定主键 ID',
    `user_id` BIGINT NOT NULL COMMENT 'Aquafish 用户 ID',
    `provider_key` VARCHAR(64) NOT NULL COMMENT 'GitHub、Google、QQ、微信等提供商标识',
    `provider_user_id` VARCHAR(191) NOT NULL COMMENT '第三方平台用户唯一 ID',
    `display_name` VARCHAR(191) NOT NULL DEFAULT '' COMMENT '第三方展示名称',
    `credential_ciphertext` LONGTEXT NULL COMMENT '应用层加密后的可刷新凭据，禁止明文保存',
    `credential_key_version` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '凭据加密密钥版本',
    `metadata_json` LONGTEXT NULL COMMENT '第三方账号非敏感元数据 JSON',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '绑定时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_oauth_provider_user` (`provider_key`, `provider_user_id`),
    UNIQUE KEY `uk_user_oauth_user_provider` (`user_id`, `provider_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户第三方登录账号绑定表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}user_relationships` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户关系主键 ID',
    `source_user_id` BIGINT NOT NULL COMMENT '发起关系的用户 ID',
    `target_user_id` BIGINT NOT NULL COMMENT '被关联用户 ID',
    `relationship_type` VARCHAR(32) NOT NULL COMMENT '关系类型：FOLLOW、FRIEND、BLOCK',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：PENDING、ACTIVE、REJECTED、REMOVED',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_relationships_pair`
        (`source_user_id`, `target_user_id`, `relationship_type`),
    KEY `idx_user_relationships_target`
        (`target_user_id`, `relationship_type`, `status`),
    KEY `idx_user_relationships_source`
        (`source_user_id`, `relationship_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='关注、好友和屏蔽关系统一表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}user_verifications` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户认证主键 ID',
    `user_id` BIGINT NOT NULL COMMENT '申请认证的用户 ID',
    `verification_type` VARCHAR(64) NOT NULL COMMENT '认证类型：EMAIL、PHONE、REAL_NAME、ORGANIZATION 等',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING、APPROVED、REJECTED、EXPIRED',
    `evidence_ciphertext` LONGTEXT NULL COMMENT '应用层加密后的认证材料',
    `evidence_key_version` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '认证材料加密密钥版本',
    `reviewer_user_id` BIGINT NULL COMMENT '审核管理员用户 ID',
    `review_note` VARCHAR(500) NULL COMMENT '审核说明',
    `verified_at` DATETIME(3) NULL COMMENT '认证通过时间',
    `expires_at` DATETIME(3) NULL COMMENT '认证失效时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '申请时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_verifications_user_type` (`user_id`, `verification_type`, `status`),
    KEY `idx_user_verifications_review` (`status`, `created_at`),
    KEY `idx_user_verifications_reviewer` (`reviewer_user_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='邮箱、手机、实名和组织认证记录表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}user_verification_tokens` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '验证令牌主键 ID',
    `user_id` BIGINT NULL COMMENT '关联用户 ID，注册前验证可为空',
    `purpose` VARCHAR(64) NOT NULL COMMENT '用途：VERIFY_EMAIL、RESET_PASSWORD、BIND_PHONE 等',
    `target_value` VARCHAR(191) NOT NULL COMMENT '脱敏或规范化后的验证目标',
    `token_hash` VARCHAR(64) NOT NULL COMMENT '一次性令牌 SHA-256 摘要',
    `attempt_count` INT NOT NULL DEFAULT 0 COMMENT '失败尝试次数',
    `expires_at` DATETIME(3) NOT NULL COMMENT '过期时间',
    `consumed_at` DATETIME(3) NULL COMMENT '成功消费时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_verification_tokens_hash` (`token_hash`),
    KEY `idx_user_verification_tokens_target`
        (`purpose`, `target_value`, `expires_at`),
    KEY `idx_user_verification_tokens_user`
        (`user_id`, `purpose`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='邮箱验证、找回密码等一次性令牌表';

INSERT IGNORE INTO `${tablePrefix}permissions`
    (`permission_key`, `name`, `permission_type`, `module_key`, `description`, `built_in`, `enabled`, `sort_order`)
VALUES
    ('user.view', '查看用户', 'menu', 'user', '允许查看用户列表和详情。', 1, 1, 10),
    ('user.manage', '管理用户', 'action', 'user', '允许修改用户状态和基础资料。', 1, 1, 20),
    ('user.profile_field.manage', '管理用户栏目', 'action', 'user', '允许维护用户资料字段。', 1, 1, 30),
    ('user.statistics.view', '查看用户统计', 'action', 'user', '允许查看用户行为和积分统计。', 1, 1, 40),
    ('user.tag.manage', '管理用户标签', 'action', 'user', '允许维护并分配运营标签。', 1, 1, 50),
    ('user.ban.manage', '管理用户封禁', 'action', 'user', '允许封禁用户和 IP。', 1, 1, 60),
    ('user.points.manage', '管理用户积分', 'action', 'user', '允许配置积分规则和人工奖惩。', 1, 1, 70),
    ('user.relationship.manage', '管理用户关系', 'action', 'user', '允许处理关注、好友和屏蔽关系。', 1, 1, 80),
    ('user.verification.manage', '管理用户认证', 'action', 'user', '允许审核用户认证。', 1, 1, 90),
    ('user.group.manage', '管理用户组', 'action', 'user', '允许维护前台用户组及社区权限。', 1, 1, 100),
    ('user.admin.manage', '管理管理员', 'action', 'user', '允许授予或取消后台管理员身份。', 1, 1, 110),
    ('user.admin_group.manage', '管理后台管理组', 'action', 'user', '允许维护管理组与管理组权限。', 1, 1, 120),
    ('user.role.manage', '管理角色', 'action', 'user', '允许维护系统角色。', 1, 1, 130),
    ('user.permission.manage', '管理权限节点', 'action', 'user', '允许维护角色和权限节点关系。', 1, 1, 140),
    ('user.session.revoke', '注销用户会话', 'action', 'user', '允许强制注销用户登录会话。', 1, 1, 150);

INSERT IGNORE INTO `${tablePrefix}role_permissions`
    (`role_id`, `permission_id`, `status`)
SELECT r.`id`, p.`id`, 'ACTIVE'
FROM `${tablePrefix}roles` r
JOIN `${tablePrefix}permissions` p ON p.`module_key` = 'user'
WHERE r.`role_key` = 'super_admin';

INSERT IGNORE INTO `${tablePrefix}admin_group_permissions`
    (`group_id`, `permission_id`)
SELECT g.`id`, p.`id`
FROM `${tablePrefix}admin_groups` g
JOIN `${tablePrefix}permissions` p ON p.`module_key` = 'user'
WHERE g.`group_key` = 'super_admin';

SET @aq_sql = NULL;
