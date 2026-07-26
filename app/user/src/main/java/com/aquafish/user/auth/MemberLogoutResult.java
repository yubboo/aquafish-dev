package com.aquafish.user.auth;

/**
 * 前台会员退出结果。
 */
public record MemberLogoutResult(
    boolean revoked
) {
}
