package com.aquafish.theme.core;

import com.aquafish.core.config.WorkDirResolver;
import com.aquafish.theme.manifest.ThemeManifest;
import com.aquafish.theme.manifest.ThemeManifestException;
import com.aquafish.theme.manifest.ThemeManifestParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Aquafish 已安装主题扫描器。
 *
 * <p>当前处理流程：</p>
 *
 * <pre>
 * workdir/themes
 * -> 一级主题目录
 * -> theme.yaml
 * -> ThemeManifestParser
 * -> ThemeManifest
 * -> ThemeDescriptor
 * </pre>
 *
 * <p>
 * ThemeManifest 只保存 theme.yaml 中的可移植清单数据。
 * ThemeDescriptor 在此基础上继续补充当前服务器上的
 * 主题目录、模板目录、静态资源目录和文件存在状态。
 * </p>
 */
@Component
public class ThemeScanner {

    /**
     * 工作目录解析器。
     */
    private final WorkDirResolver
        workDirResolver;

    /**
     * 正式主题清单解析器。
     */
    private final ThemeManifestParser
        themeManifestParser;

    /**
     * Spring 生产构造方法。
     *
     * @param workDirResolver 工作目录解析器
     * @param themeManifestParser 主题清单解析器
     */
    @Autowired
    public ThemeScanner(
        WorkDirResolver workDirResolver,
        ThemeManifestParser themeManifestParser
    ) {
        if (workDirResolver == null) {
            throw new IllegalArgumentException(
                "工作目录解析器不能为空。"
            );
        }

        if (themeManifestParser == null) {
            throw new IllegalArgumentException(
                "主题清单解析器不能为空。"
            );
        }

        this.workDirResolver =
            workDirResolver;

        this.themeManifestParser =
            themeManifestParser;
    }

    /**
     * 兼容早期测试和手动装配代码的构造方法。
     *
     * <p>
     * 新生产代码应交给 Spring 注入两个依赖。
     * </p>
     *
     * @param workDirResolver 工作目录解析器
     */
    public ThemeScanner(
        WorkDirResolver workDirResolver
    ) {
        this(
            workDirResolver,
            new ThemeManifestParser()
        );
    }

    /**
     * 扫描当前实例已经安装的全部主题。
     *
     * @return 按主题唯一标识排序的主题列表
     */
    public List<ThemeDescriptor>
        scanInstalledThemes() {

        workDirResolver
            .ensureBaseDirectories();

        Path themesDir =
            workDirResolver.themesDir();

        if (!Files.isDirectory(themesDir)) {
            return List.of();
        }

        try (
            Stream<Path> stream =
                Files.list(themesDir)
        ) {
            return stream
                .filter(Files::isDirectory)
                .filter(this::hasThemeYaml)
                .map(this::readTheme)
                .sorted(
                    Comparator.comparing(
                        ThemeDescriptor::name
                    )
                )
                .toList();
        } catch (IOException error) {
            throw new IllegalStateException(
                "扫描主题目录失败："
                    + themesDir,
                error
            );
        }
    }

    /**
     * 判断目录是否包含 theme.yaml。
     *
     * @param themeDir 主题目录
     * @return 存在时返回 true
     */
    private boolean hasThemeYaml(
        Path themeDir
    ) {
        return Files.isRegularFile(
            themeDir.resolve(
                "theme.yaml"
            )
        );
    }

    /**
     * 读取一个已安装主题。
     *
     * @param themeDir 主题根目录
     * @return 运行时主题描述
     */
    private ThemeDescriptor readTheme(
        Path themeDir
    ) {
        Path normalizedThemeDir =
            themeDir
                .toAbsolutePath()
                .normalize();

        Path themeYamlFile =
            normalizedThemeDir.resolve(
                "theme.yaml"
            );

        Path settingsYamlFile =
            normalizedThemeDir.resolve(
                "settings.yaml"
            );

        Path templatesDir =
            normalizedThemeDir.resolve(
                "templates"
            );

        Path assetsDir =
            normalizedThemeDir.resolve(
                "assets"
            );

        ThemeManifest manifest =
            parseInstalledThemeManifest(
                themeYamlFile
            );

        return new ThemeDescriptor(
            manifest.id(),
            manifest.title(),
            manifest.version(),
            manifest.engine(),
            manifest.author().name(),
            manifest.parent(),
            manifest.description(),
            normalizedThemeDir.toString(),
            themeYamlFile.toString(),
            settingsYamlFile.toString(),
            templatesDir.toString(),
            assetsDir.toString(),
            Files.isRegularFile(
                settingsYamlFile
            ),
            Files.isDirectory(
                templatesDir
            ),
            Files.isDirectory(
                assetsDir
            )
        );
    }

    /**
     * 解析已经安装主题的 theme.yaml。
     *
     * <p>
     * ThemeManifestParser 对外使用统一的
     * ThemeManifestException 表示 YAML 读取、语法和字段错误。
     * </p>
     *
     * <p>
     * 但 ThemeScanner 在第 41 步之前已经形成稳定契约：
     * 当主题名称、模板引擎等清单字段不合法时，
     * 扫描流程直接抛出 IllegalArgumentException。
     * </p>
     *
     * <p>
     * 为保持已有调用方和自动化测试兼容，
     * 扫描器会查找异常链中的字段校验异常并重新抛出。
     * YAML 语法错误、文件读取失败等其他问题，
     * 仍保留 ThemeManifestException。
     * </p>
     *
     * @param themeYamlFile theme.yaml 文件
     * @return 解析成功的主题清单
     */
    private ThemeManifest parseInstalledThemeManifest(
        Path themeYamlFile
    ) {
        try {
            return themeManifestParser.parse(
                themeYamlFile
            );
        } catch (ThemeManifestException error) {
            IllegalArgumentException
                fieldValidationError =
                    findFieldValidationError(
                        error
                    );

            if (fieldValidationError != null) {
                throw fieldValidationError;
            }

            throw error;
        }
    }

    /**
     * 在异常链中查找主题字段校验异常。
     *
     * <p>
     * Jackson YAML 语法错误不会被转换为
     * IllegalArgumentException。
     * 只有 ThemeManifest 构造阶段产生的字段校验异常
     * 才会恢复为扫描器原有异常类型。
     * </p>
     *
     * @param error 起始异常
     * @return 找到的字段校验异常；不存在时返回 null
     */
    private IllegalArgumentException
        findFieldValidationError(
            Throwable error
        ) {

        Throwable current = error;

        while (current != null) {
            if (
                current
                    instanceof IllegalArgumentException
                        validationError
            ) {
                return validationError;
            }

            current = current.getCause();
        }

        return null;
    }

}
