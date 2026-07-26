package com.aquafish.license;

import com.aquafish.core.config.WorkDirResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 生成并持久化当前 Aquafish 站点的稳定实例 ID。
 *
 * <p>关联功能：后台授权页展示的设备码、授权码实例绑定、后续在线激活的设备记录。
 * 实例 ID 第一次读取时写入 {@code workdir/instance.id}，重启程序不会变化；迁移整套
 * workdir 时也会保留，避免正常备份恢复后许可证无故失效。</p>
 */
@Component
public final class LicenseInstanceIdentityService {

    private final Path instanceIdFile;

    @Autowired
    public LicenseInstanceIdentityService(WorkDirResolver workDirResolver) {
        this(workDirResolver.instanceIdFile());
    }

    LicenseInstanceIdentityService(Path instanceIdFile) {
        this.instanceIdFile = instanceIdFile.toAbsolutePath().normalize();
    }

    /**
     * 返回当前实例 ID；不存在时使用原子文件替换创建。
     */
    public synchronized String instanceId() {
        if (Files.isRegularFile(instanceIdFile)) {
            return readExistingId();
        }

        String created = UUID.randomUUID().toString();
        Path parent = instanceIdFile.getParent();
        Path temporary = instanceIdFile.resolveSibling(instanceIdFile.getFileName() + ".tmp");

        try {
            Files.createDirectories(parent);
            Files.writeString(temporary, created + System.lineSeparator(), StandardCharsets.UTF_8);
            moveAtomically(temporary, instanceIdFile);
            return created;
        } catch (IOException error) {
            throw new IllegalStateException(
                "无法创建 Aquafish 实例 ID，请检查 workdir 写入权限。",
                error
            );
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // 临时文件清理由下一次写入覆盖，不改变本次实例 ID 结果。
            }
        }
    }

    private String readExistingId() {
        try {
            String value = Files.readString(instanceIdFile, StandardCharsets.UTF_8).trim();
            UUID.fromString(value);
            return value;
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException(
                "workdir/instance.id 内容损坏，系统不会自动换号，以免现有授权失效。",
                error
            );
        } catch (IOException error) {
            throw new IllegalStateException("无法读取 Aquafish 实例 ID。", error);
        }
    }

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
