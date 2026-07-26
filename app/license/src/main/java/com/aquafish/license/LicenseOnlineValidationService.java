package com.aquafish.license;

import com.aquafish.core.config.WorkDirResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 客户程序的非阻塞在线授权校验服务。
 *
 * <p>业务线程永远只读取内存中的已验签 AQL1 租约，不等待授权中心网络请求。后台刷新时
 * 为每次请求生成随机 nonce，并验证响应的 Ed25519 签名、授权编号、设备码、nonce、
 * 有效期和数据库版本序号；成功后仅把完整签名租约原子写入 workdir。授权中心短期
 * 维护时，ACTIVE 租约可工作到签名有效期；离线 AQF1 授权则完全不依赖本服务。</p>
 */
@Service
public final class LicenseOnlineValidationService {

    private static final int CACHE_SCHEMA_VERSION = 2;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER =
        Base64.getUrlEncoder().withoutPadding();
    private static final Set<String> DENIED_STATES = Set.of(
        "SUSPENDED", "REVOKED", "UNBOUND", "UNKNOWN",
        "INSTANCE_MISMATCH", "NOT_YET_VALID", "EXPIRED"
    );

    private final boolean enabled;
    private final URI endpoint;
    private final Duration refreshInterval;
    private final Duration offlineGrace;
    private final Duration requestTimeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final OnlineLeaseVerifier leaseVerifier;
    private final Path cacheFile;
    private final Clock clock;
    private final AtomicBoolean refreshRunning = new AtomicBoolean(false);
    private final String configurationError;

    /** 只包含 AQL1 的磁盘/内存缓存；所有业务字段都在使用前从已验签载荷派生。 */
    private volatile LicenseOnlineSnapshot snapshot;
    /** 网络失败说明只保留在内存，不能覆盖最近一次有效签名租约。 */
    private volatile String lastFailureMessage;
    /** 防止每个业务 API 在授权中心故障时都触发一次网络请求。 */
    private volatile Instant lastAttemptAt;
    /** 同一进程内拒绝回放 rowVersion 更小的旧租约。 */
    private volatile long highestSequence = -1;

