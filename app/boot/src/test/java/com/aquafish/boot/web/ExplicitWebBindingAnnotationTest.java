package com.aquafish.boot.web;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.aquafish.admin.web.AdminWorkspaceController;
import com.aquafish.content.web.AdminContentArticleController;
import com.aquafish.content.web.PublicContentController;
import com.aquafish.forum.web.AdminForumSectionController;
import com.aquafish.forum.web.ForumThreadController;
import com.aquafish.forum.web.PublicForumController;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * WebFlux 请求参数显式命名契约。
 *
 * <p>生产构建同时启用 {@code -parameters}，但控制器仍必须显式写明 URL 和查询
 * 参数名称。这样即使构建工具、IDE 或发行方式改变，也不会在真实请求进入时才以
 * HTTP 500 暴露参数名缺失问题。</p>
 */
class ExplicitWebBindingAnnotationTest {

    private static final List<Class<?>> CONTROLLERS = List.of(
        AdminWorkspaceController.class,
        AdminContentArticleController.class,
        PublicContentController.class,
        AdminForumSectionController.class,
        PublicForumController.class,
        ForumThreadController.class
    );

    @Test
    void shouldNameEveryPathVariableAndRequestParameterExplicitly() {
        for (Class<?> controller : CONTROLLERS) {
            for (Method method : controller.getDeclaredMethods()) {
                inspect(controller, method);
            }
        }
    }

    private void inspect(Class<?> controller, Method method) {
        for (Parameter parameter : method.getParameters()) {
            for (Annotation annotation : parameter.getAnnotations()) {
                if (annotation instanceof PathVariable pathVariable) {
                    assertFalse(
                        name(pathVariable.name(), pathVariable.value()).isBlank(),
                        () -> location(controller, method, parameter)
                            + " 的 @PathVariable 必须显式命名"
                    );
                }
                if (annotation instanceof RequestParam requestParam) {
                    assertFalse(
                        name(requestParam.name(), requestParam.value()).isBlank(),
                        () -> location(controller, method, parameter)
                            + " 的 @RequestParam 必须显式命名"
                    );
                }
            }
        }
    }

    private String name(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private String location(
        Class<?> controller,
        Method method,
        Parameter parameter
    ) {
        return controller.getSimpleName() + "#" + method.getName()
            + "(" + parameter.getName() + ")";
    }
}
