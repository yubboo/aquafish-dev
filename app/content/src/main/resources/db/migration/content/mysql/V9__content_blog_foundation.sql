/*
 * Aquafish MySQL / MariaDB V9：内容、博客、页面和评论数据底座。
 *
 * 设计参考：
 * 1. 借鉴 WordPress 将内容、分类、标签、评论分表的成熟边界；
 * 2. 不复制 wp_posts 的“所有对象共用一张大表”做法，文章和独立页面保持清晰分表；
 * 3. 正文版本使用不可覆盖的 revision 记录，媒体只引用 core.media_assets；
 * 4. 所有逻辑关联由领域服务校验，当前不建立跨模块物理外键。
 */

CREATE TABLE IF NOT EXISTS `${tablePrefix}content_articles` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文章主键 ID',
    `public_id` VARCHAR(36) NOT NULL COMMENT '对外使用的不可猜测 UUID',
    `author_user_id` BIGINT NOT NULL COMMENT '作者用户 ID',
    `title` VARCHAR(240) NOT NULL COMMENT '文章标题',
    `slug` VARCHAR(191) NOT NULL COMMENT '站内唯一固定链接标识',
    `excerpt` TEXT NULL COMMENT '文章摘要',
    `content_text` LONGTEXT NOT NULL COMMENT '文章正文原始内容',
    `cover_media_id` BIGINT NULL COMMENT '封面媒体资源 ID',
    `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT、REVIEW、PUBLISHED、ARCHIVED',
    `visibility` VARCHAR(32) NOT NULL DEFAULT 'PUBLIC' COMMENT '可见性：PUBLIC、MEMBERS、PRIVATE',
    `comment_status` VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT '评论状态：OPEN、CLOSED',
    `seo_title` VARCHAR(240) NULL COMMENT 'SEO 标题',
    `seo_description` VARCHAR(500) NULL COMMENT 'SEO 描述',
    `view_count` BIGINT NOT NULL DEFAULT 0 COMMENT '浏览次数',
    `comment_count` BIGINT NOT NULL DEFAULT 0 COMMENT '已通过评论数',
    `revision_number` INT NOT NULL DEFAULT 1 COMMENT '当前版本号',
    `published_at` DATETIME(3) NULL COMMENT '首次发布时间',
    `scheduled_at` DATETIME(3) NULL COMMENT '计划发布时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at` DATETIME(3) NULL COMMENT '软删除时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_content_articles_public_id` (`public_id`),
    UNIQUE KEY `uk_content_articles_slug` (`slug`),
    KEY `idx_content_articles_status_publish` (`status`, `published_at`, `id`),
    KEY `idx_content_articles_author` (`author_user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='博客文章、新闻和知识内容主表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}content_pages` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '独立页面主键 ID',
    `public_id` VARCHAR(36) NOT NULL COMMENT '对外使用的不可猜测 UUID',
    `parent_id` BIGINT NULL COMMENT '父页面 ID',
    `author_user_id` BIGINT NOT NULL COMMENT '作者用户 ID',
    `title` VARCHAR(240) NOT NULL COMMENT '页面标题',
    `slug` VARCHAR(191) NOT NULL COMMENT '站内唯一固定链接标识',
    `content_text` LONGTEXT NOT NULL COMMENT '页面正文原始内容',
    `template_key` VARCHAR(120) NOT NULL DEFAULT 'default' COMMENT '主题页面模板标识',
    `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT、REVIEW、PUBLISHED、ARCHIVED',
    `visibility` VARCHAR(32) NOT NULL DEFAULT 'PUBLIC' COMMENT '可见性：PUBLIC、MEMBERS、PRIVATE',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '同级页面排序值',
    `seo_title` VARCHAR(240) NULL COMMENT 'SEO 标题',
    `seo_description` VARCHAR(500) NULL COMMENT 'SEO 描述',
    `revision_number` INT NOT NULL DEFAULT 1 COMMENT '当前版本号',
    `published_at` DATETIME(3) NULL COMMENT '首次发布时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at` DATETIME(3) NULL COMMENT '软删除时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_content_pages_public_id` (`public_id`),
    UNIQUE KEY `uk_content_pages_slug` (`slug`),
    KEY `idx_content_pages_parent_sort` (`parent_id`, `sort_order`, `id`),
    KEY `idx_content_pages_status` (`status`, `published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='关于页、协议页和自定义独立页面主表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}content_categories` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类主键 ID',
    `parent_id` BIGINT NULL COMMENT '父分类 ID',
    `category_key` VARCHAR(120) NOT NULL COMMENT '分类稳定标识',
    `name` VARCHAR(120) NOT NULL COMMENT '分类名称',
    `slug` VARCHAR(191) NOT NULL COMMENT '分类固定链接标识',
    `description` TEXT NULL COMMENT '分类说明',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '同级排序值',
    `article_count` BIGINT NOT NULL DEFAULT 0 COMMENT '已发布文章统计',
    `enabled` SMALLINT NOT NULL DEFAULT 1 COMMENT '分类是否启用',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_content_categories_key` (`category_key`),
    UNIQUE KEY `uk_content_categories_slug` (`slug`),
    KEY `idx_content_categories_parent_sort` (`parent_id`, `sort_order`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='文章层级分类表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}content_tags` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '标签主键 ID',
    `tag_key` VARCHAR(120) NOT NULL COMMENT '标签稳定标识',
    `name` VARCHAR(120) NOT NULL COMMENT '标签名称',
    `slug` VARCHAR(191) NOT NULL COMMENT '标签固定链接标识',
    `description` TEXT NULL COMMENT '标签说明',
    `article_count` BIGINT NOT NULL DEFAULT 0 COMMENT '已发布文章统计',
    `enabled` SMALLINT NOT NULL DEFAULT 1 COMMENT '标签是否启用',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_content_tags_key` (`tag_key`),
    UNIQUE KEY `uk_content_tags_slug` (`slug`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='文章扁平标签表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}content_article_categories` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文章分类关系主键 ID',
    `article_id` BIGINT NOT NULL COMMENT '文章 ID',
    `category_id` BIGINT NOT NULL COMMENT '分类 ID',
    `is_primary` SMALLINT NOT NULL DEFAULT 0 COMMENT '是否为主分类',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '关联时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_content_article_categories_pair` (`article_id`, `category_id`),
    KEY `idx_content_article_categories_category` (`category_id`, `article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='文章与分类多对多关系表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}content_article_tags` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文章标签关系主键 ID',
    `article_id` BIGINT NOT NULL COMMENT '文章 ID',
    `tag_id` BIGINT NOT NULL COMMENT '标签 ID',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '关联时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_content_article_tags_pair` (`article_id`, `tag_id`),
    KEY `idx_content_article_tags_tag` (`tag_id`, `article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='文章与标签多对多关系表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}content_comments` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '评论主键 ID',
    `target_type` VARCHAR(32) NOT NULL COMMENT '评论目标：ARTICLE、PAGE',
    `target_id` BIGINT NOT NULL COMMENT '被评论内容 ID',
    `parent_id` BIGINT NULL COMMENT '直接回复的父评论 ID',
    `root_id` BIGINT NULL COMMENT '评论线程根评论 ID',
    `author_user_id` BIGINT NULL COMMENT '登录评论用户 ID',
    `guest_name` VARCHAR(120) NULL COMMENT '游客显示名称',
    `guest_email_hash` VARCHAR(64) NULL COMMENT '游客邮箱摘要，禁止保存明文邮箱',
    `content_text` LONGTEXT NOT NULL COMMENT '评论正文',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING、APPROVED、SPAM、TRASH',
    `ip_hash` VARCHAR(64) NULL COMMENT '评论来源 IP 的带密钥摘要',
    `user_agent` VARCHAR(500) NULL COMMENT '浏览器或客户端标识',
    `reviewer_user_id` BIGINT NULL COMMENT '审核管理员用户 ID',
    `reviewed_at` DATETIME(3) NULL COMMENT '审核时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at` DATETIME(3) NULL COMMENT '软删除时间',
    PRIMARY KEY (`id`),
    KEY `idx_content_comments_target` (`target_type`, `target_id`, `status`, `created_at`),
    KEY `idx_content_comments_parent` (`parent_id`, `created_at`),
    KEY `idx_content_comments_review` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='文章和独立页面评论及审核表';

CREATE TABLE IF NOT EXISTS `${tablePrefix}content_revisions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '内容版本主键 ID',
    `target_type` VARCHAR(32) NOT NULL COMMENT '版本目标：ARTICLE、PAGE',
    `target_id` BIGINT NOT NULL COMMENT '内容业务 ID',
    `revision_number` INT NOT NULL COMMENT '目标内递增版本号',
    `editor_user_id` BIGINT NOT NULL COMMENT '编辑用户 ID',
    `title` VARCHAR(240) NOT NULL COMMENT '该版本标题快照',
    `content_text` LONGTEXT NOT NULL COMMENT '该版本正文快照',
    `change_summary` VARCHAR(500) NULL COMMENT '本次修改摘要',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '版本创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_content_revisions_target_number`
        (`target_type`, `target_id`, `revision_number`),
    KEY `idx_content_revisions_editor` (`editor_user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='文章和页面不可覆盖的版本历史表';

INSERT IGNORE INTO `${tablePrefix}permissions`
    (`permission_key`, `name`, `permission_type`, `module_key`, `description`, `built_in`, `enabled`, `sort_order`)
VALUES
    ('content.view', '查看内容', 'menu', 'content', '允许进入内容管理模块。', 1, 1, 10),
    ('content.article.manage', '管理文章', 'action', 'content', '允许创建、编辑和归档文章。', 1, 1, 20),
    ('content.article.publish', '发布文章', 'action', 'content', '允许审核、发布和撤回文章。', 1, 1, 30),
    ('content.page.manage', '管理独立页面', 'action', 'content', '允许维护独立页面。', 1, 1, 40),
    ('content.taxonomy.manage', '管理分类标签', 'action', 'content', '允许维护文章分类和标签。', 1, 1, 50),
    ('content.comment.manage', '管理内容评论', 'action', 'content', '允许审核、标记垃圾和删除评论。', 1, 1, 60);

INSERT IGNORE INTO `${tablePrefix}role_permissions` (`role_id`, `permission_id`, `status`)
SELECT r.`id`, p.`id`, 'ACTIVE'
FROM `${tablePrefix}roles` r
JOIN `${tablePrefix}permissions` p ON p.`module_key` = 'content'
WHERE r.`role_key` = 'super_admin';

INSERT IGNORE INTO `${tablePrefix}admin_group_permissions` (`group_id`, `permission_id`)
SELECT g.`id`, p.`id`
FROM `${tablePrefix}admin_groups` g
JOIN `${tablePrefix}permissions` p ON p.`module_key` = 'content'
WHERE g.`group_key` = 'super_admin';
