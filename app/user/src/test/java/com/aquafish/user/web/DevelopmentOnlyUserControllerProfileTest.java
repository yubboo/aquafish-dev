package com.aquafish.user.web;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

class DevelopmentOnlyUserControllerProfileTest {

    @Test
    void diagnosticsControllersRequireDevProfile() {
        assertDevOnly(AdminRequestIpController.class);
        assertDevOnly(AdminTableNameTestController.class);
    }

    private void assertDevOnly(Class<?> controllerType) {
        Profile profile = controllerType.getAnnotation(Profile.class);

        assertNotNull(profile, controllerType.getSimpleName() + " 必须声明 @Profile(\"dev\")");
        assertTrue(
            Arrays.asList(profile.value()).contains("dev"),
            controllerType.getSimpleName() + " 只能在 dev Profile 注册"
        );
    }
}
