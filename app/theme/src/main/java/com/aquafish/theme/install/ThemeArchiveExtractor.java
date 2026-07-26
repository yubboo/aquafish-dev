package com.aquafish.theme.install;

import com.aquafish.core.config.WorkDirResolver;
import com.aquafish.theme.manifest.ThemeManifest;
import com.aquafish.theme.manifest.ThemeManifestException;
import com.aquafish.theme.manifest.ThemeManifestParser;
import com.aquafish.theme.validation.ThemePackageValidationResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 已通过安全校验的主题 ZIP 临时解压器。
 *
 * <p>当前处理流程：</p>
 *
 * <pre>
 * ThemePackageValidationResult
 * -> 校验 SHA-256
 * -> 创建 workdir/storage/temp/theme-install 临时目录
 * -> 逐 ZIP 条目二次安全检查
 * -> 去掉可选的单层 archiveRoot
 * -> 流式写入临时主题目录
 * -> 条目数量和总字节数一致性检查
 * -> theme.yaml 二次解析
 * -> 清单一致性检查
 * -> ThemeExtractionResult
 * </pre>
 *
 * <p>
 * 本组件不会把主题移动到 workdir/themes，
 * 也不会覆盖或启用任何主题。
 * </p>
 */
@Component
public class ThemeArchiveExtractor {

    /**
     * 文件流缓冲区。
     */
    private static final int
        BUFFER_SIZE = 8192;

    /**
     * SHA-256 格式。
     */
    private static final Pattern
        SHA256_PATTERN =
            Pattern.compile(
                "^[0-9a-f]{64}$"
            );

    /**
     * Unix 文件类型掩码。
     */
    private static final int
        UNIX_FILE_TYPE_MASK = 0170000;

    /**
     * Unix 普通文件。
     */
    private static final int
        UNIX_REGULAR_FILE = 0100000;

    /**
     * Unix 目录。
     */
    private static final int
        UNIX_DIRECTORY = 0040000;

    /**
     * Unix 符号链接。
     */
    private static final int
        UNIX_SYMBOLIC_LINK = 0120000;

    /**
     * 正式 workdir 解析器。
     */
    private final WorkDirResolver
        workDirResolver;

    /**
     * 测试专用固定临时根目录。
     */
    private final Path fixedTempRoot;

    /**
     * theme.yaml 解析器。
     */
    private final ThemeManifestParser
        themeManifestParser;

    /**
     * 安装文件系统操作。
     */
    private final ThemeInstallFileOperations
        fileOperations;

    /**
     * Spring 生产构造方法。
     *
     * @param workDirResolver workdir 解析器
     * @param themeManifestParser 清单解析器
     * @param fileOperations 文件系统操作
     */
    @Autowired
    public ThemeArchiveExtractor(
        WorkDirResolver workDirResolver,
        ThemeManifestParser themeManifestParser,
        ThemeInstallFileOperations fileOperations
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

        if (fileOperations == null) {
            throw new IllegalArgumentException(
                "主题安装文件操作不能为空。"
            );
        }

        this.workDirResolver =
            workDirResolver;

        this.fixedTempRoot = null;

        this.themeManifestParser =
            themeManifestParser;

        this.fileOperations =
            fileOperations;
    }

    /**
     * 测试专用构造方法。
     *
     * @param fixedTempRoot 固定临时根目录
     */
    ThemeArchiveExtractor(
        Path fixedTempRoot
    ) {
        if (fixedTempRoot == null) {
            throw new IllegalArgumentException(
                "测试临时目录不能为空。"
            );
        }

        this.workDirResolver = null;

        this.fixedTempRoot =
            fixedTempRoot
                .toAbsolutePath()
                .normalize();

        this.themeManifestParser =
            new ThemeManifestParser();

        this.fileOperations =
            new ThemeInstallFileOperations();
    }

