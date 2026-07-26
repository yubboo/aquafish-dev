package com.aquafish.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 工作目录边界测试。
 *
 * <p>相对路径必须跟随当前进程启动目录，不能因为父目录恰好包含
 * Aquafish 源码结构就越界读取另一个实例的运行数据。</p>
 */
class AquafishPathResolverTest {

    @TempDir
    Path tempDir;

    /**
     * 验证默认 workdir 严格相对当前进程目录解析。
     */
    @Test
    void resolvesRelativeWorkDirAgainstCurrentWorkingDirectory() {
        Path expected = Path.of("")
            .toAbsolutePath()
            .normalize()
            .resolve("workdir")
            .normalize();

        assertEquals(
            expected,
            AquafishPathResolver.resolveWorkDirPath("workdir")
        );
    }

    /**
     * 验证部署平台传入的绝对工作目录不会被再次改写。
     */
    @Test
    void preservesAbsoluteWorkDir() {
        Path expected = tempDir
            .resolve("managed-workdir")
            .toAbsolutePath()
            .normalize();

        assertEquals(
            expected,
            AquafishPathResolver.resolveWorkDirPath(expected.toString())
        );
    }
}
