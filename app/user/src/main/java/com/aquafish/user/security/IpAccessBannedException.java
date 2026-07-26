package com.aquafish.user.security;

/**
 * 当前请求命中有效 IP 封禁规则。
 */
public class IpAccessBannedException extends RuntimeException {

    public IpAccessBannedException(String message) {
        super(message);
    }
}
