package com.aquafish.admin.web;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

class DevelopmentOnlyAdminControllerProfileTest {

    @Test
    void schemaAndDiagnosticsControllersRequireDevProfile() {
        assertDevOnly(AdminR2dbcDiagnosticsController.class);
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
