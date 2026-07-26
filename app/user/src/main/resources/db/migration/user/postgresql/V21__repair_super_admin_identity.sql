/*
 * 修复旧安装流程在用户身份字段扩展后创建的残缺超级管理员。
 * 仅处理仍启用且真实绑定 super_admin 角色的账号，避免恢复安全删除账号的 UID。
 */
WITH base AS (
    SELECT COALESCE(MAX(uid), 0) AS max_uid
    FROM ${tablePrefix}users
), candidates AS (
    SELECT DISTINCT candidate.id,
           ROW_NUMBER() OVER (ORDER BY candidate.id) AS offset_value
    FROM ${tablePrefix}users candidate
    JOIN ${tablePrefix}user_roles user_role ON user_role.user_id = candidate.id
    JOIN ${tablePrefix}roles role ON role.id = user_role.role_id
    WHERE role.role_key = 'super_admin'
      AND UPPER(candidate.status) = 'ACTIVE'
      AND candidate.uid IS NULL
)
UPDATE ${tablePrefix}users repaired
SET uid = base.max_uid + candidates.offset_value
FROM base, candidates
WHERE repaired.id = candidates.id;

UPDATE ${tablePrefix}users repaired
SET public_id = 'AQUA_' || LPAD(TO_HEX(repaired.id), 16, '0')
WHERE UPPER(repaired.status) = 'ACTIVE'
  AND (repaired.public_id IS NULL OR BTRIM(repaired.public_id) = '')
  AND EXISTS (
      SELECT 1
      FROM ${tablePrefix}user_roles user_role
      JOIN ${tablePrefix}roles role ON role.id = user_role.role_id
      WHERE user_role.user_id = repaired.id
        AND role.role_key = 'super_admin'
  );