    /**
     * 把已通过安全校验的 ZIP 解压到独立临时目录。
     *
     * @param packagePath 原始主题 ZIP
     * @param validationResult 安全校验结果
     * @return 临时解压成功结果
     */
    public ThemeExtractionResult extract(
        Path packagePath,
        ThemePackageValidationResult
            validationResult
    ) {
        Path normalizedPackagePath =
            validateBeforeExtraction(
                packagePath,
                validationResult
            );

        String expectedHash =
            normalizeHash(
                validationResult.sha256()
            );

        String beforeHash =
            calculateSha256(
                normalizedPackagePath,
                ThemeInstallStage.VALIDATION
            );

        if (!expectedHash.equals(beforeHash)) {
            throw new ThemeArchiveExtractionException(
                ThemeInstallErrorCode
                    .PACKAGE_CHANGED_AFTER_VALIDATION,
                ThemeInstallStage.VALIDATION,
                "主题包在安全校验完成后已经发生变化。",
                true
            );
        }

        Path workspaceDirectory = null;

        try {
            workspaceDirectory =
                createWorkspace(
                    validationResult
                        .manifest()
                        .id()
                );

            Path extractedThemeDirectory =
                workspaceDirectory
                    .resolve("theme")
                    .normalize();

            ensureInsideWorkspace(
                workspaceDirectory,
                extractedThemeDirectory
            );

            Files.createDirectory(
                extractedThemeDirectory
            );

            ExtractionStatistics statistics =
                extractArchive(
                    normalizedPackagePath,
                    validationResult,
                    extractedThemeDirectory
                );

            verifyStatistics(
                statistics,
                validationResult
            );

            ThemeManifest extractedManifest =
                verifyExtractedManifest(
                    extractedThemeDirectory,
                    validationResult
                );

            verifyExtractedTree(
                extractedThemeDirectory
            );

            String afterHash =
                calculateSha256(
                    normalizedPackagePath,
                    ThemeInstallStage.POST_VALIDATION
                );

            if (
                !expectedHash.equals(afterHash)
                    || !beforeHash.equals(
                        afterHash
                    )
            ) {
                throw new ThemeArchiveExtractionException(
                    ThemeInstallErrorCode
                        .PACKAGE_CHANGED_AFTER_VALIDATION,
                    ThemeInstallStage.POST_VALIDATION,
                    "主题包在临时解压过程中发生变化。",
                    false
                );
            }

            return new ThemeExtractionResult(
                extractedManifest,
                workspaceDirectory.toString(),
                extractedThemeDirectory.toString(),
                statistics.entryCount(),
                statistics.fileCount(),
                statistics.totalBytes(),
                afterHash,
                !validationResult
                    .archiveRoot()
                    .isBlank(),
                validationResult
            );
        } catch (
            ThemeArchiveExtractionException
                error
        ) {
            throw cleanupAndRebuildException(
                workspaceDirectory,
                error
            );
        } catch (IOException error) {
            ThemeArchiveExtractionException
                wrappedError =
                    new ThemeArchiveExtractionException(
                        ThemeInstallErrorCode
                            .ARCHIVE_EXTRACTION_FAILED,
                        ThemeInstallStage.EXTRACTION,
                        "临时解压主题包失败："
                            + safeMessage(error),
                        error,
                        false
                    );

            throw cleanupAndRebuildException(
                workspaceDirectory,
                wrappedError
            );
        } catch (RuntimeException error) {
            ThemeArchiveExtractionException
                wrappedError =
                    new ThemeArchiveExtractionException(
                        ThemeInstallErrorCode
                            .UNEXPECTED_INSTALL_ERROR,
                        ThemeInstallStage.EXTRACTION,
                        "临时解压主题包发生非预期错误："
                            + safeMessage(error),
                        error,
                        false
                    );

            throw cleanupAndRebuildException(
                workspaceDirectory,
                wrappedError
            );
        }
    }

