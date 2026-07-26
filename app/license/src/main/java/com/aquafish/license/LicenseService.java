package com.aquafish.license;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 系统平台许可证用例服务。
 *
 * <p>关联流程：状态查询、授权码激活、取消激活、API 强制授权。Controller 和
 * WebFilter 都只调用本服务，避免出现“页面显示有效、拦截器却判定无效”的双重逻辑。</p>
 */
@Service
public final class LicenseService {

    private final LicenseInstanceIdentityService identityService;
    private final LicenseFileStore fileStore;
    private final LicenseTokenVerifier verifier;
    private final boolean enforcementEnabled;
    private final LicenseOnlineValidationService onlineValidationService;
    private final LicenseOnlineActivationClient onlineActivationClient;
    private final String portalUrl;

    @Autowired
    public LicenseService(
        LicenseInstanceIdentityService identityService,
        LicenseFileStore fileStore,
        LicenseTokenVerifier verifier,
        LicenseEnforcementPolicy enforcementPolicy,
        LicenseOnlineValidationService onlineValidationService,
        LicenseOnlineActivationClient onlineActivationClient,
        @Value("${aquafish.license.portal-url:}") String portalUrl
    ) {
        this(
            identityService,
            fileStore,
            verifier,
            enforcementPolicy.enforcementEnabled(),
            onlineValidationService,
            onlineActivationClient,
            portalUrl
        );
    }

    private LicenseService(
        LicenseInstanceIdentityService identityService,
        LicenseFileStore fileStore,
        LicenseTokenVerifier verifier,
        boolean enforcementEnabled,
        LicenseOnlineValidationService onlineValidationService,
        LicenseOnlineActivationClient onlineActivationClient,
        String portalUrl
    ) {
        this.identityService = identityService;
        this.fileStore = fileStore;
        this.verifier = verifier;
        this.enforcementEnabled = enforcementEnabled;
        this.onlineValidationService = onlineValidationService;
        this.onlineActivationClient = onlineActivationClient;
        this.portalUrl = normalizePortalUrl(portalUrl);
    }

    /** 测试和显式离线调用使用的兼容构造，不会发起任何网络请求。 */
    LicenseService(
        LicenseInstanceIdentityService identityService,
        LicenseFileStore fileStore,
        LicenseTokenVerifier verifier,
        boolean enforcementEnabled
    ) {
        this(
            identityService,
            fileStore,
            verifier,
            enforcementEnabled,
            LicenseOnlineValidationService.disabled(),
            LicenseOnlineActivationClient.disabled(),
            ""
        );
    }

    /**
     * 使用授权中心 AQO1 激活码完成在线激活。
     *
     * <p>授权中心返回的 AQF1 仍必须经过本机 Ed25519 验签和设备码校验，验证成功后
     * 才原子保存；网络响应不能直接改变授权状态。</p>
     */
    public CompletableFuture<LicenseStatusView> activateOnline(String activationCode) {
        final String instanceId;
        try {
            instanceId = identityService.instanceId();
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(new LicenseActivationException(
                "LICENSE_INSTANCE_UNAVAILABLE", error.getMessage()
            ));
        }
        return onlineActivationClient.activate(activationCode, instanceId).thenApply(licenseCode -> {
            LicenseVerification verification = verifier.verify(licenseCode, instanceId);
            if (!verification.valid()) {
                throw new LicenseActivationException(
                    "LICENSE_" + verification.status().name(), verification.message()
                );
            }
            fileStore.save(licenseCode);
            onlineValidationService.onLicenseActivated(verification.payload());
            return toView(instanceId, verification);
        });
    }

    /**
     * 返回实时授权状态，每次都重新读取并验签，过期授权不会因内存缓存继续放行。
     */
    public LicenseStatusView status() {
        String instanceId;
        try {
            instanceId = identityService.instanceId();
        } catch (RuntimeException error) {
            return view(
                LicenseStatusCode.CONFIGURATION_ERROR,
                false,
                "不可用",
                null,
                null,
                error.getMessage()
            );
        }

        try {
            return fileStore.read()
                .map(code -> toView(instanceId, verifier.verify(code, instanceId)))
                .orElseGet(() -> view(
                    LicenseStatusCode.NOT_ACTIVATED,
                    false,
                    instanceId,
                    null,
                    null,
                    enforcementEnabled
                        ? "系统尚未激活，请输入与当前设备码匹配的授权码。"
                        : "系统尚未激活；当前为开发环境，授权拦截已关闭。"
                ));
        } catch (RuntimeException error) {
            return view(
                LicenseStatusCode.CONFIGURATION_ERROR,
                false,
                instanceId,
                null,
                null,
                error.getMessage()
            );
        }
    }

    /**
     * 先完整验签再持久化，错误授权码不会覆盖当前有效授权。
     */
    public LicenseStatusView activate(String licenseCode) {
        String instanceId = identityService.instanceId();
        LicenseVerification verification = verifier.verify(licenseCode, instanceId);
        if (!verification.valid()) {
            throw new LicenseActivationException(
                "LICENSE_" + verification.status().name(),
                verification.message()
            );
        }

        fileStore.save(licenseCode);
        onlineValidationService.onLicenseActivated(verification.payload());
        return toView(instanceId, verification);
    }

