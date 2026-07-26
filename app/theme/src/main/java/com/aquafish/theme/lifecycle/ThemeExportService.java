package com.aquafish.theme.lifecycle;

import com.aquafish.core.config.WorkDirResolver;
import com.aquafish.core.operation.ExtensionOperationCoordinator;
import com.aquafish.core.operation.ExtensionOperationHandle;
import com.aquafish.core.operation.ExtensionOperationKeys;
import com.aquafish.theme.core.ThemeDescriptor;
import com.aquafish.theme.core.ThemeScanner;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;

/**
 * 把一个已安装主题导出为可重新安装的标准 ZIP。
 *
 * <p>ZIP 根目录直接包含 theme.yaml、settings.yaml、templates 和 assets，
 * 与安装器输入结构一致，不额外包一层主题目录。导出只包含主题包文件，不包含
 * workdir/settings/themes 中的站点私有设置。</p>
 */
@Service
public class ThemeExportService {

    private static final int MAX_FILES = 20_000;
    private static final long MAX_UNCOMPRESSED_BYTES = 64L * 1024L * 1024L;

    private final ThemeScanner themeScanner;
    private final WorkDirResolver workDirResolver;
    private final ExtensionOperationCoordinator operationCoordinator;

    public ThemeExportService(
        ThemeScanner themeScanner,
        WorkDirResolver workDirResolver,
        ExtensionOperationCoordinator operationCoordinator
    ) {
        this.themeScanner = themeScanner;
        this.workDirResolver = workDirResolver;
        this.operationCoordinator = operationCoordinator;
    }

    /**
     * 导出主题包。
     *
     * @param themeId 已安装主题 ID
     * @return 标准 ZIP 字节
     */
    public byte[] export(String themeId) {
        try (ExtensionOperationHandle ignored = acquireOperation()) {
            ThemeDescriptor theme = requireInstalled(themeId);
            Path themeDirectory = safeThemeDirectory(theme);
            List<Path> files = listRegularFiles(themeDirectory);

            try (
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(output)
            ) {
                long totalBytes = 0L;
                for (Path file : files) {
                    long fileBytes = Files.size(file);
                    totalBytes = Math.addExact(totalBytes, fileBytes);
                    if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                        throw new ThemeLifecycleException(
                            "THEME_EXPORT_TOO_LARGE",
                            "主题导出文件总大小不能超过 64 MiB。"
                        );
                    }

                    String entryName = themeDirectory.relativize(file)
                        .toString()
                        .replace('\\', '/');
                    ZipEntry entry = new ZipEntry(entryName);
                    entry.setTime(0L);
                    zip.putNextEntry(entry);
                    Files.copy(file, zip);
                    zip.closeEntry();
                }
                zip.finish();
                return output.toByteArray();
            } catch (ThemeLifecycleException error) {
                throw error;
            } catch (Exception error) {
                throw new ThemeLifecycleException(
                    "THEME_EXPORT_FAILED",
                    "主题导出失败。",
                    error
                );
            }
        }
    }

    private List<Path> listRegularFiles(Path themeDirectory) {
        try (var stream = Files.walk(themeDirectory)) {
            List<Path> files = stream
                .filter(path -> !path.equals(themeDirectory))
                .peek(path -> rejectSymbolicLink(themeDirectory, path))
                .filter(path -> Files.isRegularFile(
                    path,
                    LinkOption.NOFOLLOW_LINKS
                ))
                .sorted(Comparator.comparing(path ->
                    themeDirectory.relativize(path).toString()
                ))
                .toList();
            if (files.size() > MAX_FILES) {
                throw new ThemeLifecycleException(
                    "THEME_EXPORT_TOO_MANY_FILES",
                    "主题导出文件数量不能超过 " + MAX_FILES + "。"
                );
            }
            return files;
        } catch (ThemeLifecycleException error) {
            throw error;
        } catch (IOException error) {
            throw new ThemeLifecycleException(
                "THEME_EXPORT_FAILED",
                "读取主题目录失败。",
                error
            );
        }
    }

    private void rejectSymbolicLink(Path root, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || Files.isSymbolicLink(path)) {
            throw new ThemeLifecycleException(
                "THEME_EXPORT_UNSAFE_ENTRY",
                "主题包含不安全的符号链接，已停止导出。"
            );
        }
    }

    private ThemeDescriptor requireInstalled(String themeId) {
        String normalized = themeId == null
            ? ""
            : themeId.trim().toLowerCase(Locale.ROOT);
        return themeScanner.scanInstalledThemes().stream()
            .filter(theme -> theme.name().equals(normalized))
            .findFirst()
            .orElseThrow(() -> new ThemeLifecycleException(
                "THEME_NOT_FOUND",
                "没有找到已安装主题：" + normalized
            ));
    }

    private Path safeThemeDirectory(ThemeDescriptor theme) {
        Path themesRoot = workDirResolver.themesDir()
            .toAbsolutePath()
            .normalize();
        Path themeDirectory = Path.of(theme.themeDir())
            .toAbsolutePath()
            .normalize();
        if (
            !themeDirectory.startsWith(themesRoot)
                || themeDirectory.getParent() == null
                || !themeDirectory.getParent().equals(themesRoot)
                || Files.isSymbolicLink(themeDirectory)
                || !Files.isDirectory(themeDirectory, LinkOption.NOFOLLOW_LINKS)
        ) {
            throw new ThemeLifecycleException(
                "THEME_DIRECTORY_UNSAFE",
                "主题运行目录不安全，已停止导出。"
            );
        }
        return themeDirectory;
    }

    private ExtensionOperationHandle acquireOperation() {
        return operationCoordinator.tryAcquire(ExtensionOperationKeys.THEME_GLOBAL)
            .orElseThrow(() -> new ThemeLifecycleException(
                "THEME_OPERATION_BUSY",
                "当前有其他主题写操作正在执行，请稍后重试。"
            ));
    }
}
