package com.aquafish.license;

import com.aquafish.core.config.WorkDirResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 系统平台授权码的本地持久化边界。
 *
 * <p>授权码保存在 {@code workdir/licenses/platform.license}，不写入源码目录，也不进入
 * 生产发行包。保存过程采用临时文件加原子替换，避免断电或进程中断留下半截授权码。</p>
 */
@Component
public final class LicenseFileStore {

    static final int MAX_LICENSE_CODE_LENGTH = 32_768;

    private final Path licenseFile;

    @Autowired
    public LicenseFileStore(WorkDirResolver workDirResolver) {
        this(workDirResolver.licensesDir().resolve("platform.license"));
    }

    LicenseFileStore(Path licenseFile) {
        this.licenseFile = licenseFile.toAbsolutePath().normalize();
    }

    /** 读取并限制授权码大小；文件不存在或为空表示尚未激活。 */
    public synchronized Optional<String> read() {
        if (!Files.isRegularFile(licenseFile)) {
            return Optional.empty();
        }

        try {
            String value = Files.readString(licenseFile, StandardCharsets.UTF_8).trim();
            if (value.isEmpty()) {
                return Optional.empty();
            }
            if (value.length() > MAX_LICENSE_CODE_LENGTH) {
                throw new IllegalStateException("本地授权文件超过允许大小，可能已损坏。");
            }
            return Optional.of(value);
        } catch (IOException error) {
            throw new IllegalStateException("读取本地授权文件失败。", error);
        }
    }

    /**
     * 先写同目录临时文件、限制权限，再原子替换正式文件，避免留下半截授权码。
     */
    public synchronized void save(String licenseCode) {
        String safeCode = licenseCode == null ? "" : licenseCode.trim();
        if (safeCode.isEmpty() || safeCode.length() > MAX_LICENSE_CODE_LENGTH) {
            throw new IllegalArgumentException("授权码为空或超过允许大小。");
        }

        Path parent = licenseFile.getParent();
        Path temporary = licenseFile.resolveSibling(licenseFile.getFileName() + ".tmp");

        try {
            Files.createDirectories(parent);
            Files.writeString(temporary, safeCode + System.lineSeparator(), StandardCharsets.UTF_8);
            restrictPermissionsWhenSupported(temporary);
            moveAtomically(temporary, licenseFile);
            restrictPermissionsWhenSupported(licenseFile);
        } catch (IOException error) {
            throw new IllegalStateException("保存本地授权文件失败，请检查 workdir 写入权限。", error);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // 临时文件不包含额外秘密，下一次保存会覆盖同一路径。
            }
        }
    }

    /** 删除本地授权文件；不存在时按幂等成功处理。 */
    public synchronized void delete() {
        try {
            Files.deleteIfExists(licenseFile);
        } catch (IOException error) {
            throw new IllegalStateException("取消激活失败，无法删除本地授权文件。", error);
        }
    }

    /** 在支持 POSIX 权限的平台把文件限制为仅所有者可读写。 */
    private void restrictPermissionsWhenSupported(Path path) {
        try {
            Files.setPosixFilePermissions(
                path,
                Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            );
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows 不支持 POSIX 权限；继续依赖 workdir 所在目录的 ACL。
        }
    }

    /** 优先原子移动；文件系统不支持时回退为同路径替换。 */
    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(
                source,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
