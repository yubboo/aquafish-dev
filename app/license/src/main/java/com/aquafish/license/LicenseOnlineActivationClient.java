package com.aquafish.license;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Aquafish 到独立授权中心的在线激活客户端。
 *
 * <p>请求只包含客户持有的 AQO1 激活码和本机设备码；响应中的 AQF1 必须再通过
 * LicenseTokenVerifier 本地验签后才会保存。即使 DNS、代理或网络响应被篡改，攻击者
 * 没有 Ed25519 私钥也无法向 Aquafish 注入伪造权益。</p>
 */
@Service
public final class LicenseOnlineActivationClient {
    private final boolean enabled;
    private final URI endpoint;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String configurationError;

    @Autowired
    public LicenseOnlineActivationClient(
        ObjectMapper objectMapper,
        @Value("${aquafish.license.online.enabled:false}") boolean enabled,
        @Value("${aquafish.license.online.activation-url:}") String endpoint,
        @Value("${aquafish.license.online.connect-timeout-millis:2000}") long connectTimeoutMillis,
        @Value("${aquafish.license.online.request-timeout-millis:3000}") long requestTimeoutMillis
    ) {
        this(
            enabled,
            endpoint,
            Duration.ofMillis(Math.max(100, requestTimeoutMillis)),
            objectMapper,
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(100, connectTimeoutMillis)))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
        );
    }

    LicenseOnlineActivationClient(
        boolean enabled,
        String endpoint,
        Duration timeout,
        ObjectMapper objectMapper,
        HttpClient httpClient
    ) {
        this.enabled = enabled;
        this.timeout = timeout;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        URI parsed = null;
        String error = null;
        if (enabled) {
            try {
                parsed = URI.create(endpoint == null ? "" : endpoint.trim());
                if (!isTrustedEndpoint(parsed)) {
                    error = "在线激活地址必须使用 HTTPS 并指向 /api/v1/activations；"
                        + "只有本机联调允许 HTTP。";
                }
            } catch (IllegalArgumentException invalid) {
                error = "在线激活地址格式无效。";
            }
        }
        this.endpoint = parsed;
        this.configurationError = error;
    }

    /** 为单元测试和显式离线场景创建无网络副作用实现。 */
    static LicenseOnlineActivationClient disabled() {
        return new LicenseOnlineActivationClient(
            false,
            "",
            Duration.ofSeconds(3),
            new ObjectMapper(),
            HttpClient.newHttpClient()
        );
    }

    /** 异步请求授权中心，不占用 Netty 事件循环线程。 */
    public CompletableFuture<String> activate(String activationCode, String instanceId) {
        if (!enabled) {
            return CompletableFuture.failedFuture(new LicenseActivationException(
                "ONLINE_ACTIVATION_DISABLED", "在线授权中心尚未启用。"
            ));
        }
        if (configurationError != null) {
            return CompletableFuture.failedFuture(new LicenseActivationException(
                "ONLINE_ACTIVATION_CONFIGURATION_ERROR", configurationError
            ));
        }
        try {
            byte[] body = objectMapper.writeValueAsBytes(
                new AuthorityActivationRequest(activationCode, instanceId)
            );
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(this::readGrant)
                .exceptionally(error -> {
                    Throwable cause = unwrap(error);
                    if (cause instanceof LicenseActivationException activationError) {
                        throw activationError;
                    }
                    throw new LicenseActivationException(
                        "ONLINE_ACTIVATION_UNAVAILABLE",
                        "暂时无法连接 Aquafish 授权中心，请检查网络或改用离线设备码授权。"
                    );
                });
        } catch (Exception error) {
            return CompletableFuture.failedFuture(new LicenseActivationException(
                "ONLINE_ACTIVATION_REQUEST_FAILED", "在线激活请求无法创建。"
            ));
        }
    }

    private String readGrant(HttpResponse<String> response) {
        try {
            AuthorityEnvelope envelope = objectMapper.readValue(response.body(), AuthorityEnvelope.class);
            if (response.statusCode() != 200 || !envelope.success() || envelope.data() == null) {
                String message = envelope.message() == null || envelope.message().isBlank()
                    ? "授权中心拒绝了在线激活请求。"
                    : envelope.message();
                String code = envelope.code() == null || envelope.code().isBlank()
                    ? "ONLINE_ACTIVATION_REJECTED"
                    : envelope.code();
                throw new LicenseActivationException(code, message);
            }
            String licenseCode = envelope.data().licenseCode();
            if (licenseCode == null || licenseCode.isBlank()) {
                throw new LicenseActivationException(
                    "ONLINE_ACTIVATION_INVALID_RESPONSE", "授权中心没有返回签名授权码。"
                );
            }
            return licenseCode;
        } catch (LicenseActivationException error) {
            throw error;
        } catch (Exception error) {
            throw new LicenseActivationException(
                "ONLINE_ACTIVATION_INVALID_RESPONSE", "授权中心响应格式无效。"
            );
        }
    }

    private boolean isTrustedEndpoint(URI uri) {
        if (uri == null || uri.getHost() == null || uri.getUserInfo() != null
            || uri.getQuery() != null || uri.getFragment() != null
            || !"/api/v1/activations".equals(uri.getPath())) {
            return false;
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) return true;
        return "http".equalsIgnoreCase(uri.getScheme())
            && ("127.0.0.1".equals(uri.getHost()) || "localhost".equalsIgnoreCase(uri.getHost()));
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private record AuthorityActivationRequest(String activationCode, String instanceId) {
    }

    private record AuthorityEnvelope(
        boolean success,
        AuthorityActivationData data,
        String code,
        String message
    ) {
    }

    private record AuthorityActivationData(String licenseCode) {
    }
}
