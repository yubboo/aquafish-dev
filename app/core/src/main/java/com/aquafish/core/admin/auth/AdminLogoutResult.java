package com.aquafish.core.admin.auth;

/**
 * 后台退出结果。
 */
public record AdminLogoutResult(
    boolean loggedOut,
    String note
) {
}
