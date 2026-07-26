package com.aquafish.license;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 授权码专用 JSON 编解码配置。
 *
 * <p>Spring Boot 4 的 Web 层已经逐步切换到 Jackson 3，而 AQF1 授权格式当前明确
 * 使用 Jackson 2 的稳定字节表示。这里单独注册编码器，保证签名载荷的 Instant
 * 始终使用 ISO-8601，不依赖 Web 层自动配置是否存在。</p>
 */
@Configuration(proxyBeanMethods = false)
class LicenseJsonConfiguration {

    @Bean
    ObjectMapper licenseObjectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
