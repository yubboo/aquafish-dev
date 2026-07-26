package com.aquafish.theme.install;

import com.aquafish.core.config.WorkDirResolver;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 安装并受控升级随 JAR 发布的官方 default 主题。
 *
 * <p>default 是系统管理的安全回退主题。发行版本更新时先把旧副本移动到备份，
 * 再用经过结构校验的 staging 原子替换；站点个性化应使用 settings 或独立主题。</p>
 */
@Component
public class BuiltinDefaultThemeProvisioner implements SmartInitializingSingleton {

    private static final String RESOURCE =
        "aquafish/builtin-themes/aquafish-default.zip";

    private final WorkDirResolver workDirResolver;

    public BuiltinDefaultThemeProvisioner(WorkDirResolver workDirResolver) {
        this.workDirResolver = workDirResolver;
    }

    @Override
    public void afterSingletonsInstantiated() {
        workDirResolver.ensureBaseDirectories();
        Path themesDir = workDirResolver.themesDir().toAbsolutePath().normalize();
        Path target = themesDir.resolve("default").normalize();
        if (Files.exists(target) && !Files.isRegularFile(target.resolve("theme.yaml"))) {
            throw new IllegalStateException(
                "默认主题目录已存在但缺少 theme.yaml，请先修复或移走该目录：" + target
            );
        }

        ClassPathResource resource = new ClassPathResource(RESOURCE);
        if (!resource.exists()) {
            throw new IllegalStateException("发行包缺少内置默认主题资源：" + RESOURCE);
        }

        Path staging = workDirResolver.tempDir()
            .resolve("builtin-default-" + UUID.randomUUID())
            .toAbsolutePath()
            .normalize();
        requireInside(staging, workDirResolver.tempDir(), "默认主题临时目录越界。");

        Path backup = null;
        try {
            Files.createDirectories(staging);
            try (InputStream input = resource.getInputStream()) {
                extract(input, staging);
            }
            requireThemePackage(staging);

            if (Files.isRegularFile(target.resolve("theme.yaml"))) {
                String installedVersion = themeVersion(target.resolve("theme.yaml"));
                String bundledVersion = themeVersion(staging.resolve("theme.yaml"));
                if (compareVersions(installedVersion, bundledVersion) >= 0) {
                    deleteTree(staging, workDirResolver.tempDir());
                    return;
                }
                backup = backupPath(installedVersion);
                Files.createDirectories(backup.getParent());
                move(target, backup);
            }

            try {
                move(staging, target);
            } catch (IOException | RuntimeException installError) {
                if (backup != null && Files.exists(backup) && !Files.exists(target)) {
                    move(backup, target);
                }
                throw installError;
            }
        } catch (IOException error) {
            deleteTree(staging, workDirResolver.tempDir());
            throw new IllegalStateException("安装或升级内置默认主题失败。", error);
        } catch (RuntimeException error) {
            deleteTree(staging, workDirResolver.tempDir());
            throw error;
        }
    }

    /** 逐条目校验规范路径，禁止 ZIP Slip 和写出暂存目录。 */
    private void extract(InputStream input, Path staging) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                Path output = staging.resolve(name).normalize();
                if (name.isBlank() || name.startsWith("/") || !output.startsWith(staging)) {
                    throw new IllegalStateException("内置主题包含非法 ZIP 路径：" + name);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    Files.copy(zip, output, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }

    private void requireThemePackage(Path staging) {
        for (String required : List.of(
            "theme.yaml",
            "templates/index.html",
            "templates/member/login.html",
            "templates/member/register.html"
        )) {
            if (!Files.isRegularFile(staging.resolve(required))) {
                throw new IllegalStateException("内置默认主题包缺少必要文件：" + required);
            }
        }
        if (!Files.isDirectory(staging.resolve("assets"))) {
            throw new IllegalStateException("内置默认主题包缺少 assets 目录。");
        }
    }

    private String themeVersion(Path manifest) throws IOException {
        return Files.readAllLines(manifest).stream()
            .map(String::trim)
            .filter(line -> line.startsWith("version:"))
            .map(line -> line.substring("version:".length()).trim().replace("\"", ""))
            .filter(value -> !value.isBlank())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("默认主题清单缺少 version。"));
    }

    static int compareVersions(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < length; index++) {
            int leftValue = versionPart(leftParts, index);
            int rightValue = versionPart(rightParts, index);
            int compared = Integer.compare(leftValue, rightValue);
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    private static int versionPart(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        String digits = parts[index].replaceFirst("[^0-9].*$", "");
        if (digits.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException error) {
            throw new IllegalStateException("默认主题版本格式无效。", error);
        }
    }

    private Path backupPath(String installedVersion) {
        Path root = workDirResolver.backupsDir()
            .resolve("themes/builtin-default-upgrades")
            .toAbsolutePath()
            .normalize();
        Path backup = root.resolve(
            "default-" + installedVersion.replaceAll("[^A-Za-z0-9._-]", "_")
                + "-" + UUID.randomUUID()
        ).normalize();
        requireInside(backup, root, "默认主题备份目录越界。");
        return backup;
    }

    private void requireInside(Path path, Path root, String message) {
        if (!path.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize())) {
            throw new IllegalStateException(message);
        }
    }

    private void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target);
        }
    }

    private void deleteTree(Path path, Path allowedRoot) {
        if (!Files.exists(path)) {
            return;
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(allowedRoot.toAbsolutePath().normalize())) {
            return;
        }
        try (var paths = Files.walk(normalized)) {
            paths.sorted(Comparator.reverseOrder()).forEach(current -> {
                try {
                    Files.deleteIfExists(current);
                } catch (IOException ignored) {
                    // 原始安装异常优先返回，残留暂存目录交给运维诊断。
                }
            });
        } catch (IOException ignored) {
            // 同上：不覆盖原始异常。
        }
    }
}