    /**
     * 验证解压前条件。
     */
    private Path validateBeforeExtraction(
        Path packagePath,
        ThemePackageValidationResult
            validationResult
    ) {
        if (packagePath == null) {
            throw new ThemeArchiveExtractionException(
                ThemeInstallErrorCode
                    .PACKAGE_VALIDATION_FAILED,
                ThemeInstallStage.VALIDATION,
                "主题包路径不能为空。",
                true
            );
        }

        if (
            validationResult == null
                || !validationResult.valid()
        ) {
            throw new ThemeArchiveExtractionException(
                ThemeInstallErrorCode
                    .PACKAGE_VALIDATION_FAILED,
                ThemeInstallStage.VALIDATION,
                "主题包没有通过安全校验。",
                true
            );
        }

        if (
            validationResult.manifest()
                == null
        ) {
            throw new ThemeArchiveExtractionException(
                ThemeInstallErrorCode
                    .MANIFEST_UNAVAILABLE,
                ThemeInstallStage.VALIDATION,
                "主题包校验结果中没有可用主题清单。",
                true
            );
        }

        String expectedHash =
            normalizeHash(
                validationResult.sha256()
            );

        if (
            !SHA256_PATTERN
                .matcher(expectedHash)
                .matches()
        ) {
            throw new ThemeArchiveExtractionException(
                ThemeInstallErrorCode
                    .PACKAGE_VALIDATION_FAILED,
                ThemeInstallStage.VALIDATION,
                "主题包校验结果缺少有效 SHA-256。",
                true
            );
        }

        Path normalizedPackagePath =
            packagePath
                .toAbsolutePath()
                .normalize();

        if (
            !Files.isRegularFile(
                normalizedPackagePath,
                LinkOption.NOFOLLOW_LINKS
            )
        ) {
            throw new ThemeArchiveExtractionException(
                ThemeInstallErrorCode
                    .ARCHIVE_OPEN_FAILED,
                ThemeInstallStage.VALIDATION,
                "主题 ZIP 不存在或不是普通文件："
                    + normalizedPackagePath,
                true
            );
        }

        return normalizedPackagePath;
    }

    /**
     * 创建独立临时工作目录。
     */
    private Path createWorkspace(
        String themeId
    ) {
        final Path tempRoot;

        try {
            tempRoot = resolveTempRoot();

            Files.createDirectories(tempRoot);
        } catch (IOException error) {
            throw new ThemeArchiveExtractionException(
                ThemeInstallErrorCode
                    .TEMP_DIRECTORY_CREATE_FAILED,
                ThemeInstallStage.PREPARATION,
                "创建主题安装临时根目录失败："
                    + safeMessage(error),
                error,
                true
            );
        }

        Path installTempRoot =
            tempRoot
                .resolve("theme-install")
                .normalize();

        if (
            !fileOperations.isWithin(
                tempRoot,
                installTempRoot
            )
        ) {
            throw new ThemeArchiveExtractionException(
                ThemeInstallErrorCode
                    .TARGET_PATH_INVALID,
                ThemeInstallStage.PREPARATION,
                "主题安装临时目录超出 workdir 临时根目录。",
                true
            );
        }

        try {
            Files.createDirectories(
                installTempRoot
            );

            return Files.createTempDirectory(
                installTempRoot,
                safeTemporaryPrefix(themeId)
            );
        } catch (IOException error) {
            throw new ThemeArchiveExtractionException(
                ThemeInstallErrorCode
                    .TEMP_DIRECTORY_CREATE_FAILED,
                ThemeInstallStage.PREPARATION,
                "创建独立主题安装工作目录失败："
                    + safeMessage(error),
                error,
                true
            );
        }
    }

    /**
     * 获取正式或测试临时根目录。
     */
    private Path resolveTempRoot() {
        if (fixedTempRoot != null) {
            return fixedTempRoot;
        }

        workDirResolver
            .ensureBaseDirectories();

        return workDirResolver
            .tempDir()
            .toAbsolutePath()
            .normalize();
    }

