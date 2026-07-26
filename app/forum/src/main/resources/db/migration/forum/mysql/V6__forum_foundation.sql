/*
 * Aquafish MySQL / MariaDB V6：强论坛第一版数据底座。
 *
 * 本迁移的边界：
 * 1. 创建板块、版主、主题、楼层、关注、审核历史和通知发件箱表；
 * 2. 注册论坛第一版稳定权限节点；
 * 3. 不自动创建板块，不伪造运营数据；
 * 4. 不给普通角色自动授予后台论坛管理权。
 *
 * 多数据库原则：
 * 1. 所有表名都使用 ${tablePrefix}，不写死 aq_；
 * 2. 本文件只使用 MySQL 8 和 MariaDB 均支持的结构能力；
 * 3. PostgreSQL 使用同版本的独立方言文件。
 *
 * 关联约束原则：
 * 当前不创建跨模块物理外键。用户、角色属于 user/core 边界，
 * 逻辑引用由领域服务校验，这样便于备份恢复、分模块升级和后续数据迁移。
 */

/* =========================================================
 * 一、论坛板块
 * ========================================================= */

CREATE TABLE IF NOT EXISTS `${tablePrefix}forum_sections` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '板块主键 ID',
    `parent_id` BIGINT NULL COMMENT '父板块 ID，空表示顶级板块',
    `section_key` VARCHAR(120) NOT NULL COMMENT '板块稳定唯一标识',
    `name` VARCHAR(120) NOT NULL COMMENT '板块名称',
    `description` TEXT NULL COMMENT '板块说明',
    `icon` VARCHAR(500) NULL COMMENT '板块图标资源引用',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '同级板块排序值',
    `visibility` VARCHAR(32) NOT NULL DEFAULT 'PUBLIC' COMMENT '可见策略：PUBLIC、MEMBERS、PRIVATE',
    `posting_policy` VARCHAR(32) NOT NULL DEFAULT 'MEMBERS' COMMENT '发布策略：CLOSED、MEMBERS、SELECTED_GROUPS',
    `moderation_policy` VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT '审核策略：NONE、FIRST_POST、ALL_POSTS',
    `thread_count` BIGINT NOT NULL DEFAULT 0 COMMENT '板块可见主题统计',
    `post_count` BIGINT NOT NULL DEFAULT 0 COMMENT '板块可见帖子统计',
    `enabled` SMALLINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    `created_by` BIGINT NOT NULL COMMENT '创建操作人用户 ID',
    `updated_by` BIGINT NOT NULL COMMENT '最后修改人用户 ID',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_forum_sections_section_key` (`section_key`),
    KEY `idx_forum_sections_parent_sort` (`parent_id`, `sort_order`, `id`),
    KEY `idx_forum_sections_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='论坛板块与两级分类表';

/* =========================================================
 * 二、板块版主授权
 * ========================================================= */

