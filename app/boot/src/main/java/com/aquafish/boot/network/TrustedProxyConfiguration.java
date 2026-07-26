package com.aquafish.boot.network;

import com.aquafish.common.net.TrustedProxyClientIpResolver;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Aquafish 可信反向代理网络配置。
 *
 * <p>只有 TCP 直连来源位于配置的可信代理 CIDR 中，
 * 系统才会读取 X-Forwarded-For 和 X-Real-IP。</p>
 */
@Configuration(proxyBeanMethods = false)
public class TrustedProxyConfiguration {

    /**
     * 创建全系统统一使用的客户端 IP 解析器。
     *
     * @param configuredCidrs 逗号分隔的可信代理 IP 或 CIDR
     * @return 不可变的可信代理解析器
     */
    @Bean
    TrustedProxyClientIpResolver trustedProxyClientIpResolver(
        @Value(
            "${aquafish.network.trusted-proxies:" +
            "127.0.0.1/32,::1/128}"
        )
        String configuredCidrs
    ) {
        List<String> cidrs =
            Arrays.stream(
                    configuredCidrs == null
                        ? new String[0]
                        : configuredCidrs.split(",")
                )
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .toList();

        return new TrustedProxyClientIpResolver(
            cidrs
        );
    }
}
