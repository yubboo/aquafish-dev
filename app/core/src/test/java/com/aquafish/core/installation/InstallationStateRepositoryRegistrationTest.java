package com.aquafish.core.installation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.core.installation.r2dbc.R2dbcInstallationStateStore;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Repository;

/**
 * 安装状态仓库 Spring 注册边界测试。
 */
class InstallationStateRepositoryRegistrationTest {

    @Test
    void shouldRegisterOnlyReactiveRepository() {
        assertTrue(
            AnnotatedElementUtils.hasAnnotation(
                R2dbcInstallationStateStore.class,
                Repository.class
            )
        );

    }
}
