/* Aquafish MySQL / MariaDB V10：论坛举报、表态与补充权限。 */

CREATE TABLE IF NOT EXISTS `${tablePrefix}forum_reports` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '举报主键 ID',
    `reporter_user_id` BIGINT NOT NULL COMMENT '举报用户 ID',
    `section_id` BIGINT NOT NULL COMMENT '目标所在板块 ID',
    `target_type` VARCHAR(32) NOT NULL COMMENT '举报目标：THREAD、POST、USER',
    `target_id` BIGINT NOT NULL COMMENT '被举报业务 ID',
    `reason_code` VARCHAR(64) NOT NULL COMMENT '举报原因稳定编码',
    `reason_text` VARCHAR(1000) NULL COMMENT '举报补充说明',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING、PROCESSING、RESOLVED、REJECTED',
    `assignee_user_id` BIGINT NULL COMMENT '当前处理管理员用户 ID',
    `resolution` VARCHAR(1000) NULL COMMENT '处理结论',
    `resolved_at` DATETIME(3) NULL COMMENT '完成处理时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '举报时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_forum_reports_queue` (`status`, `created_at`, `id`),
    KEY `idx_forum_reports_target` (`target_type`, `target_id`, `status`),
    KEY `idx_forum_reports_reporter` (`reporter_user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='论坛主题、回复和用户举报处理表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}forum_post_reactions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '帖子表态主键 ID',
    `post_id` BIGINT NOT NULL COMMENT '帖子 ID',
    `user_id` BIGINT NOT NULL COMMENT '表态用户 ID',
    `reaction_type` VARCHAR(32) NOT NULL DEFAULT 'LIKE' COMMENT '表态类型：LIKE、THANKS、USEFUL 等',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '表态时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_forum_post_reactions_post_user_type`
        (`post_id`, `user_id`, `reaction_type`),
    KEY `idx_forum_post_reactions_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='论坛帖子点赞、感谢和有用等表态关系表';

INSERT IGNORE INTO `${tablePrefix}permissions`
    (`permission_key`, `name`, `permission_type`, `module_key`, `description`, `built_in`, `enabled`, `sort_order`)
VALUES
    ('forum.report.create', '提交论坛举报', 'action', 'forum', '允许举报主题、回复或用户。', 1, 1, 80),
    ('forum.report.manage', '处理论坛举报', 'action', 'forum', '允许受理和关闭论坛举报。', 1, 1, 200),
    ('forum.reaction.create', '论坛帖子表态', 'action', 'forum', '允许对帖子点赞、感谢或标记有用。', 1, 1, 90);

INSERT IGNORE INTO `${tablePrefix}role_permissions` (`role_id`, `permission_id`, `status`)
SELECT r.`id`, p.`id`, 'ACTIVE'
FROM `${tablePrefix}roles` r
JOIN `${tablePrefix}permissions` p ON p.`module_key` = 'forum'
WHERE r.`role_key` = 'super_admin';

INSERT IGNORE INTO `${tablePrefix}admin_group_permissions` (`group_id`, `permission_id`)
SELECT g.`id`, p.`id`
FROM `${tablePrefix}admin_groups` g
JOIN `${tablePrefix}permissions` p ON p.`module_key` = 'forum'
WHERE g.`group_key` = 'super_admin';