    /**
     * 流式受限解压 ZIP。
     */
    private ExtractionStatistics
        extractArchive(
            Path packagePath,
            ThemePackageValidationResult
                validationResult,
            Path extractedThemeDirectory
        ) {

        int entryCount = 0;
        int fileCount = 0;
        long totalBytes = 0L;

        Set<String> canonicalNames =
            new HashSet<>();

        boolean archiveOpened = false;

        try (
            ZipFile zipFile =
                ZipFile.builder()
                    .setPath(packagePath)
                    .setUseUnicodeExtraFields(
                        true
                    )
                    .setMaxNumberOfDisks(1)
                    .get()
        ) {
            archiveOpened = true;

            Enumeration<ZipArchiveEntry>
                entries = zipFile.getEntries();

            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry =
                    entries.nextElement();

                entryCount++;

                String normalizedName =
                    validateEntryAndNormalize(
                        zipFile,
                        entry,
                        validationResult
                            .archiveRoot(),
                        canonicalNames
                    );

                String relativeName =
                    removeArchiveRoot(
                        normalizedName,
                        validationResult
                            .archiveRoot()
                    );

                if (relativeName.isBlank()) {
                    if (!entry.isDirectory()) {
                        throw invalidContent(
                            normalizedName,
                            "主题包装根条目必须是目录。"
                        );
                    }

                    continue;
                }

                Path targetPath =
                    extractedThemeDirectory
                        .resolve(relativeName)
                        .normalize();

                if (
                    !fileOperations.isWithin(
                        extractedThemeDirectory,
                        targetPath
                    )
                ) {
                    throw invalidContent(
                        normalizedName,
                        "解压目标超出临时主题目录。"
                    );
                }

                if (entry.isDirectory()) {
                    createSafeDirectory(
                        targetPath,
                        extractedThemeDirectory
                    );

                    continue;
                }

                createSafeDirectory(
                    targetPath.getParent(),
                    extractedThemeDirectory
                );

                long writtenBytes =
                    writeEntry(
                        zipFile,
                        entry,
                        targetPath,
                        normalizedName,
                        totalBytes,
                        validationResult
                            .totalUncompressedSize()
                    );

                totalBytes =
                    safeAdd(
                        totalBytes,
                        writtenBytes
                    );

                fileCount++;
            }
        } catch (
            ThemeArchiveExtractionException
                error
        ) {
            throw error;
        } catch (IOException error) {
            ThemeInstallErrorCode errorCode =
                archiveOpened
                    ? ThemeInstallErrorCode
                        .ARCHIVE_EXTRACTION_FAILED
                    : ThemeInstallErrorCode
                        .ARCHIVE_OPEN_FAILED;

            throw new ThemeArchiveExtractionException(
                errorCode,
                ThemeInstallStage.EXTRACTION,
                archiveOpened
                    ? "读取主题 ZIP 条目失败："
                        + safeMessage(error)
                    : "无法打开经过校验的主题 ZIP："
                        + safeMessage(error),
                error,
                false
            );
        }

