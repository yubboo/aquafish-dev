package com.aquafish.user.auth;

/**
 * 用户注册落库结果。
 *
 * <p>只返回非敏感身份摘要；密码、密码哈希和数据库连接信息永不进入该对象。</p>
 */
public record MemberRegistrationResult(
    long id,
    long uid,
    String publicId,
    String username
) {
}
