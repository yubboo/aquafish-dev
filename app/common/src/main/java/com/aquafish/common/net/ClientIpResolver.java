package com.aquafish.common.net;

/**
 * 客户端真实 IP 解析工具。
 *
 * 当前阶段：
 * Step 17 用户系统安全基础能力。
 *
 * 作用：
 * 1. 从 HTTP 请求头中解析用户真实 IP。
 * 2. 支持常见反向代理头，例如 X-Forwarded-For、X-Real-IP。
 * 3. 为后续登录日志、安全审计、违规处理做准备。
 *
 * 为什么不能只用 RemoteAddress：
 * 如果项目部署在 Nginx、1Panel、宝塔、CDN、负载均衡后面，
 * 后端直接拿到的 RemoteAddress 很可能是代理服务器 IP，
 * 不一定是用户真实 IP。
 *
 * 常见真实 IP 来源优先级：
 * 1. X-Forwarded-For
 * 2. X-Real-IP
 * 3. Proxy-Client-IP
 * 4. WL-Proxy-Client-IP
 * 5. RemoteAddress
 *
 * 注意：
 * 1. IP 只能作为安全审计线索，不能等同于用户真实住址。
 * 2. 用户使用 VPN、代理、公司网络、手机流量时，IP 归属地可能不准确。
 * 3. 后续如果接入 CDN，需要根据 CDN 的真实 IP 头进一步适配。
 */
public final class ClientIpResolver {

    /**
     * unknown 是一些代理在无法识别 IP 时可能传回来的字符串。
     */
    private static final String UNKNOWN = "unknown";

    /**
     * 工具类不允许 new。
     */
    private ClientIpResolver() {
    }

    /**
     * 解析客户端 IP。
     *
     * @param xForwardedFor     X-Forwarded-For 请求头，可能包含多个 IP
     * @param xRealIp           X-Real-IP 请求头
     * @param proxyClientIp     Proxy-Client-IP 请求头
     * @param wlProxyClientIp   WL-Proxy-Client-IP 请求头
     * @param remoteAddress     连接来源地址，通常是最后兜底值
     * @return 解析后的客户端 IP，如果无法解析则返回 null
     */
    public static String resolve(
        String xForwardedFor,
        String xRealIp,
        String proxyClientIp,
        String wlProxyClientIp,
        String remoteAddress
    ) {
        /*
         * X-Forwarded-For 可能长这样：
         *
         * 36.112.24.18, 10.0.0.1, 127.0.0.1
         *
         * 第一个 IP 通常是最初客户端 IP。
         */
        String ip = firstValidIpFromForwardedFor(xForwardedFor);

        if (isValid(ip)) {
            return ip;
        }

        if (isValid(xRealIp)) {
            return xRealIp.trim();
        }

        if (isValid(proxyClientIp)) {
            return proxyClientIp.trim();
        }

        if (isValid(wlProxyClientIp)) {
            return wlProxyClientIp.trim();
        }

        if (isValid(remoteAddress)) {
            return cleanRemoteAddress(remoteAddress);
        }

        return null;
    }

    /**
     * 从 X-Forwarded-For 中取第一个有效 IP。
     */
    private static String firstValidIpFromForwardedFor(String value) {
        if (!isValid(value)) {
            return null;
        }

        String[] parts = value.split(",");

        for (String part : parts) {
            String ip = part.trim();

            if (isValid(ip)) {
                return ip;
            }
        }

        return null;
    }

    /**
     * 判断 IP 字符串是否有效。
     *
     * 这里只做基础判断：
     * 1. 不能为空
     * 2. 不能是 unknown
     *
     * 详细 IPv4 / IPv6 格式校验后续可以再加。
     */
    private static boolean isValid(String value) {
        return value != null
            && !value.isBlank()
            && !UNKNOWN.equalsIgnoreCase(value.trim());
    }

    /**
     * 清理 RemoteAddress。
     *
     * RemoteAddress 有时可能带端口，例如：
     * 127.0.0.1:53211
     *
     * 当前先做 IPv4 的端口清理。
     * IPv6 后续再单独完善。
     */
    private static String cleanRemoteAddress(String value) {
        String ip = value.trim();

        int colonIndex = ip.indexOf(':');

        if (colonIndex > 0 && ip.indexOf('.') > 0) {
            return ip.substring(0, colonIndex);
        }

        return ip;
    }
}