    @Autowired
    public LicenseOnlineValidationService(
        WorkDirResolver workDirResolver,
        ObjectMapper objectMapper,
        OnlineLeaseVerifier leaseVerifier,
        @Value("${aquafish.license.online.enabled:false}") boolean enabled,
        @Value("${aquafish.license.online.url:}") String endpoint,
        @Value("${aquafish.license.online.refresh-seconds:3600}") long refreshSeconds,
        @Value("${aquafish.license.online.offline-grace-seconds:2592000}")
        long offlineGraceSeconds,
        @Value("${aquafish.license.online.connect-timeout-millis:2000}")
        long connectTimeoutMillis,
        @Value("${aquafish.license.online.request-timeout-millis:3000}")
        long requestTimeoutMillis
    ) {
        this(
            enabled,
            endpoint,
            Duration.ofSeconds(Math.max(60, refreshSeconds)),
            Duration.ofSeconds(Math.max(0, offlineGraceSeconds)),
            Duration.ofMillis(Math.max(100, requestTimeoutMillis)),
            objectMapper,
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(100, connectTimeoutMillis)))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(),
            leaseVerifier,
            workDirResolver.licensesDir().resolve("online-status.json"),
            Clock.systemUTC()
        );
    }

    LicenseOnlineValidationService(
        boolean enabled,
        String endpoint,
        Duration refreshInterval,
        Duration offlineGrace,
        Duration requestTimeout,
        ObjectMapper objectMapper,
        HttpClient httpClient,
        OnlineLeaseVerifier leaseVerifier,
        Path cacheFile,
        Clock clock
    ) {
        this.enabled = enabled;
        this.refreshInterval = refreshInterval;
        this.offlineGrace = offlineGrace;
        this.requestTimeout = requestTimeout;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.leaseVerifier = leaseVerifier;
        this.cacheFile = cacheFile.toAbsolutePath().normalize();
        this.clock = clock;

        URI parsedEndpoint = null;
        String error = null;
        if (enabled) {
            try {
                parsedEndpoint = URI.create(endpoint == null ? "" : endpoint.trim());
                if (!isTrustedEndpoint(parsedEndpoint)) {
                    error = "在线授权地址必须使用 HTTPS 并指向 /api/v1/licenses/check，"
                        + "且不能包含凭据、查询参数或片段；仅回环地址联调允许 HTTP。";
                }
            } catch (IllegalArgumentException invalidUrl) {
                error = "在线授权地址格式无效。";
            }
        }
        this.endpoint = parsedEndpoint;
        this.configurationError = error;
        this.snapshot = readSnapshot();
    }

    /** 为旧单元测试和显式离线场景创建无副作用实现。 */
    static LicenseOnlineValidationService disabled() {
        ObjectMapper mapper = new ObjectMapper();
        return new LicenseOnlineValidationService(
            false,
            "",
            Duration.ofHours(1),
            Duration.ofDays(30),
            Duration.ofSeconds(3),
            mapper,
            HttpClient.newHttpClient(),
            new OnlineLeaseVerifier(mapper, java.util.Map.of(), Duration.ZERO, Clock.systemUTC()),
            Path.of("workdir", "licenses", "online-status.disabled.json"),
            Clock.systemUTC()
        );
    }

    /**
     * 立即使用已验签租约作出决定，并在需要时异步刷新。
     *
     * <p>首次启用在线校验但尚未获得租约时，高级能力保持锁定，避免通过删除缓存和反复
     * 重启无限延长“首次宽限期”。获得一次 ACTIVE 租约后，授权中心停机不会立即影响
     * 客户站点，直到签名租约有效期或本地配置的更短上限结束。</p>
     */
    public LicenseOnlineDecision evaluate(LicensePayload license) {
        if (!enabled) {
            return allowed("DISABLED", "在线授权校验未启用。", null, null, null);
        }
        if (configurationError != null) {
            return denied(
                LicenseStatusCode.CONFIGURATION_ERROR,
                "CONFIGURATION_ERROR",
                configurationError,
                null,
                null,
                null
            );
        }

        Instant now = clock.instant();
        OnlineLeasePayload lease = currentLease(license);
        if (shouldRefresh(lease, now)) startRefresh(license);

        if (lease == null) {
            String message = lastFailureMessage == null
                ? "尚未取得可信在线状态，正在连接授权中心。"
                : lastFailureMessage;
            return denied(
                LicenseStatusCode.ONLINE_CHECK_REQUIRED,
                "PENDING",
                message,
                null,
                null,
                lastAttemptAt == null ? now : lastAttemptAt.plus(refreshInterval)
            );
        }

        highestSequence = Math.max(highestSequence, lease.sequence());
        Instant usableUntil = earlier(
            lease.validUntil(),
            lease.issuedAt().plus(offlineGrace)
        );
        Instant nextRefreshAt = lease.issuedAt().plus(effectiveRefresh(lease));
        String message = lastFailureMessage == null ? lease.message() : lastFailureMessage;

        if (DENIED_STATES.contains(lease.status())) {
            return denied(
                statusForRemoteState(lease.status()),
                lease.status(),
                lease.message(),
                lease.issuedAt(),
                usableUntil,
                nextRefreshAt
            );
        }
        if ("ACTIVE".equals(lease.status()) && !now.isAfter(usableUntil)) {
            return allowed(
                lease.status(),
                message,
                lease.issuedAt(),
                usableUntil,
                nextRefreshAt
            );
        }
        return denied(
            LicenseStatusCode.ONLINE_CHECK_REQUIRED,
            "ONLINE_CHECK_REQUIRED",
            "可信在线授权租约已经到期，请恢复网络并重新校验；离线授权文件不受此限制。",
            lease.issuedAt(),
            usableUntil,
            nextRefreshAt
        );
    }

    /**
     * 激活新授权时清除上一授权的租约，并立即为新授权请求签名租约。
     * 不创建可伪造的 PENDING 文件，也不继承旧授权的在线状态。
     */
    public void onLicenseActivated(LicensePayload license) {
        clearSnapshot();
        startRefresh(license);
    }

    /** 取消激活时删除在线租约，防止下一份授权继承前一份授权的设备状态。 */
    public void onLicenseDeactivated() {
        clearSnapshot();
    }

    /** 管理端“重新校验”使用该方法；Future 在后台完成，不占用 WebFlux 事件线程等待网络。 */
    CompletableFuture<LicenseOnlineStatusView> refreshNow(LicensePayload license) {
        if (!enabled || configurationError != null) {
            return CompletableFuture.completedFuture(evaluate(license).view());
        }
        return refresh(license).thenApply(ignored -> evaluate(license).view());
    }

    /** 合并并发刷新，避免同一实例在高并发 API 请求下形成授权中心请求风暴。 */
    private void startRefresh(LicensePayload license) {
        if (!refreshRunning.compareAndSet(false, true)) return;
        refresh(license).whenComplete((ignored, error) -> refreshRunning.set(false));
    }

    private CompletableFuture<Void> refresh(LicensePayload license) {
        Instant attemptedAt = clock.instant();
        lastAttemptAt = attemptedAt;
        String nonce = newNonce();
        try {
            byte[] requestBody = objectMapper.writeValueAsBytes(new OnlineCheckRequest(
                license.licenseId(),
                license.instanceId(),
                nonce
            ));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                .build();
            return httpClient.sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            ).thenAccept(response -> applyResponse(license, nonce, response))
                .exceptionally(error -> {
                    recordFailure("在线授权中心暂时不可达。");
                    return null;
                });
        } catch (Exception requestError) {
            recordFailure("在线授权请求无法创建。");
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * HTTP 外层 status/message 只用于兼容展示，安全判定完全来自已验签 lease。
     * nonce 不一致、签名损坏、设备不符或版本倒退时，保留最近一次有效租约。
     */
    private synchronized void applyResponse(
        LicensePayload license,
        String nonce,
        HttpResponse<String> response
    ) {
        try {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("online authority HTTP " + response.statusCode());
            }
            AuthorityEnvelope envelope = objectMapper.readValue(
                response.body(),
                AuthorityEnvelope.class
            );
            AuthorityDecision data = envelope == null ? null : envelope.data();
            if (!envelope.success() || data == null) {
                throw new IllegalStateException("invalid online authority envelope");
            }
            OnlineLeaseVerification verification = leaseVerifier.verify(
                data.lease(),
                license.licenseId(),
                license.instanceId(),
                nonce
            );
            if (!verification.valid()) {
                recordFailure(verification.message());
                return;
            }
            OnlineLeasePayload lease = verification.payload();
            if (highestSequence >= 0 && lease.sequence() < highestSequence) {
                recordFailure("授权中心返回了旧版本状态，已拒绝可能的回放响应。");
                return;
            }

            highestSequence = Math.max(highestSequence, lease.sequence());
            snapshot = new LicenseOnlineSnapshot(CACHE_SCHEMA_VERSION, data.lease());
            lastFailureMessage = null;
            writeSnapshot(snapshot);
        } catch (Exception invalidResponse) {
            recordFailure("在线授权中心返回了无效或未签名的响应。");
        }
    }

    /** 网络或协议失败不能覆盖最近一次成功租约，只更新内存中的管理员提示。 */
    private void recordFailure(String message) {
        lastFailureMessage = message;
    }

    /**
     * 从缓存取得租约并重新验签。旧 schema、修改后的 JSON、别的授权或别的设备租约
     * 都会被删除；删除缓存不会获得宽限期，只会回到 PENDING 锁定状态。
     */
    private synchronized OnlineLeasePayload currentLease(LicensePayload license) {
        LicenseOnlineSnapshot current = snapshot;
        if (current == null) return null;
        OnlineLeaseVerification verification = leaseVerifier.verify(
            current.lease(),
            license.licenseId(),
            license.instanceId(),
            null
        );
        if (!verification.valid()) {
            lastFailureMessage = verification.message();
            clearSnapshot();
            return null;
        }
        return verification.payload();
    }

    private boolean shouldRefresh(OnlineLeasePayload lease, Instant now) {
        Instant attempted = lastAttemptAt;
        if (attempted != null && now.isBefore(attempted.plus(refreshInterval))) {
            return false;
        }
        return lease == null
            || !now.isBefore(lease.issuedAt().plus(effectiveRefresh(lease)));
    }

    private Duration effectiveRefresh(OnlineLeasePayload lease) {
        long remoteSeconds = Math.max(60, Math.min(86_400, lease.refreshAfterSeconds()));
        return Duration.ofSeconds(Math.min(refreshInterval.toSeconds(), remoteSeconds));
    }

    private LicenseStatusCode statusForRemoteState(String state) {
        return switch (state) {
            case "SUSPENDED" -> LicenseStatusCode.SUSPENDED;
            case "REVOKED" -> LicenseStatusCode.REVOKED;
            case "UNBOUND" -> LicenseStatusCode.DEVICE_UNBOUND;
            case "EXPIRED" -> LicenseStatusCode.EXPIRED;
            case "NOT_YET_VALID" -> LicenseStatusCode.NOT_YET_VALID;
            default -> LicenseStatusCode.ONLINE_CHECK_REQUIRED;
        };
    }

    private LicenseOnlineDecision allowed(
        String state,
        String message,
        Instant checkedAt,
        Instant graceExpiresAt,
        Instant nextRefreshAt
    ) {
        return new LicenseOnlineDecision(
            true,
            null,
            message,
            new LicenseOnlineStatusView(
                enabled,
                state,
                checkedAt,
                graceExpiresAt,
                nextRefreshAt,
                message
            )
        );
    }

    private LicenseOnlineDecision denied(
        LicenseStatusCode status,
        String state,
        String message,
        Instant checkedAt,
        Instant graceExpiresAt,
        Instant nextRefreshAt
    ) {
        return new LicenseOnlineDecision(
            false,
            status,
            message,
            new LicenseOnlineStatusView(
                enabled,
                state,
                checkedAt,
                graceExpiresAt,
                nextRefreshAt,
                message
            )
        );
    }

    /** 只允许 HTTPS；本机回环 HTTP 仅供自动化测试和部署前联调。 */
    private boolean isTrustedEndpoint(URI uri) {
        if (uri == null
            || uri.getHost() == null
            || uri.getUserInfo() != null
            || uri.getQuery() != null
            || uri.getFragment() != null
            || uri.getPath() == null
            || !uri.getPath().endsWith("/api/v1/licenses/check")) {
            return false;
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) return true;
        return "http".equalsIgnoreCase(uri.getScheme())
            && ("127.0.0.1".equals(uri.getHost())
                || "localhost".equalsIgnoreCase(uri.getHost()));
    }

    /** 旧版或损坏缓存按“没有可信租约”处理，绝不回退读取其中的明文 ACTIVE 状态。 */
    private LicenseOnlineSnapshot readSnapshot() {
        if (!Files.isRegularFile(cacheFile)) return null;
        try {
            if (Files.size(cacheFile) > 128 * 1024) return null;
            LicenseOnlineSnapshot value = objectMapper.readValue(
                Files.readString(cacheFile, StandardCharsets.UTF_8),
                LicenseOnlineSnapshot.class
            );
            return value != null
                && value.schemaVersion() == CACHE_SCHEMA_VERSION
                && value.lease() != null
                && value.lease().startsWith("AQL1.")
                ? value
                : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 原子保存 AQL1 签名租约。临时文件与正式文件都不包含私钥、管理员令牌、激活码
     * 或完整 AQF1 授权码；写入失败只影响跨重启缓存，不影响当前内存租约。
     */
    private synchronized void writeSnapshot(LicenseOnlineSnapshot value) {
        if (!enabled || value == null) return;
        Path temporary = cacheFile.resolveSibling(cacheFile.getFileName() + ".tmp");
        try {
            Files.createDirectories(cacheFile.getParent());
            Files.writeString(
                temporary,
                objectMapper.writeValueAsString(value) + System.lineSeparator(),
                StandardCharsets.UTF_8
            );
            try {
                Files.move(
                    temporary,
                    cacheFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, cacheFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // 当前进程仍使用内存中的已验签租约，下一次成功刷新会再次尝试持久化。
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // 临时文件只含公开的签名租约，不含任何签名私钥或管理员凭据。
            }
        }
    }

    private synchronized void clearSnapshot() {
        snapshot = null;
        highestSequence = -1;
        lastAttemptAt = null;
        try {
            Files.deleteIfExists(cacheFile);
        } catch (IOException ignored) {
            // 内存状态已经清除；残留文件下次读取仍必须重新通过签名和授权/设备匹配校验。
        }
    }

    private Instant earlier(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private String newNonce() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    private record OnlineCheckRequest(String licenseId, String instanceId, String nonce) {
    }

    private record AuthorityEnvelope(boolean success, AuthorityDecision data) {
    }

    private record AuthorityDecision(
        String status,
        String message,
        String checkedAt,
        int refreshAfterSeconds,
        String lease
    ) {
    }
}
