package com.aquafish.theme.web;

import com.aquafish.theme.core.ActiveThemeResolver;
import com.aquafish.theme.core.DefaultThemeResolver;
import com.aquafish.theme.core.ThemeDescriptor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 当前主题静态资源安全出口。
 *
 * <p>资源路径始终在当前主题 assets 目录内解析，拒绝目录穿越、目录下载和不存在文件。
 * CSS/JS 保持短缓存以便主题设置及时生效；带版本号的图片与字体使用 30 天浏览器缓存，
 * 并统一返回 Last-Modified 供浏览器本地再验证。</p>
 */
@RestController
public class ThemeAssetController {

    private final ActiveThemeResolver activeThemeResolver;
    private final DefaultThemeResolver defaultThemeResolver;

    public ThemeAssetController(
        ActiveThemeResolver activeThemeResolver,
        DefaultThemeResolver defaultThemeResolver
    ) {
        this.activeThemeResolver = activeThemeResolver;
        this.defaultThemeResolver = defaultThemeResolver;
    }

    @GetMapping("/theme-assets/{*assetPath}")
    public Mono<ResponseEntity<Resource>> asset(
        @PathVariable("assetPath") String assetPath
    ) {
        return Mono.fromCallable(() -> resolve(assetPath))
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorReturn(ResponseEntity.notFound().build());
    }

    private ResponseEntity<Resource> resolve(String assetPath) throws IOException {
        ThemeDescriptor theme = activeThemeResolver.activeTheme()
            .or(defaultThemeResolver::defaultTheme)
            .orElseThrow(() -> new IllegalStateException("没有可用主题。"));
        Path base = Path.of(theme.assetsDir()).toAbsolutePath().normalize();
        String relative = assetPath == null ? "" : assetPath.replace('\\', '/');
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        Path target = base.resolve(relative).normalize();
        if (relative.isBlank()
            || !target.startsWith(base)
            || !Files.isRegularFile(target)) {
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType = mediaTypeFor(target, Files.probeContentType(target));

        return ResponseEntity.status(HttpStatus.OK)
            .contentType(mediaType)
            .contentLength(Files.size(target))
            .lastModified(Files.getLastModifiedTime(target).toMillis())
            .cacheControl(cacheControlFor(relative))
            .body(new FileSystemResource(target));
    }

    /**
     * 图片和字体文件由主题版本化文件名承载变更，适合使用较长的浏览器本地缓存。
     * HTML 页面本身仍由业务控制器 no-store，不会被这里的规则影响。
     */
    static CacheControl cacheControlFor(String assetPath) {
        String extension = extension(assetPath);
        if (extension.equals("webp")
            || extension.equals("avif")
            || extension.equals("png")
            || extension.equals("jpg")
            || extension.equals("jpeg")
            || extension.equals("gif")
            || extension.equals("svg")
            || extension.equals("woff")
            || extension.equals("woff2")
            || extension.equals("ttf")
            || extension.equals("otf")) {
            return CacheControl.maxAge(Duration.ofDays(30)).cachePublic();
        }
        return CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();
    }

    /**
     * Windows 未注册 WebP/Woff2 MIME 时使用扩展名兜底，避免返回下载流类型。
     */
    static MediaType mediaTypeFor(Path target, String probed) {
        String extension = extension(target == null ? "" : target.getFileName().toString());
        if (extension.equals("webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        if (extension.equals("avif")) {
            return MediaType.parseMediaType("image/avif");
        }
        if (extension.equals("woff2")) {
            return MediaType.parseMediaType("font/woff2");
        }
        try {
            return probed == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(probed);
        } catch (IllegalArgumentException ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static String extension(String path) {
        String normalized = path == null ? "" : path.toLowerCase(Locale.ROOT);
        int dot = normalized.lastIndexOf('.');
        return dot < 0 ? "" : normalized.substring(dot + 1);
    }
}