CREATE TABLE IF NOT EXISTS `${tablePrefix}forum_section_moderators` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '版主授权主键 ID',
    `section_id` BIGINT NOT NULL COMMENT '被授权板块 ID',
    `user_id` BIGINT NOT NULL COMMENT '版主用户 ID',
    `moderator_type` VARCHAR(32) NOT NULL DEFAULT 'MODERATOR' COMMENT '版主类型：OWNER、MODERATOR、ASSISTANT',
    `permission_scope` TEXT NULL COMMENT '额外限制的权限键集合 JSON',
    `inherit_children` SMALLINT NOT NULL DEFAULT 0 COMMENT '是否管理子板块',
    `starts_at` DATETIME(3) NULL COMMENT '授权生效时间',
    `expires_at` DATETIME(3) NULL COMMENT '授权失效时间',
    `enabled` SMALLINT NOT NULL DEFAULT 1 COMMENT '授权是否启用',
    `created_by` BIGINT NOT NULL COMMENT '授权操作人用户 ID',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_forum_section_moderators_section_user` (`section_id`, `user_id`),
    KEY `idx_forum_section_moderators_user` (`user_id`, `enabled`),
    KEY `idx_forum_section_moderators_expiry` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='论坛板块版主范围授权表';

/* =========================================================
 * 三、主题聚合
 *
 * next_floor 是下一个可分配楼层号。
 * 回复事务必须锁定主题行后递增它，
 * 禁止使用 MAX(floor_number) + 1。
 * ========================================================= */

CREATE TABLE IF NOT EXISTS `${tablePrefix}forum_threads` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主题主键 ID',
    `section_id` BIGINT NOT NULL COMMENT '所属板块 ID',
    `author_user_id` BIGINT NOT NULL COMMENT '主题作者用户 ID',
    `title` VARCHAR(240) NOT NULL COMMENT '主题标题',
    `status` VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT '主题状态：OPEN、CLOSED、HIDDEN、DELETED',
    `moderation_status` VARCHAR(32) NOT NULL DEFAULT 'APPROVED' COMMENT '审核状态：PENDING、APPROVED、REJECTED',
    `pinned_level` SMALLINT NOT NULL DEFAULT 0 COMMENT '置顶级别，0 表示不置顶',
    `featured_level` SMALLINT NOT NULL DEFAULT 0 COMMENT '精华级别，0 表示非精华',
    `reply_count` BIGINT NOT NULL DEFAULT 0 COMMENT '回复数，不含首帖',
    `view_count` BIGINT NOT NULL DEFAULT 0 COMMENT '浏览次数',
    `next_floor` BIGINT NOT NULL DEFAULT 2 COMMENT '下一个可分配楼层，首帖固定为 1',
    `first_post_id` BIGINT NULL COMMENT '首帖 ID，主题创建事务内回填',
    `last_post_id` BIGINT NULL COMMENT '最后可见帖子 ID',
    `last_reply_user_id` BIGINT NULL COMMENT '最后回复人用户 ID',
    `last_reply_at` DATETIME(3) NULL COMMENT '最后回复时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at` DATETIME(3) NULL COMMENT '软删除时间',
    PRIMARY KEY (`id`),
    KEY `idx_forum_threads_section_list`
        (`section_id`, `status`, `moderation_status`, `pinned_level`, `last_reply_at`, `id`),
    KEY `idx_forum_threads_author` (`author_user_id`, `created_at`),
    KEY `idx_forum_threads_last_reply` (`last_reply_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='论坛主题聚合与列表统计表';

/* =========================================================
 * 四、首帖与回复楼层
 * ========================================================= */

CREATE TABLE IF NOT EXISTS `${tablePrefix}forum_posts` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '帖子主键 ID',
    `thread_id` BIGINT NOT NULL COMMENT '所属主题 ID',
    `section_id` BIGINT NOT NULL COMMENT '所属板块 ID，用于审核和分区查询',
    `author_user_id` BIGINT NOT NULL COMMENT '帖子作者用户 ID',
    `floor_number` BIGINT NOT NULL COMMENT '主题内永不复用的楼层号',
    `content_text` LONGTEXT NOT NULL COMMENT '帖子正文原始内容',
    `quoted_post_id` BIGINT NULL COMMENT '引用的帖子 ID',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED' COMMENT '帖子状态：PUBLISHED、HIDDEN、DELETED',
    `moderation_status` VARCHAR(32) NOT NULL DEFAULT 'APPROVED' COMMENT '审核状态：PENDING、APPROVED、REJECTED',
    `edited_at` DATETIME(3) NULL COMMENT '最后编辑时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at` DATETIME(3) NULL COMMENT '软删除时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_forum_posts_thread_floor` (`thread_id`, `floor_number`),
    KEY `idx_forum_posts_thread_list` (`thread_id`, `status`, `floor_number`),
    KEY `idx_forum_posts_author` (`author_user_id`, `created_at`),
    KEY `idx_forum_posts_section_moderation` (`section_id`, `moderation_status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='论坛首帖和回复楼层统一存储表';

/* =========================================================
 * 五、主题关注
 * ========================================================= */

CREATE TABLE IF NOT EXISTS `${tablePrefix}forum_thread_subscriptions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '关注记录主键 ID',
    `thread_id` BIGINT NOT NULL COMMENT '被关注主题 ID',
    `user_id` BIGINT NOT NULL COMMENT '关注用户 ID',
    `notification_mode` VARCHAR(32) NOT NULL DEFAULT 'ALL_REPLIES' COMMENT '通知模式',
    `last_read_post_id` BIGINT NULL COMMENT '用户最后已读帖子 ID',
    `enabled` SMALLINT NOT NULL DEFAULT 1 COMMENT '是否继续关注',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_forum_thread_subscriptions_thread_user` (`thread_id`, `user_id`),
    KEY `idx_forum_thread_subscriptions_user` (`user_id`, `enabled`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='论坛主题关注与已读位置表';

/* =========================================================
 * 六、不可覆盖的审核与管理历史
 * ========================================================= */

CREATE TABLE IF NOT EXISTS `${tablePrefix}forum_moderation_actions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '管理动作主键 ID',
    `section_id` BIGINT NOT NULL COMMENT '动作发生的板块 ID',
    `target_type` VARCHAR(32) NOT NULL COMMENT '目标类型：SECTION、THREAD、POST',
    `target_id` BIGINT NOT NULL COMMENT '目标业务 ID',
    `action_type` VARCHAR(64) NOT NULL COMMENT '审核、隐藏、恢复、关闭、置顶、加精、移动或删除动作',
    `operator_user_id` BIGINT NOT NULL COMMENT '操作人用户 ID',
    `before_state` LONGTEXT NULL COMMENT '操作前关键状态 JSON',
    `after_state` LONGTEXT NULL COMMENT '操作后关键状态 JSON',
    `reason` VARCHAR(500) NULL COMMENT '管理原因',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '动作时间',
    PRIMARY KEY (`id`),
    KEY `idx_forum_moderation_actions_target` (`target_type`, `target_id`, `created_at`),
    KEY `idx_forum_moderation_actions_section` (`section_id`, `created_at`),
    KEY `idx_forum_moderation_actions_operator` (`operator_user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='论坛不可覆盖的审核与管理动作历史';

/* =========================================================
 * 七、通知事务发件箱
 *
 * 发帖和回复事务只写入本表。
 * 通知消费失败不会回滚已经成功的论坛内容。
 * ========================================================= */

CREATE TABLE IF NOT EXISTS `${tablePrefix}forum_notification_outbox` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '发件箱主键 ID',
    `event_key` VARCHAR(100) NOT NULL COMMENT '事件幂等唯一标识',
    `event_type` VARCHAR(100) NOT NULL COMMENT '事件类型',
    `aggregate_type` VARCHAR(32) NOT NULL COMMENT '聚合类型：THREAD 或 POST',
    `aggregate_id` BIGINT NOT NULL COMMENT '聚合 ID',
    `payload` LONGTEXT NOT NULL COMMENT '通知事件 JSON 负载',
    `delivery_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '投递状态：PENDING、PROCESSING、DELIVERED、FAILED',
    `attempt_count` INT NOT NULL DEFAULT 0 COMMENT '已尝试投递次数',
    `available_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下次可投递时间',
    `delivered_at` DATETIME(3) NULL COMMENT '投递成功时间',
    `last_error` VARCHAR(1000) NULL COMMENT '最后一次投递错误',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_forum_notification_outbox_event_key` (`event_key`),
    KEY `idx_forum_notification_outbox_delivery`
        (`delivery_status`, `available_at`, `id`),
    KEY `idx_forum_notification_outbox_aggregate` (`aggregate_type`, `aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='论坛通知事务发件箱';

/* =========================================================
 * 八、注册论坛权限节点
 *
 * INSERT IGNORE 只补充缺失权限，
 * 不覆盖管理员已修改的权限名称和说明。
 * ========================================================= */

INSERT IGNORE INTO `${tablePrefix}permissions`
    (`permission_key`, `name`, `permission_type`, `module_key`, `description`, `built_in`, `enabled`, `sort_order`)
VALUES
    ('forum.thread.read', '阅读论坛主题', 'action', 'forum', '允许查看有权访问板块的主题和楼层。', 1, 1, 10),
    ('forum.thread.create', '发布论坛主题', 'action', 'forum', '允许在符合板块策略时发布新主题。', 1, 1, 20),
    ('forum.thread.reply', '回复论坛主题', 'action', 'forum', '允许回复开放且可回复的主题。', 1, 1, 30),
    ('forum.thread.edit_own', '编辑自己的主题', 'action', 'forum', '允许在规则范围内编辑自己发布的主题。', 1, 1, 40),
    ('forum.post.edit_own', '编辑自己的回复', 'action', 'forum', '允许在规则范围内编辑自己发布的楼层。', 1, 1, 50),
    ('forum.thread.subscribe', '关注论坛主题', 'action', 'forum', '允许关注主题并接收新回复通知。', 1, 1, 60),
    ('forum.attachment.upload', '上传论坛附件', 'action', 'forum', '允许为主题或回复引用已上传的媒体附件。', 1, 1, 70),
    ('forum.section.manage', '管理论坛板块', 'action', 'forum', '允许创建、修改、排序和启停论坛板块。', 1, 1, 100),
    ('forum.thread.audit', '审核论坛主题', 'action', 'forum', '允许审核、拒绝和恢复授权板块的主题。', 1, 1, 110),
    ('forum.thread.close', '关闭论坛主题', 'action', 'forum', '允许关闭或重新打开授权板块的主题。', 1, 1, 120),
    ('forum.thread.pin', '置顶论坛主题', 'action', 'forum', '允许设置或取消主题置顶级别。', 1, 1, 130),
    ('forum.thread.feature', '设置论坛精华', 'action', 'forum', '允许设置或取消主题精华级别。', 1, 1, 140),
    ('forum.thread.move', '移动论坛主题', 'action', 'forum', '允许在授权范围内移动主题所属板块。', 1, 1, 150),
    ('forum.thread.delete', '删除论坛主题', 'action', 'forum', '允许软删除或恢复授权板块的主题。', 1, 1, 160),
    ('forum.post.audit', '审核论坛回复', 'action', 'forum', '允许审核、拒绝和恢复授权板块的回复。', 1, 1, 170),
    ('forum.post.delete', '删除论坛回复', 'action', 'forum', '允许软删除或恢复授权板块的回复。', 1, 1, 180),
    ('forum.moderator.manage', '管理论坛版主', 'action', 'forum', '允许管理板块版主、授权范围和有效期。', 1, 1, 190);
