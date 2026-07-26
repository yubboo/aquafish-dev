/*
 * 修复旧安装流程在用户身份字段扩展后创建的残缺超级管理员。
 * 仅处理仍启用且真实绑定 super_admin 角色的账号，避免恢复安全删除账号的 UID。
 */
UPDATE `${tablePrefix}users` repaired
JOIN (
    SELECT candidate.id,
           base.max_uid + candidate.id AS repaired_uid
    FROM `${tablePrefix}users` candidate
    JOIN `${tablePrefix}user_roles` user_role ON user_role.user_id = candidate.id
    JOIN `${tablePrefix}roles` role ON role.id = user_role.role_id
    CROSS JOIN (
        SELECT COALESCE(MAX(uid), 0) AS max_uid
        FROM `${tablePrefix}users`
    ) base
    WHERE role.role_key = 'super_admin'
      AND UPPER(candidate.status) = 'ACTIVE'
      AND candidate.uid IS NULL
) values_to_apply ON values_to_apply.id = repaired.id
SET repaired.uid = values_to_apply.repaired_uid;

UPDATE `${tablePrefix}users` repaired
JOIN `${tablePrefix}user_roles` user_role ON user_role.user_id = repaired.id
JOIN `${tablePrefix}roles` role ON role.id = user_role.role_id
SET repaired.public_id = CONCAT(
    'AQUA_',
    UPPER(REPLACE(UUID(), '-', ''))
)
WHERE role.role_key = 'super_admin'
  AND UPPER(repaired.status) = 'ACTIVE'
  AND (repaired.public_id IS NULL OR TRIM(repaired.public_id) = '');
