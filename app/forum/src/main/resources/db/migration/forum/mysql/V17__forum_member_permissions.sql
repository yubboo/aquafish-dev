/* Aquafish MySQL / MariaDB V17：默认会员组论坛前台权限。 */

/*
 * 只补充 member 用户组尚不存在的权限，不覆盖管理员自定义 permission_value。
 * 管理权限不授予前台会员组；指定板块和私有板块范围仍默认拒绝。
 */
INSERT IGNORE INTO `${tablePrefix}user_group_permissions`
    (`group_id`, `permission_key`, `permission_value`)
SELECT
    g.`id`,
    p.`permission_key`,
    NULL
FROM `${tablePrefix}user_groups` g
JOIN `${tablePrefix}permissions` p
  ON p.`permission_key` IN (
      'forum.thread.read',
      'forum.thread.create',
      'forum.thread.reply',
      'forum.thread.edit_own',
      'forum.post.edit_own',
      'forum.thread.subscribe',
      'forum.attachment.upload'
  )
WHERE g.`group_key` = 'member';
