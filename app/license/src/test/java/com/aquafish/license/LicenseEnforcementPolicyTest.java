package com.aquafish.license;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 授权强制开关的配置绑定、Profile 和发行构建负向测试。
 */
class LicenseEnforcementPolicyTest {

    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withUserConfiguration(PolicyConfiguration.class);

    @Test
    void defaultsToEnforcementEnabled() {
        contextRunner.run(context -> assertTrue(
            context.getBean(LicenseEnforcementPolicy.class)
                .enforcementEnabled()
        ));
    }

    @Test
    void developmentProfileMayDisableEnforcement() {
        contextRunner
            .withInitializer(context ->
                context.getEnvironment().setActiveProfiles("dev"))
            .withPropertyValues(
                "aquafish.license.enforcement-enabled=false"
            )
            .run(context -> assertFalse(
                context.getBean(LicenseEnforcementPolicy.class)
                    .enforcementEnabled()
            ));
    }

    @Test
    void prodProfileMustRejectDisabledEnforcementDuringBinding() {
        contextRunner
            .withInitializer(context ->
                context.getEnvironment().setActiveProfiles("prod"))
            .withPropertyValues(
                "aquafish.license.enforcement-enabled=false"
            )
            .run(context -> {
                Throwable failure = context.getStartupFailure();
                assertNotNull(failure);
                Throwable rootCause = rootCause(failure);
                assertInstanceOf(IllegalStateException.class, rootCause);
                assertTrue(rootCause.getMessage().contains(
                    "正式或发行环境禁止关闭 Aquafish 授权强制校验"
                ));
            });
    }

    @Test
    void releaseMarkerMustRejectDisabledEnforcementWithoutFormalProfile() {
        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> new LicenseEnforcementPolicy(false, false, true)
        );
        assertTrue(error.getMessage().contains(
            "正式或发行环境禁止关闭 Aquafish 授权强制校验"
        ));
    }

    @Test
    void formalProfileMustRejectDisabledEnforcementWithoutReleaseMarker() {
        assertThrows(
            IllegalStateException.class,
            () -> new LicenseEnforcementPolicy(false, true, false)
        );
    }

    @Test
    void formalReleaseMayStartOnlyWhenEnforcementRemainsEnabled() {
        LicenseEnforcementPolicy policy =
            new LicenseEnforcementPolicy(true, true, true);

        assertTrue(policy.enforcementEnabled());
        assertTrue(policy.formalProfile());
        assertTrue(policy.releaseBuild());
    }

    @Configuration(proxyBeanMethods = false)
    @Import(LicenseEnforcementPolicy.class)
    static class PolicyConfiguration {
    }

    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
