/*
 * 用户可复用 UID。
 *
 * id 继续作为数据库内部主键，任何文章、帖子、会话和审计关系都只引用 id。
 * uid 只负责后台与个人中心展示；账号安全删除时把 uid 置空，新账号会复用最小空缺值。
 */
ALTER TABLE `${tablePrefix}users`
    ADD COLUMN `uid` BIGINT UNSIGNED NULL COMMENT '可复用的正整数用户 UID' AFTER `id`;

UPDATE `${tablePrefix}users`
SET `uid` = `id`
WHERE `uid` IS NULL;

ALTER TABLE `${tablePrefix}users`
    ADD UNIQUE KEY `uk_users_uid` (`uid`);

/*
 * 单例锁表用于跨线程、跨应用实例串行分配 UID。
 * 分配服务必须在同一事务中先 SELECT ... FOR UPDATE，再查询最小空缺 UID 并插入用户。
 */
CREATE TABLE `${tablePrefix}user_uid_allocator` (
    `id` TINYINT UNSIGNED NOT NULL COMMENT '固定为 1 的分配锁主键',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近一次获取分配锁的时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户 UID 跨实例事务分配锁';

INSERT IGNORE INTO `${tablePrefix}user_uid_allocator` (`id`) VALUES (1);
