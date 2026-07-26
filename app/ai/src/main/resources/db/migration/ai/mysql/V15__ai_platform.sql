/* Aquafish MySQL / MariaDB V15：AI 提供商、模型、提示词、任务和审计。 */

CREATE TABLE IF NOT EXISTS `${tablePrefix}ai_providers` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'AI 提供商主键 ID',
    `provider_key` VARCHAR(120) NOT NULL COMMENT '提供商稳定标识',
    `name` VARCHAR(191) NOT NULL COMMENT '提供商显示名称',
    `provider_type` VARCHAR(64) NOT NULL COMMENT '协议类型：OPENAI_COMPATIBLE、OLLAMA、CUSTOM',
    `base_url` VARCHAR(1000) NOT NULL COMMENT '不含密钥的 API 基础地址',
    `enabled` SMALLINT NOT NULL DEFAULT 1 COMMENT '提供商是否启用',
    `timeout_seconds` INT NOT NULL DEFAULT 60 COMMENT '请求超时秒数',
    `extra_config_json` LONGTEXT NULL COMMENT '不含密钥的扩展配置 JSON',
    `health_status` VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN' COMMENT '健康状态：UNKNOWN、HEALTHY、DEGRADED、DOWN',
    `last_checked_at` DATETIME(3) NULL COMMENT '最近健康检查时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_providers_provider_key` (`provider_key`),
    KEY `idx_ai_providers_enabled` (`enabled`, `provider_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='AI 服务提供商与非敏感连接配置表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}ai_provider_credentials` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'AI 凭据主键 ID',
    `provider_id` BIGINT NOT NULL COMMENT '所属 AI 提供商 ID',
    `credential_name` VARCHAR(120) NOT NULL DEFAULT 'default' COMMENT '凭据槽位名称',
    `ciphertext` LONGTEXT NOT NULL COMMENT '应用层加密后的 API 密钥或访问令牌',
    `key_version` VARCHAR(64) NOT NULL COMMENT '凭据加密密钥版本',
    `fingerprint` VARCHAR(64) NOT NULL COMMENT '用于去重和审计的不可逆凭据指纹',
    `enabled` SMALLINT NOT NULL DEFAULT 1 COMMENT '凭据是否启用',
    `expires_at` DATETIME(3) NULL COMMENT '凭据到期时间',
    `last_used_at` DATETIME(3) NULL COMMENT '最近使用时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_provider_credentials_name` (`provider_id`, `credential_name`),
    KEY `idx_ai_provider_credentials_enabled` (`provider_id`, `enabled`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='AI 提供商应用层加密凭据表，禁止保存明文密钥';

CREATE TABLE IF NOT EXISTS `${tablePrefix}ai_models` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'AI 模型主键 ID',
    `provider_id` BIGINT NOT NULL COMMENT '所属 AI 提供商 ID',
    `model_key` VARCHAR(191) NOT NULL COMMENT '提供商 API 使用的模型标识',
    `name` VARCHAR(191) NOT NULL COMMENT '后台显示名称',
    `model_type` VARCHAR(32) NOT NULL COMMENT '模型类型：CHAT、EMBEDDING、IMAGE、RERANK',
    `context_window` INT NULL COMMENT '上下文窗口 token 数',
    `input_unit_price` DECIMAL(18,8) NULL COMMENT '输入计费单价，仅用于估算',
    `output_unit_price` DECIMAL(18,8) NULL COMMENT '输出计费单价，仅用于估算',
    `currency` VARCHAR(16) NOT NULL DEFAULT 'USD' COMMENT '计费币种',
    `default_flag` SMALLINT NOT NULL DEFAULT 0 COMMENT '是否为该类型默认模型',
    `enabled` SMALLINT NOT NULL DEFAULT 1 COMMENT '模型是否启用',
    `capabilities_json` LONGTEXT NULL COMMENT '流式、工具、视觉等能力 JSON',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_models_provider_model` (`provider_id`, `model_key`),
    KEY `idx_ai_models_type_enabled` (`model_type`, `enabled`, `default_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='AI 提供商可用模型和能力清单表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}ai_prompts` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '提示词主键 ID',
    `prompt_key` VARCHAR(191) NOT NULL COMMENT '提示词稳定标识',
    `name` VARCHAR(191) NOT NULL COMMENT '提示词显示名称',
    `module_key` VARCHAR(64) NOT NULL COMMENT '使用该提示词的业务模块',
    `system_prompt` LONGTEXT NOT NULL COMMENT '系统提示词模板',
    `user_prompt_template` LONGTEXT NULL COMMENT '用户提示词模板',
    `variables_json` LONGTEXT NULL COMMENT '模板变量声明 JSON',
    `version` INT NOT NULL DEFAULT 1 COMMENT '提示词版本号',
    `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT、ACTIVE、ARCHIVED',
    `created_by` BIGINT NOT NULL COMMENT '创建管理员用户 ID',
    `updated_by` BIGINT NOT NULL COMMENT '最后修改管理员用户 ID',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_prompts_prompt_key_version` (`prompt_key`, `version`),
    KEY `idx_ai_prompts_module_status` (`module_key`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='按业务模块版本化管理的 AI 提示词表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}ai_tasks` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'AI 任务主键 ID',
    `public_id` VARCHAR(36) NOT NULL COMMENT '对外任务 UUID',
    `task_type` VARCHAR(64) NOT NULL COMMENT '任务类型：CHAT、SUMMARY、EMBEDDING、MODERATION 等',
    `module_key` VARCHAR(64) NOT NULL COMMENT '发起任务的业务模块',
    `request_user_id` BIGINT NULL COMMENT '发起任务的用户 ID',
    `provider_id` BIGINT NOT NULL COMMENT '实际使用提供商 ID',
    `model_id` BIGINT NOT NULL COMMENT '实际使用模型 ID',
    `prompt_id` BIGINT NULL COMMENT '实际使用提示词 ID',
    `target_type` VARCHAR(64) NULL COMMENT '关联业务对象类型',
    `target_id` BIGINT NULL COMMENT '关联业务对象 ID',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING、RUNNING、SUCCEEDED、FAILED、CANCELLED',
    `input_hash` VARCHAR(64) NOT NULL COMMENT '输入内容摘要，审计表不保存敏感原文',
    `input_tokens` BIGINT NOT NULL DEFAULT 0 COMMENT '输入 token 数',
    `output_tokens` BIGINT NOT NULL DEFAULT 0 COMMENT '输出 token 数',
    `estimated_cost` DECIMAL(18,8) NOT NULL DEFAULT 0 COMMENT '按模型价格估算的成本',
    `result_reference` VARCHAR(1000) NULL COMMENT '结果在业务表或存储系统中的引用',
    `error_code` VARCHAR(120) NULL COMMENT '规范化失败编码',
    `error_summary` VARCHAR(1000) NULL COMMENT '脱敏失败摘要',
    `started_at` DATETIME(3) NULL COMMENT '开始执行时间',
    `finished_at` DATETIME(3) NULL COMMENT '完成时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_tasks_public_id` (`public_id`),
    KEY `idx_ai_tasks_queue` (`status`, `created_at`, `id`),
    KEY `idx_ai_tasks_user` (`request_user_id`, `created_at`),
    KEY `idx_ai_tasks_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='AI 异步任务、用量、成本和结果引用表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}ai_audit_records` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'AI 审计主键 ID',
    `task_id` BIGINT NULL COMMENT '关联 AI 任务 ID',
    `event_type` VARCHAR(64) NOT NULL COMMENT '事件：REQUEST、RESPONSE、BLOCKED、ERROR、CREDENTIAL_ROTATED',
    `operator_user_id` BIGINT NULL COMMENT '相关用户或管理员 ID',
    `provider_id` BIGINT NULL COMMENT '相关提供商 ID',
    `model_id` BIGINT NULL COMMENT '相关模型 ID',
    `policy_result` VARCHAR(64) NULL COMMENT '内容安全或权限策略结果',
    `detail_summary` VARCHAR(1000) NULL COMMENT '不含提示词原文和密钥的审计摘要',
    `occurred_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件时间',
    PRIMARY KEY (`id`),
    KEY `idx_ai_audit_records_task` (`task_id`, `occurred_at`),
    KEY `idx_ai_audit_records_event` (`event_type`, `occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='不保存敏感原文和密钥的 AI 安全审计事件表';

INSERT IGNORE INTO `${tablePrefix}permissions`
    (`permission_key`, `name`, `permission_type`, `module_key`, `description`, `built_in`, `enabled`, `sort_order`)
VALUES
    ('ai.view', '查看 AI 模块', 'menu', 'ai', '允许进入 AI 管理模块。', 1, 1, 10),
    ('ai.provider.manage', '管理 AI 提供商', 'action', 'ai', '允许维护提供商连接与加密凭据。', 1, 1, 20),
    ('ai.model.manage', '管理 AI 模型', 'action', 'ai', '允许维护模型能力和默认模型。', 1, 1, 30),
    ('ai.prompt.manage', '管理 AI 提示词', 'action', 'ai', '允许维护版本化提示词。', 1, 1, 40),
    ('ai.task.view', '查看 AI 任务', 'action', 'ai', '允许查看任务状态、用量和脱敏错误。', 1, 1, 50),
    ('ai.audit.view', '查看 AI 审计', 'action', 'ai', '允许查看 AI 安全审计事件。', 1, 1, 60);

INSERT IGNORE INTO `${tablePrefix}role_permissions` (`role_id`, `permission_id`, `status`)
SELECT r.`id`, p.`id`, 'ACTIVE' FROM `${tablePrefix}roles` r
JOIN `${tablePrefix}permissions` p ON p.`module_key` = 'ai' WHERE r.`role_key` = 'super_admin';
INSERT IGNORE INTO `${tablePrefix}admin_group_permissions` (`group_id`, `permission_id`)
SELECT g.`id`, p.`id` FROM `${tablePrefix}admin_groups` g
JOIN `${tablePrefix}permissions` p ON p.`module_key` = 'ai' WHERE g.`group_key` = 'super_admin';
