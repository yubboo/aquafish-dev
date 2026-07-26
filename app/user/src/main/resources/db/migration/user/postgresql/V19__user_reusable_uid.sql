/*
 * 用户可复用 UID。
 *
 * id 继续作为数据库内部主键，任何文章、帖子、会话和审计关系都只引用 id。
 * uid 只负责后台与个人中心展示；账号安全删除时把 uid 置空，新账号会复用最小空缺值。
 */
ALTER TABLE ${tablePrefix}users
    ADD COLUMN uid BIGINT;

UPDATE ${tablePrefix}users
SET uid = id
WHERE uid IS NULL;

CREATE UNIQUE INDEX uk_users_uid
    ON ${tablePrefix}users (uid)
    WHERE uid IS NOT NULL;

COMMENT ON COLUMN ${tablePrefix}users.uid IS '可复用的正整数用户 UID';

/*
 * 单例锁表用于跨线程、跨应用实例串行分配 UID。
 * 分配服务必须在同一事务中先 SELECT ... FOR UPDATE，再查询最小空缺 UID 并插入用户。
 */
CREATE TABLE ${tablePrefix}user_uid_allocator (
    id SMALLINT PRIMARY KEY,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ${tablePrefix}user_uid_allocator IS '用户 UID 跨实例事务分配锁';
COMMENT ON COLUMN ${tablePrefix}user_uid_allocator.id IS '固定为 1 的分配锁主键';

INSERT INTO ${tablePrefix}user_uid_allocator (id)
VALUES (1)
ON CONFLICT (id) DO NOTHING;
