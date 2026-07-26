/* Aquafish MySQL / MariaDB V16：统一搜索文档与可靠索引队列。 */

CREATE TABLE IF NOT EXISTS `${tablePrefix}search_documents` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '搜索文档主键 ID',
    `document_type` VARCHAR(64) NOT NULL COMMENT '文档类型：ARTICLE、PAGE、FORUM_THREAD、USER 等',
    `document_id` BIGINT NOT NULL COMMENT '来源业务表主键 ID',
    `title` VARCHAR(500) NOT NULL COMMENT '用于搜索和展示的标题',
    `summary` TEXT NULL COMMENT '用于搜索结果展示的摘要',
    `search_text` LONGTEXT NOT NULL COMMENT '标准化后的可搜索文本',
    `url_path` VARCHAR(1000) NOT NULL COMMENT '站内相对访问路径',
    `owner_user_id` BIGINT NULL COMMENT '内容作者或所有者用户 ID',
    `visibility` VARCHAR(32) NOT NULL DEFAULT 'PUBLIC' COMMENT '可见性：PUBLIC、MEMBERS、PRIVATE',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE、HIDDEN、DELETED',
    `source_updated_at` DATETIME(3) NOT NULL COMMENT '来源记录最后更新时间',
    `indexed_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近构建索引时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_search_documents_type_id` (`document_type`, `document_id`),
    KEY `idx_search_documents_type_status` (`document_type`, `status`, `source_updated_at`),
    KEY `idx_search_documents_owner` (`owner_user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='跨内容、论坛和用户模块的统一搜索投影表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}search_index_queue` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '索引队列主键 ID',
    `event_key` VARCHAR(120) NOT NULL COMMENT '来源事件幂等唯一标识',
    `document_type` VARCHAR(64) NOT NULL COMMENT '需要更新的文档类型',
    `document_id` BIGINT NOT NULL COMMENT '需要更新的业务记录 ID',
    `operation_type` VARCHAR(32) NOT NULL COMMENT '操作：UPSERT、DELETE、REBUILD',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING、PROCESSING、DONE、FAILED',
    `attempt_count` INT NOT NULL DEFAULT 0 COMMENT '已尝试处理次数',
    `available_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下次可处理时间',
    `processed_at` DATETIME(3) NULL COMMENT '处理成功时间',
    `last_error` VARCHAR(1000) NULL COMMENT '最后一次失败的脱敏摘要',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '入队时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_search_index_queue_event_key` (`event_key`),
    KEY `idx_search_index_queue_delivery` (`status`, `available_at`, `id`),
    KEY `idx_search_index_queue_document` (`document_type`, `document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='搜索索引增量更新和失败重试队列表';

INSERT IGNORE INTO `${tablePrefix}permissions`
    (`permission_key`, `name`, `permission_type`, `module_key`, `description`, `built_in`, `enabled`, `sort_order`)
VALUES
    ('search.view', '使用站内搜索', 'action', 'search', '允许搜索有权查看的站内内容。', 1, 1, 10),
    ('search.index.view', '查看搜索索引', 'menu', 'search', '允许查看索引状态和队列。', 1, 1, 20),
    ('search.index.manage', '管理搜索索引', 'action', 'search', '允许重试、重建和清理搜索索引。', 1, 1, 30);

INSERT IGNORE INTO `${tablePrefix}role_permissions` (`role_id`, `permission_id`, `status`)
SELECT r.`id`, p.`id`, 'ACTIVE' FROM `${tablePrefix}roles` r
JOIN `${tablePrefix}permissions` p ON p.`module_key` = 'search' WHERE r.`role_key` = 'super_admin';
INSERT IGNORE INTO `${tablePrefix}admin_group_permissions` (`group_id`, `permission_id`)
SELECT g.`id`, p.`id` FROM `${tablePrefix}admin_groups` g
JOIN `${tablePrefix}permissions` p ON p.`module_key` = 'search' WHERE g.`group_key` = 'super_admin';