        return new ExtractionStatistics(
            entryCount,
            fileCount,
            totalBytes
        );
    }

    /**
     * 二次验证 ZIP 条目。
     */
    private String validateEntryAndNormalize(
        ZipFile zipFile,
        ZipArchiveEntry entry,
        String archiveRoot,
        Set<String> canonicalNames
    ) {
        if (entry == null) {
            throw invalidContent(
                "",
                "ZIP 中存在空条目对象。"
            );
        }

        String originalName =
            entry.getName();

        if (
            originalName == null
                || originalName.isBlank()
        ) {
            throw invalidContent(
                "",
                "ZIP 中存在名称为空的条目。"
            );
        }

        String slashName =
            originalName.replace(
                '\\',
                '/'
            );

        if (!slashName.equals(slashName.trim())) {
            throw invalidContent(
                originalName,
                "ZIP 条目名称首尾不能包含空白字符。"
            );
        }

        String normalizedName =
            slashName;

        if (
            normalizedName.startsWith("/")
                || normalizedName
                    .contains(":")
                || containsControlCharacter(
                    normalizedName
                )
                || containsUnsafeSegment(
                    normalizedName
                )
                || containsRepeatedSeparator(
                    normalizedName
                )
        ) {
            throw invalidContent(
                originalName,
                "ZIP 条目路径未通过二次安全检查。"
            );
        }

        if (
            !belongsToArchiveRoot(
                normalizedName,
                archiveRoot
            )
        ) {
            throw invalidContent(
                originalName,
                "ZIP 条目不属于已校验的主题根目录。"
            );
        }

        String canonicalName =
            canonicalEntryName(
                normalizedName
            );

        if (
            canonicalName.isBlank()
                || !canonicalNames.add(
                    canonicalName
                )
        ) {
            throw invalidContent(
                originalName,
                "ZIP 中存在重复或大小写冲突条目。"
            );
        }

        if (
            !zipFile.canReadEntryData(entry)
        ) {
            throw invalidContent(
                originalName,
                "当前压缩组件无法读取该 ZIP 条目。"
            );
        }

        if (
            entry.getGeneralPurposeBit()
                != null
                && entry
                    .getGeneralPurposeBit()
                    .usesEncryption()
        ) {
            throw invalidContent(
                originalName,
                "主题 ZIP 不允许包含加密条目。"
            );
        }

        if (entry.isUnixSymlink()) {
            throw invalidContent(
                originalName,
                "主题 ZIP 不允许包含符号链接。"
            );
        }

        if (isSpecialUnixFile(entry)) {
            throw invalidContent(
                originalName,
                "主题 ZIP 不允许包含特殊 Unix 文件。"
            );
        }

        return normalizedName;
    }

    /**
     * 去除可选的单层 ZIP 包装目录。
     */
    private String removeArchiveRoot(
        String normalizedName,
        String archiveRoot
    ) {
        String normalizedRoot =
            normalizeText(archiveRoot);

        if (normalizedRoot.isBlank()) {
            return normalizedName;
        }

        if (
            normalizedName.equalsIgnoreCase(
                normalizedRoot
            )
        ) {
            return "";
        }

        String prefix =
            normalizedRoot + "/";

        if (
            normalizedName.length()
                > prefix.length()
                && normalizedName
                    .regionMatches(
                        true,
                        0,
                        prefix,
                        0,
                        prefix.length()
                    )
        ) {
            return normalizedName.substring(
                prefix.length()
            );
        }

        throw invalidContent(
            normalizedName,
            "无法剥离已校验的主题包装目录。"
        );
    }

    /**
     * 判断条目是否位于 archiveRoot 中。
     */
    private boolean belongsToArchiveRoot(
        String normalizedName,
        String archiveRoot
    ) {
        String normalizedRoot =
            normalizeText(archiveRoot);

        if (normalizedRoot.isBlank()) {
            return true;
        }

        if (
            normalizedName.equalsIgnoreCase(
                normalizedRoot
            )
        ) {
            return true;
        }

        String prefix =
            normalizedRoot + "/";

        return normalizedName.length()
                > prefix.length()
            && normalizedName.regionMatches(
                true,
                0,
                prefix,
                0,
                prefix.length()
            );
    }

    /**
     * 创建受工作目录约束的目录。
     */
    private void createSafeDirectory(
        Path directory,
        Path extractedThemeDirectory
    ) throws IOException {

        if (directory == null) {
            throw invalidContent(
                "",
                "无法确定 ZIP 条目的父目录。"
            );
        }

        if (
            !fileOperations.isWithin(
                extractedThemeDirectory,
                directory
            )
        ) {
            throw invalidContent(
                directory.toString(),
                "待创建目录超出临时主题根目录。"
            );
        }

        Files.createDirectories(directory);

        if (
            Files.isSymbolicLink(directory)
                || !Files.isDirectory(
                    directory,
                    LinkOption.NOFOLLOW_LINKS
                )
        ) {
            throw invalidContent(
                directory.toString(),
                "解压目标目录不是安全普通目录。"
            );
        }
    }

    /**
     * 流式写入一个普通文件。
     */
    private long writeEntry(
        ZipFile zipFile,
        ZipArchiveEntry entry,
        Path targetPath,
        String entryName,
        long currentTotal,
        long expectedTotal
    ) throws IOException {

        if (
            Files.exists(
                targetPath,
                LinkOption.NOFOLLOW_LINKS
            )
        ) {
            throw invalidContent(
                entryName,
                "ZIP 条目目标已经存在。"
            );
        }

        long written = 0L;

        try (
            InputStream input =
                zipFile.getInputStream(entry);
            OutputStream output =
                Files.newOutputStream(
                    targetPath,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
                )
        ) {
            byte[] buffer =
                new byte[BUFFER_SIZE];

            int read;

            while (
                (read = input.read(buffer))
                    != -1
            ) {
                written =
                    safeAdd(
                        written,
                        read
                    );

                long resultingTotal =
                    safeAdd(
                        currentTotal,
                        written
                    );

                if (
                    resultingTotal
                        > expectedTotal
                ) {
                    throw invalidContent(
                        entryName,
                        "实际解压字节数超过安全校验结果。"
                    );
                }

                output.write(
                    buffer,
                    0,
                    read
                );
            }
        }

        if (
            Files.isSymbolicLink(targetPath)
                || !Files.isRegularFile(
                    targetPath,
                    LinkOption.NOFOLLOW_LINKS
                )
        ) {
            throw invalidContent(
                entryName,
                "解压后的目标不是安全普通文件。"
            );
        }

        return written;
    }

    /**
     * 校验条目数量和总字节数与原始扫描一致。
     */
    private void verifyStatistics(
        ExtractionStatistics statistics,
        ThemePackageValidationResult
            validationResult
    ) {
        if (
            statistics.entryCount()
                != validationResult
                    .entryCount()
        ) {
            throw new ThemeArchiveExtractionException(
                ThemeInstallErrorCode
                    .EXTRACTED_CONTENT_INVALID,
                ThemeInstallStage.POST_VALIDATION,
                "解压条目数量与安全校验结果不一致："
                    + statistics.entryCount()
                    + " / "
                    + validationResult.entryCount(),
                false
            );
        }

        if (
            statistics.totalBytes()
                != validationResult
                    .totalUncompressedSize()
        ) {
            throw new ThemeArchiveExtractionException(
                ThemeInstallErrorCode
                    .EXTRACTED_CONTENT_INVALID,
                ThemeInstallStage.POST_VALIDATION,
                "实际解压字节数与安全校验结果不一致："
                    + statistics.totalBytes()
                    + " / "
                    + validationResult
                        .totalUncompressedSize(),
                false
            );
        }
    }

    /**
     * 二次解析解压后的 theme.yaml。
     */
    private ThemeManifest verifyExtractedManifest(
        Path extractedThemeDirectory,
        ThemePackageValidationResult
            validationResult
    ) {
        Path manifestFile =
            extractedThemeDirectory
                .resolve("theme.yaml")
                .normalize();

        if (
            !fileOperations.isWithin(
                extractedThemeDirectory,
                manifestFile
            )
                || !Files.isRegularFile(
                    manifestFile,
                    LinkOption.NOFOLLOW_LINKS
                )
                || Files.isSymbolicLink(
                    manifestFile
                )
        ) {
            throw new ThemeArchiveExtractionException(
                ThemeInstallErrorCode
                    .EXTRACTED_MANIFEST_MISMATCH,
                ThemeInstallStage.POST_VALIDATION,
                "解压后没有找到安全的 theme.yaml。",
                false
            );
        }

        final ThemeManifest extractedManifest;

        try {
            extractedManifest =
                themeManifestParser.parse(
                    manifestFile
                );
        } catch (ThemeManifestException error) {
            throw new ThemeArchiveExtractionException(
                ThemeInstallErrorCode
                    .EXTRACTED_MANIFEST_MISMATCH,
                ThemeInstallStage.POST_VALIDATION,
                "解压后的 theme.yaml 无法重新解析："
                    + safeMessage(error),
                error,
                false
            );
        }

        if (
            !extractedManifest.equals(
                validationResult.manifest()
            )
        ) {
            throw new ThemeArchiveExtractionException(
                ThemeInstallErrorCode
                    .EXTRACTED_MANIFEST_MISMATCH,
                ThemeInstallStage.POST_VALIDATION,
                "解压后的主题清单与原始校验清单不一致。",
                false
            );
        }

        return extractedManifest;
    }

    /**
     * 遍历解压后的真实目录进行最终边界检查。
     */
    private void verifyExtractedTree(
        Path extractedThemeDirectory
    ) {
        try {
            Files.walkFileTree(
                extractedThemeDirectory,
                new SimpleFileVisitor<Path>() {

                    /**
                     * 检查每个真实目录。
                     */
                    @Override
                    public FileVisitResult
                        preVisitDirectory(
                            Path directory,
                            BasicFileAttributes
                                attributes
                        ) {

                        verifyExtractedPath(
                            extractedThemeDirectory,
                            directory,
                            true
                        );

                        return FileVisitResult.CONTINUE;
                    }

                    /**
                     * 检查每个真实文件。
                     */
                    @Override
                    public FileVisitResult visitFile(
                        Path file,
                        BasicFileAttributes
                            attributes
                    ) {

                        verifyExtractedPath(
                            extractedThemeDirectory,
                            file,
                            false
                        );

                        return FileVisitResult.CONTINUE;
                    }
                }
            );
        } catch (
            ThemeArchiveExtractionException
                error
        ) {
            throw error;
        } catch (IOException error) {
            throw new ThemeArchiveExtractionException(
                ThemeInstallErrorCode
                    .EXTRACTED_CONTENT_INVALID,
                ThemeInstallStage.POST_VALIDATION,
                "遍历解压后的主题目录失败："
                    + safeMessage(error),
                error,
                false
            );
        }
    }

    /**
     * 检查解压后的单个真实路径。
     */
    private void verifyExtractedPath(
        Path root,
        Path candidate,
        boolean directory
    ) {
        if (
            !fileOperations.isWithin(
                root,
                candidate
            )
                || Files.isSymbolicLink(
                    candidate
                )
        ) {
            throw invalidContent(
                candidate.toString(),
                "解压后的路径超出主题根目录或属于符号链接。"
            );
        }

        boolean validType =
            directory
                ? Files.isDirectory(
                    candidate,
                    LinkOption.NOFOLLOW_LINKS
                )
                : Files.isRegularFile(
                    candidate,
                    LinkOption.NOFOLLOW_LINKS
                );

        if (!validType) {
            throw invalidContent(
                candidate.toString(),
                "解压后的路径类型不安全。"
            );
        }
    }

    /**
     * 验证工作目录内部边界。
     */
    private void ensureInsideWorkspace(
        Path workspace,
        Path candidate
    ) {
        if (
            !fileOperations.isWithin(
                workspace,
                candidate
            )
        ) {
            throw new ThemeArchiveExtractionException(
                ThemeInstallErrorCode
                    .TARGET_PATH_INVALID,
                ThemeInstallStage.PREPARATION,
                "临时主题目录超出独立安装工作目录。",
                false
            );
        }
    }

    /**
     * 失败后删除整个独立工作目录。
     */
    private ThemeArchiveExtractionException
        cleanupAndRebuildException(
            Path workspaceDirectory,
            ThemeArchiveExtractionException
                originalError
        ) {

        if (workspaceDirectory == null) {
            return new ThemeArchiveExtractionException(
                originalError.errorCode(),
                originalError.stage(),
                originalError.getMessage(),
                originalError.getCause(),
                true
            );
        }

        try {
            fileOperations.deleteRecursively(
                workspaceDirectory
            );

            return new ThemeArchiveExtractionException(
                originalError.errorCode(),
                originalError.stage(),
                originalError.getMessage(),
                originalError.getCause(),
                true
            );
        } catch (IOException cleanupError) {
            return new ThemeArchiveExtractionException(
                ThemeInstallErrorCode
                    .CLEANUP_FAILED,
                ThemeInstallStage.CLEANUP,
                "主题临时解压失败后无法完整清理工作目录："
                    + workspaceDirectory
                    + "。原始错误："
                    + originalError.getMessage()
                    + "。清理错误："
                    + safeMessage(cleanupError),
                cleanupError,
                false
            );
        }
    }

    /**
     * 计算 ZIP SHA-256。
     */
    private String calculateSha256(
        Path packagePath,
        ThemeInstallStage stage
    ) {
        try {
            MessageDigest digest =
                MessageDigest.getInstance(
                    "SHA-256"
                );

            try (
                InputStream input =
                    Files.newInputStream(
                        packagePath
                    )
            ) {
                byte[] buffer =
                    new byte[BUFFER_SIZE];

                int read;

                while (
                    (read = input.read(buffer))
                        != -1
                ) {
                    digest.update(
                        buffer,
                        0,
                        read
                    );
                }
            }

            return HexFormat
                .of()
                .formatHex(
                    digest.digest()
                );
        } catch (
            IOException
                | NoSuchAlgorithmException error
        ) {
            throw new ThemeArchiveExtractionException(
                ThemeInstallErrorCode
                    .ARCHIVE_OPEN_FAILED,
                stage,
                "计算主题 ZIP SHA-256 失败："
                    + safeMessage(error),
                error,
                true
            );
        }
    }

    /**
     * 判断路径是否包含点路径或父目录。
     */
    private boolean containsUnsafeSegment(
        String value
    ) {
        String[] segments =
            value.split(
                "/",
                -1
            );

        for (String segment : segments) {
            if (
                ".".equals(segment)
                    || "..".equals(segment)
            ) {
                return true;
            }
        }

        return false;
    }

    /**
     * 判断是否含有重复路径分隔符。
     */
    private boolean containsRepeatedSeparator(
        String value
    ) {
        if (value.contains("//")) {
            return true;
        }

        String[] segments =
            value.split(
                "/",
                -1
            );

        for (
            int index = 0;
            index < segments.length;
            index++
        ) {
            String segment =
                segments[index];

            boolean trailingDirectorySegment =
                index == segments.length - 1
                    && segment.isEmpty()
                    && value.endsWith("/");

            if (
                segment.isEmpty()
                    && !trailingDirectorySegment
            ) {
                return true;
            }
        }

        return false;
    }

    /**
     * 判断是否包含控制字符。
     */
    private boolean containsControlCharacter(
        String value
    ) {
        for (
            int index = 0;
            index < value.length();
            index++
        ) {
            char character =
                value.charAt(index);

            if (
                character < 32
                    || character == 127
            ) {
                return true;
            }
        }

        return false;
    }

    /**
     * 创建跨平台重复检查名称。
     */
    private String canonicalEntryName(
        String value
    ) {
        String normalized = value;

        while (
            normalized.endsWith("/")
                && normalized.length() > 1
        ) {
            normalized =
                normalized.substring(
                    0,
                    normalized.length() - 1
                );
        }

        return normalized
            .toLowerCase(Locale.ROOT);
    }

    /**
     * 判断 Unix 特殊文件。
     */
    private boolean isSpecialUnixFile(
        ZipArchiveEntry entry
    ) {
        if (
            entry == null
                || entry.isUnixSymlink()
        ) {
            return false;
        }

        int unixMode =
            entry.getUnixMode();

        if (unixMode == 0) {
            return false;
        }

        int fileType =
            unixMode
                & UNIX_FILE_TYPE_MASK;

        return fileType != 0
            && fileType != UNIX_REGULAR_FILE
            && fileType != UNIX_DIRECTORY
            && fileType != UNIX_SYMBOLIC_LINK;
    }

    /**
     * 创建 EXTRACTED_CONTENT_INVALID 异常。
     */
    private ThemeArchiveExtractionException
        invalidContent(
            String entryName,
            String reason
        ) {

        String safeEntryName =
            normalizeText(entryName);

        String message =
            safeEntryName.isBlank()
                ? reason
                : reason
                    + " 条目："
                    + safeEntryName;

        return new ThemeArchiveExtractionException(
            ThemeInstallErrorCode
                .EXTRACTED_CONTENT_INVALID,
            ThemeInstallStage.EXTRACTION,
            message,
            false
        );
    }

    /**
     * 安全 long 加法。
     */
    private long safeAdd(
        long left,
        long right
    ) {
        if (
            right > 0L
                && left
                    > Long.MAX_VALUE - right
        ) {
            return Long.MAX_VALUE;
        }

        return left + right;
    }

    /**
     * 创建安全的临时目录前缀。
     */
    private String safeTemporaryPrefix(
        String themeId
    ) {
        String normalized =
            normalizeText(themeId)
                .replaceAll(
                    "[^a-zA-Z0-9._-]",
                    "-"
                );

        if (normalized.isBlank()) {
            normalized = "theme";
        }

        if (normalized.length() > 40) {
            normalized =
                normalized.substring(
                    0,
                    40
                );
        }

        return normalized + "-";
    }

    /**
     * 标准化普通文本。
     */
    private String normalizeText(
        String value
    ) {
        if (
            value == null
                || value.isBlank()
        ) {
            return "";
        }

        return value.trim();
    }

    /**
     * 标准化 SHA-256。
     */
    private String normalizeHash(
        String value
    ) {
        return normalizeText(value)
            .toLowerCase(Locale.ROOT);
    }

    /**
     * 获取安全异常说明。
     */
    private String safeMessage(
        Throwable error
    ) {
        if (
            error == null
                || error.getMessage() == null
                || error.getMessage().isBlank()
        ) {
            return error == null
                ? "未知错误"
                : error
                    .getClass()
                    .getSimpleName();
        }

        return error.getMessage();
    }

    /**
     * 解压统计。
     */
    private record ExtractionStatistics(
        int entryCount,
        int fileCount,
        long totalBytes
    ) {
    }
}
