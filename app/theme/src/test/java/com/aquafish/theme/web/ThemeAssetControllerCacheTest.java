package com.aquafish.theme.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * 主题静态资源缓存与 MIME 回归测试。
 *
 * <p>图片/字体使用较长浏览器缓存，CSS 使用短缓存；Windows 无法探测 WebP/Woff2
 * 类型时也必须返回可由浏览器直接渲染的媒体类型。</p>
 */
class ThemeAssetControllerCacheTest {

    @Test
    void shouldCacheVersionedImageForThirtyDays() {
        assertEquals(
            "max-age=2592000, public",
            ThemeAssetController.cacheControlFor(
                "images/aquafish-keeper-v1.webp"
            ).getHeaderValue()
        );
    }

    @Test
    void shouldKeepStylesheetCacheShort() {
        assertEquals(
            "max-age=300, public",
            ThemeAssetController.cacheControlFor("css/style.css").getHeaderValue()
        );
    }

    @Test
    void shouldResolveWebpAndWoff2MimeWithoutOperatingSystemMapping() {
        assertEquals(
            MediaType.parseMediaType("image/webp"),
            ThemeAssetController.mediaTypeFor(Path.of("hero.webp"), null)
        );
        assertEquals(
            MediaType.parseMediaType("font/woff2"),
            ThemeAssetController.mediaTypeFor(Path.of("title.woff2"), null)
        );
    }
}
