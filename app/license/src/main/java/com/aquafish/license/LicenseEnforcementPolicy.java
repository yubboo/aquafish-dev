package com.aquafish.license;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * 决定当前进程能否关闭系统平台授权强制校验。
 *
 * <p>信任边界：普通外置配置可以为开发构建关闭授权拦截，但不能改变正式发行物的
 * 身份。正式 {@code bootJar} 会内嵌发行标记；同时 {@code prod}、{@code release}
 * 和 {@code formal} Profile 也被视为正式运行环境。任一正式条件成立时，如果配置
 * 请求关闭 enforcement，应用必须在启动阶段失败，不能静默降级成全部放行。</p>
 *
 * <p>本类只约束授权强制开关，不负责公钥信任链、许可证验签或模块权限判断。</p>
 */
@Component
public final class LicenseEnforcementPolicy {

    static final String RELEASE_MARKER =
        "META-INF/aquafish-release.marker";

    private static final Profiles FORMAL_PROFILES = Profiles.of(
        "prod",
        "release",
        "formal"
    );

    private final boolean enforcementEnabled;
    private final boolean releaseBuild;
    private final boolean formalProfile;

    @Autowired
    public LicenseEnforcementPolicy(
        Environment environment,
        @Value("${aquafish.license.enforcement-enabled:true}")
        boolean configuredEnforcement
    ) {
        this(
            configuredEnforcement,
            environment.acceptsProfiles(FORMAL_PROFILES),
            releaseMarkerPresent()
        );
    }

    /**
     * 测试构造显式传入正式 Profile 与发行标记，避免测试依赖真实打包顺序。
     */
    LicenseEnforcementPolicy(
        boolean configuredEnforcement,
        boolean formalProfile,
        boolean releaseBuild
    ) {
        this.formalProfile = formalProfile;
        this.releaseBuild = releaseBuild;
        boolean formalRuntime = formalProfile || releaseBuild;
        if (formalRuntime && !configuredEnforcement) {
            throw new IllegalStateException(
                "正式或发行环境禁止关闭 Aquafish 授权强制校验，"
                    + "请移除 aquafish.license.enforcement-enabled=false "
                    + "或 AQUAFISH_LICENSE_ENFORCEMENT_ENABLED=false。"
            );
        }
        this.enforcementEnabled = configuredEnforcement;
    }

    /** 返回经过正式运行边界校验后的有效 enforcement 状态。 */
    public boolean enforcementEnabled() {
        return enforcementEnabled;
    }

    /** 返回当前类路径是否来自带内嵌发行标记的正式构建。 */
    public boolean releaseBuild() {
        return releaseBuild;
    }

    /** 返回是否激活了 prod、release 或 formal Profile。 */
    public boolean formalProfile() {
        return formalProfile;
    }

    private static boolean releaseMarkerPresent() {
        ClassLoader loader = LicenseEnforcementPolicy.class.getClassLoader();
        return loader != null && loader.getResource(RELEASE_MARKER) != null;
    }
}
