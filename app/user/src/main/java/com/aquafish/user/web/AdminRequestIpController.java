package com.aquafish.user.web;

import com.aquafish.common.net.ClientIpResolver;
import com.aquafish.common.web.ApiResult;
import java.net.InetSocketAddress;
import org.springframework.context.annotation.Profile;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开发环境请求 IP 诊断接口。
 *
 * <p>接口会回显代理请求头，只能用于本地调试。正式可信代理规则完成前，
 * 生产环境不得注册该 Controller。</p>
 */
@Profile("dev")
@RestController
@RequestMapping("/api/admin/users")
public class AdminRequestIpController {

    @GetMapping("/request-ip")
    public ApiResult<RequestIpResponse> requestIp(
        ServerHttpRequest request,
        @RequestHeader(name = "X-Forwarded-For", required = false) String xForwardedFor,
        @RequestHeader(name = "X-Real-IP", required = false) String xRealIp,
        @RequestHeader(name = "Proxy-Client-IP", required = false) String proxyClientIp,
        @RequestHeader(name = "WL-Proxy-Client-IP", required = false) String wlProxyClientIp
    ) {
        String remoteAddress = resolveRemoteAddress(request);
        String clientIp = ClientIpResolver.resolve(
            xForwardedFor,
            xRealIp,
            proxyClientIp,
            wlProxyClientIp,
            remoteAddress
        );

        RequestIpResponse data = new RequestIpResponse(
            clientIp,
            remoteAddress,
            xForwardedFor,
            xRealIp,
            proxyClientIp,
            wlProxyClientIp,
            "仅用于 dev Profile 下验证代理请求头和 IP 解析结果。"
        );

        return ApiResult.ok(data, "请求 IP 识别成功");
    }

    private String resolveRemoteAddress(ServerHttpRequest request) {
        InetSocketAddress address = request.getRemoteAddress();

        if (address == null) {
            return null;
        }

        if (address.getAddress() != null) {
            return address.getAddress().getHostAddress();
        }

        return address.getHostString();
    }

    public record RequestIpResponse(
        String clientIp,
        String remoteAddress,
        String xForwardedFor,
        String xRealIp,
        String proxyClientIp,
        String wlProxyClientIp,
        String note
    ) {
    }
}