    /**
     * 取消本机激活，只删除本地授权文件，不修改实例 ID。
     */
    public LicenseStatusView deactivate() {
        fileStore.delete();
        onlineValidationService.onLicenseDeactivated();
        return status();
    }

    /**
     * 管理员主动在线复核当前授权。
     *
     * <p>只用于授权管理按钮，不进入普通业务请求链。没有本地授权或本地验签失败时直接
     * 返回当前状态；本地有效时等待一次有超时限制的在线请求完成，再返回最新快照。</p>
     */
    public CompletableFuture<LicenseStatusView> refreshOnline() {
        try {
            String instanceId = identityService.instanceId();
            return fileStore.read()
                .map(code -> {
                    LicenseVerification verification = verifier.verify(code, instanceId);
                    if (!verification.valid() || verification.payload() == null) {
                        return CompletableFuture.completedFuture(toView(instanceId, verification));
                    }
                    return onlineValidationService.refreshNow(verification.payload())
                        .thenApply(ignored -> toView(instanceId, verification));
                })
                .orElseGet(() -> CompletableFuture.completedFuture(status()));
        } catch (RuntimeException error) {
            return CompletableFuture.completedFuture(status());
        }
    }

    /**
     * 判断某个模块在当前授权状态下是否可用。
     *
     * <p>开发环境关闭 enforcement 时全部放行；正式环境必须先通过整个平台验签，
     * 再由 {@link LicenseFeature} 检查 features。WebFilter 与未来服务层授权判断都应
     * 调用这里，不能自行复制一套规则。</p>
     */
    public boolean isFeatureUsable(LicenseStatusView status, LicenseFeature feature) {
        if (status == null || feature == null) {
            return false;
        }
        if (!status.enforcementEnabled()) {
            return true;
        }
        return status.valid() && feature.grantedBy(status.features());
    }

    /**
     * 校验官方主题、插件等具体商品权益。平台无效或未包含对应模块时，本方法不会仅凭
     * entitlement 放行，避免客户只复制主题权益字段绕过系统平台授权。
     */
    public boolean isAssetUsable(
        LicenseStatusView status,
        LicenseFeature requiredFeature,
        String assetType,
        String assetId
    ) {
        if (!isFeatureUsable(status, requiredFeature)) {
            return false;
        }
        if (!status.enforcementEnabled()) {
            return true;
        }
        return status.entitlements().stream()
            .anyMatch(item -> item.matches(assetType, assetId));
    }

    /** 把验签内部结果转换成允许返回浏览器的脱敏状态对象。 */
    private LicenseStatusView toView(String instanceId, LicenseVerification verification) {
        LicensePayload payload = verification.payload();
        LicenseOnlineDecision onlineDecision = payload == null || !verification.valid()
            ? null
            : onlineValidationService.evaluate(payload);
        if (onlineDecision != null && !onlineDecision.usable()) {
            return view(
                onlineDecision.deniedStatus(),
                false,
                instanceId,
                payload,
                onlineDecision.view(),
                onlineDecision.message()
            );
        }
        return view(
            verification.status(),
            verification.valid(),
            instanceId,
            payload,
            onlineDecision == null ? null : onlineDecision.view(),
            verification.message()
        );
    }

    /** 统一组装状态，确保无 payload 时不泄露或制造伪造授权字段。 */
    private LicenseStatusView view(
        LicenseStatusCode status,
        boolean valid,
        String instanceId,
        LicensePayload payload,
        LicenseOnlineStatusView online,
        String message
    ) {
        return new LicenseStatusView(
            status,
            valid,
            !enforcementEnabled || valid,
            enforcementEnabled,
            instanceId,
            payload == null ? null : payload.licenseId(),
            payload == null ? null : payload.edition(),
            payload == null ? null : payload.customer(),
            payload == null ? null : payload.issuedAt(),
            payload == null ? null : payload.expiresAt(),
            payload == null ? List.of() : payload.features(),
            payload == null ? List.of() : payload.entitlements(),
            online,
            portalUrl,
            message
        );
    }

    /**
     * 授权中心入口只接受 HTTPS；本机开发联调允许 127.0.0.1/localhost 的 HTTP。
     *
     * <p>拒绝 userInfo 可防止把访问令牌写进 URL；拒绝 fragment 不影响正常路由，
     * 同时避免配置里藏入浏览器端状态。无效配置只隐藏入口，不影响本地授权验签。</p>
     */
    private String normalizePortalUrl(String value) {
        String candidate = value == null ? "" : value.trim();
        if (candidate.isEmpty()) return null;
        try {
            URI uri = URI.create(candidate);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                return null;
            }
            if ("https".equalsIgnoreCase(uri.getScheme())) return uri.toString();
            if ("http".equalsIgnoreCase(uri.getScheme())
                && ("127.0.0.1".equals(uri.getHost())
                    || "localhost".equalsIgnoreCase(uri.getHost()))) {
                return uri.toString();
            }
        } catch (IllegalArgumentException ignored) {
            // 非法地址只隐藏跳转按钮；授权本身仍由本地公钥和签名租约正常校验。
        }
        return null;
    }
}
