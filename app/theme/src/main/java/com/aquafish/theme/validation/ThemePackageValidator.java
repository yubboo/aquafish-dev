package com.aquafish.theme.validation;

import com.aquafish.theme.manifest.ThemeManifest;
import com.aquafish.theme.manifest.ThemeManifestException;
import com.aquafish.theme.manifest.ThemeManifestParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.stereotype.Component;

/**
 * Aquafish 主题 ZIP 安全校验器。
 *
 * <p>当前处理流程：</p>
 *
 * <pre>
 * 主题 ZIP
 * -> ZIP 条目安全扫描
 * -> 识别唯一 theme.yaml
 * -> 识别主题包根目录
 * -> 安全读取 UTF-8 清单
 * -> ThemeManifestParser
 * -> ThemePackageValidationResult
 * </pre>
 *
 * <p>
 * 当前类只读取 ZIP，不执行解压、安装或主题启用。
 * </p>
 */
@Component
public class ThemePackageValidator {

    /**
     * Windows 盘符路径。
     */
    private static final Pattern
        WINDOWS_DRIVE_PATTERN =
            Pattern.compile(
                "^[a-zA-Z]:($|/).*"
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
     * ZIP 流读取缓冲区。
     */
    private static final int
        READ_BUFFER_SIZE = 8192;

    /**
     * 只有实际解压内容达到 1 MiB 时，
     * 才执行异常压缩比判断。
     *
     * 避免极小文本文件因为 ZIP 固定头部开销
     * 产生没有实际意义的压缩比结果。
     */
    private static final long
        COMPRESSION_RATIO_MIN_BYTES =
            1024L * 1024L;

    /**
     * 主题包禁止携带的可执行或服务端代码扩展名。
     *
     * <p>
     * 浏览器端 .js 和 .mjs 是主题静态资源，
     * 因此不能放入该禁止列表。
     * </p>
     */
    private static final Set<String>
        DANGEROUS_FILE_EXTENSIONS = Set.of(
            ".exe",
            ".dll",
            ".so",
            ".dylib",
            ".com",
            ".scr",
            ".msi",
            ".msp",
            ".bat",
            ".cmd",
            ".ps1",
            ".psm1",
            ".sh",
            ".bash",
            ".zsh",
            ".fish",
            ".vbs",
            ".vbe",
            ".jse",
            ".wsf",
            ".wsh",
            ".hta",
            ".jar",
            ".war",
            ".ear",
            ".class",
            ".jsp",
            ".jspx",
            ".php",
            ".phtml",
            ".phar",
            ".asp",
            ".aspx",
            ".cgi",
            ".pl",
            ".py",
            ".rb",
            ".lua",
            ".apk",
            ".dex"
        );

    /**
     * 可能改变服务器行为的危险特殊文件名。
     */
    private static final Set<String>
        DANGEROUS_FILE_NAMES = Set.of(
            ".htaccess",
            ".user.ini",
            "web.config",
            "php.ini"
        );

    /**
     * 不会直接阻止安装，
     * 但不应该出现在生产主题包中的文件名。
     */
    private static final Set<String>
        UNRECOMMENDED_FILE_NAMES = Set.of(
            ".ds_store",
            "thumbs.db",
            "desktop.ini",
            "package-lock.json",
            "pnpm-lock.yaml",
            "yarn.lock",
            "npm-shrinkwrap.json"
        );

    /**
     * 不建议打包进生产主题的目录。
     */
    private static final Set<String>
        UNRECOMMENDED_PATH_SEGMENTS = Set.of(
            "__macosx",
            ".git",
            ".svn",
            ".hg",
            ".idea",
            ".vscode",
            "node_modules"
        );

    /**
     * 安全校验策略。
     */
    private final ThemePackageValidationPolicy
        policy;

    /**
     * 正式主题清单解析器。
     */
    private final ThemeManifestParser
        themeManifestParser;

    /**
     * Spring 默认构造方法。
     */
    public ThemePackageValidator() {
        this(
            ThemePackageValidationPolicy.defaults(),
            new ThemeManifestParser()
        );
    }

    /**
     * 自定义策略构造方法。
     *
     * @param policy 校验策略
     */
    ThemePackageValidator(
        ThemePackageValidationPolicy policy
    ) {
        this(
            policy,
            new ThemeManifestParser()
        );
    }

    /**
     * 完整依赖构造方法。
     *
     * @param policy 校验策略
     * @param themeManifestParser 清单解析器
     */
    ThemePackageValidator(
        ThemePackageValidationPolicy policy,
        ThemeManifestParser themeManifestParser
    ) {
        if (policy == null) {
            throw new IllegalArgumentException(
                "主题包校验策略不能为空。"
            );
        }

        if (themeManifestParser == null) {
            throw new IllegalArgumentException(
                "主题清单解析器不能为空。"
            );
        }

        this.policy = policy;
        this.themeManifestParser =
            themeManifestParser;
    }

    /**
     * 对主题 ZIP 执行只读安全校验。
     *
     * @param packagePath ZIP 文件路径
     * @return 完整校验结果
     */
    public ThemePackageValidationResult validate(
        Path packagePath
    ) {
        List<ThemePackageIssue> issues =
            new ArrayList<>();

        if (packagePath == null) {
            addError(
                issues,
                ThemePackageIssueCode
                    .PACKAGE_PATH_MISSING,
                "",
                "主题包路径不能为空。"
            );

            return createResult(
                null,
                "",
                0L,
                0,
                0L,
                issues
            );
        }

        Path normalizedPackagePath =
            packagePath
                .toAbsolutePath()
                .normalize();

        if (
            !Files.exists(
                normalizedPackagePath,
                LinkOption.NOFOLLOW_LINKS
            )
        ) {
            addError(
                issues,
                ThemePackageIssueCode
                    .PACKAGE_NOT_FOUND,
                "",
                "主题包文件不存在："
                    + normalizedPackagePath
            );

            return createResult(
                null,
                "",
                0L,
                0,
                0L,
                issues
            );
        }

        if (
            !Files.isRegularFile(
                normalizedPackagePath,
                LinkOption.NOFOLLOW_LINKS
            )
        ) {
            addError(
                issues,
                ThemePackageIssueCode
                    .PACKAGE_NOT_REGULAR_FILE,
                "",
                "主题包必须是普通文件："
                    + normalizedPackagePath
            );

            return createResult(
                null,
                "",
                0L,
                0,
                0L,
                issues
            );
        }

        String fileName =
            normalizedPackagePath.getFileName()
                == null
                    ? ""
                    : normalizedPackagePath
                        .getFileName()
                        .toString();

        if (
            !fileName
                .toLowerCase(Locale.ROOT)
                .endsWith(".zip")
        ) {
            addError(
                issues,
                ThemePackageIssueCode
                    .PACKAGE_EXTENSION_INVALID,
                "",
                "主题包文件扩展名必须是 .zip："
                    + fileName
            );

            return createResult(
                null,
                "",
                readPackageSizeSafely(
                    normalizedPackagePath,
                    issues
                ),
                0,
                0L,
                issues
            );
        }

        long packageSize =
            readPackageSizeSafely(
                normalizedPackagePath,
                issues
            );

        if (!issues.isEmpty()) {
            return createResult(
                null,
                "",
                packageSize,
                0,
                0L,
                issues
            );
        }

        if (
            packageSize
                > policy.maxPackageBytes()
        ) {
            addError(
                issues,
                ThemePackageIssueCode
                    .PACKAGE_SIZE_EXCEEDED,
                "",
                "主题 ZIP 文件大小超过限制："
                    + packageSize
                    + " 字节，最大允许 "
                    + policy.maxPackageBytes()
                    + " 字节。"
            );

            return createResult(
                null,
                "",
                packageSize,
                0,
                0L,
                issues
            );
        }

        String sha256 =
            calculatePackageSha256(
                normalizedPackagePath,
                issues
            );

        int entryCount = 0;
        long knownTotalUncompressedSize = 0L;

        ThemeManifest manifest = null;
        String archiveRoot = "";

        Set<String> canonicalEntryNames =
            new HashSet<>();

        List<String> normalizedEntryNames =
            new ArrayList<>();

        List<ManifestCandidate>
            manifestCandidates =
                new ArrayList<>();

        try (
            ZipFile zipFile =
                ZipFile.builder()
                    .setPath(
                        normalizedPackagePath
                    )
                    .setUseUnicodeExtraFields(
                        true
                    )
                    .setMaxNumberOfDisks(
                        1
                    )
                    .get()
        ) {
            Enumeration<ZipArchiveEntry>
                entries = zipFile.getEntries();

            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry =
                    entries.nextElement();

                entryCount++;

                if (
                    entryCount
                        > policy.maxEntryCount()
                ) {
                    addError(
                        issues,
                        ThemePackageIssueCode
                            .ENTRY_COUNT_EXCEEDED,
                        "",
                        "主题包条目数量超过限制："
                            + policy.maxEntryCount()
                    );

                    break;
                }

                String originalName =
                    entry == null
                        ? ""
                        : entry.getName();

                String normalizedName =
                    normalizeEntryName(
                        originalName
                    );

                if (!normalizedName.isBlank()) {
                    normalizedEntryNames.add(
                        normalizedName
                    );
                }

                inspectEntry(
                    zipFile,
                    entry,
                    canonicalEntryNames,
                    issues
                );

                if (
                    isManifestCandidate(
                        entry,
                        normalizedName
                    )
                ) {
                    manifestCandidates.add(
                        new ManifestCandidate(
                            entry,
                            normalizedName
                        )
                    );
                }

                inspectFilePolicy(
                    entry,
                    normalizedName,
                    issues
                );

                long actualEntrySize =
                    inspectEntryContent(
                        zipFile,
                        entry,
                        normalizedName,
                        knownTotalUncompressedSize,
                        issues
                    );

                knownTotalUncompressedSize =
                    safeAdd(
                        knownTotalUncompressedSize,
                        actualEntrySize
                    );
            }

            if (entryCount == 0) {
                addError(
                    issues,
                    ThemePackageIssueCode
                        .ARCHIVE_EMPTY,
                    "",
                    "主题 ZIP 中没有任何条目。"
                );
            } else if (
                !hasIssue(
                    issues,
                    ThemePackageIssueCode
                        .ENTRY_COUNT_EXCEEDED
                )
            ) {
                ManifestReadResult
                    manifestReadResult =
                        inspectManifest(
                            zipFile,
                            manifestCandidates,
                            normalizedEntryNames,
                            issues
                        );

                manifest =
                    manifestReadResult.manifest();

                archiveRoot =
                    manifestReadResult.archiveRoot();
            }
        } catch (IOException error) {
            addError(
                issues,
                ThemePackageIssueCode
                    .ARCHIVE_OPEN_FAILED,
                "",
                "无法打开主题 ZIP："
                    + safeMessage(error)
            );
        }

        return createResult(
            manifest,
            archiveRoot,
            packageSize,
            entryCount,
            knownTotalUncompressedSize,
            sha256,
            issues
        );
    }

    /**
     * 检查单个 ZIP 条目。
     */
    private void inspectEntry(
        ZipFile zipFile,
        ZipArchiveEntry entry,
        Set<String> canonicalEntryNames,
        List<ThemePackageIssue> issues
    ) {
        String originalName =
            entry == null
                ? null
                : entry.getName();

        if (
            originalName == null
                || originalName.isBlank()
        ) {
            addError(
                issues,
                ThemePackageIssueCode
                    .ENTRY_NAME_EMPTY,
                "",
                "主题 ZIP 中存在名称为空的条目。"
            );

            return;
        }

        String normalizedName =
            normalizeEntryName(
                originalName
            );

        if (normalizedName.isBlank()) {
            addError(
                issues,
                ThemePackageIssueCode
                    .ENTRY_NAME_EMPTY,
                originalName,
                "主题 ZIP 条目名称不能为空。"
            );

            return;
        }

        if (
            normalizedName.length()
                > policy.maxPathLength()
        ) {
            addError(
                issues,
                ThemePackageIssueCode
                    .ENTRY_PATH_LENGTH_EXCEEDED,
                originalName,
                "ZIP 条目路径长度超过限制："
                    + normalizedName.length()
                    + "，最大允许 "
                    + policy.maxPathLength()
                    + "。"
            );
        }

        int pathDepth =
            calculatePathDepth(
                normalizedName
            );

        if (
            pathDepth
                > policy.maxPathDepth()
        ) {
            addError(
                issues,
                ThemePackageIssueCode
                    .ENTRY_PATH_DEPTH_EXCEEDED,
                originalName,
                "ZIP 条目目录层级超过限制："
                    + pathDepth
                    + "，最大允许 "
                    + policy.maxPathDepth()
                    + "。"
            );
        }

        if (
            normalizedName.startsWith("/")
        ) {
            addError(
                issues,
                ThemePackageIssueCode
                    .ENTRY_ABSOLUTE_PATH,
                originalName,
                "ZIP 条目不能使用绝对路径："
                    + originalName
            );
        }

        if (
            WINDOWS_DRIVE_PATTERN
                .matcher(normalizedName)
                .matches()
                || normalizedName.contains(":")
        ) {
            addError(
                issues,
                ThemePackageIssueCode
                    .ENTRY_DRIVE_PREFIX,
                originalName,
                "ZIP 条目不能包含 Windows 盘符或冒号："
                    + originalName
            );
        }

        if (
            containsUnsafePathSegment(
                normalizedName
            )
        ) {
            addError(
                issues,
                ThemePackageIssueCode
                    .ENTRY_PATH_TRAVERSAL,
                originalName,
                "ZIP 条目包含不安全的路径片段："
                    + originalName
            );
        }

        if (
            containsControlCharacter(
                normalizedName
            )
        ) {
            addError(
                issues,
                ThemePackageIssueCode
                    .ENTRY_PATH_TRAVERSAL,
                originalName,
                "ZIP 条目名称包含控制字符："
                    + printableEntryName(
                        originalName
                    )
            );
        }

        String canonicalName =
            canonicalEntryName(
                normalizedName
            );

        if (
            !canonicalName.isBlank()
                && !canonicalEntryNames.add(
                    canonicalName
                )
        ) {
            addError(
                issues,
                ThemePackageIssueCode
                    .ENTRY_DUPLICATE,
                originalName,
                "主题 ZIP 中存在重复或大小写冲突条目："
                    + originalName
            );
        }

        if (
            !zipFile.canReadEntryData(entry)
        ) {
            addError(
                issues,
                ThemePackageIssueCode
                    .ENTRY_UNREADABLE,
                originalName,
                "当前压缩组件无法安全读取该 ZIP 条目。"
            );
        }

        if (
            entry.getGeneralPurposeBit()
                != null
                && entry
                    .getGeneralPurposeBit()
                    .usesEncryption()
        ) {
            addError(
                issues,
                ThemePackageIssueCode
                    .ENTRY_ENCRYPTED,
                originalName,
                "主题包不允许包含加密 ZIP 条目。"
            );
        }

        if (entry.isUnixSymlink()) {
            addError(
                issues,
                ThemePackageIssueCode
                    .ENTRY_SYMBOLIC_LINK,
                originalName,
                "主题包不允许包含 Unix 符号链接。"
            );
        }

        if (isSpecialUnixFile(entry)) {
            addError(
                issues,
                ThemePackageIssueCode
                    .ENTRY_SPECIAL_FILE,
                originalName,
                "主题包不允许包含设备文件、FIFO、"
                    + "套接字或其他特殊 Unix 文件。"
            );
        }
    }

    /**
     * 读取和解析唯一主题清单。
     */
    private ManifestReadResult inspectManifest(
        ZipFile zipFile,
        List<ManifestCandidate> candidates,
        List<String> normalizedEntryNames,
        List<ThemePackageIssue> issues
    ) {
        if (
            candidates == null
                || candidates.isEmpty()
        ) {
            addError(
                issues,
                ThemePackageIssueCode
                    .MANIFEST_MISSING,
                "",
                "主题包中没有找到 theme.yaml。"
            );

            return ManifestReadResult.empty();
        }

        if (candidates.size() > 1) {
            addError(
                issues,
                ThemePackageIssueCode
                    .MANIFEST_DUPLICATE,
                "",
                "主题包中存在多个 theme.yaml，"
                    + "无法确定唯一主题清单。"
            );

            return ManifestReadResult.empty();
        }

        ManifestCandidate candidate =
            candidates.get(0);

        String manifestPath =
            candidate.normalizedName();

        String fileName =
            manifestFileName(
                manifestPath
            );

        if (!"theme.yaml".equals(fileName)) {
            addError(
                issues,
                ThemePackageIssueCode
                    .MANIFEST_INVALID,
                manifestPath,
                "主题清单文件名必须严格使用小写 theme.yaml。"
            );

            return ManifestReadResult.empty();
        }

        if (
            !isSafeManifestLocation(
                manifestPath
            )
        ) {
            return ManifestReadResult.empty();
        }

        String archiveRoot =
            determineArchiveRoot(
                manifestPath,
                issues
            );

        if (archiveRoot == null) {
            return ManifestReadResult.empty();
        }

        validateArchiveRootStructure(
            archiveRoot,
            normalizedEntryNames,
            issues
        );

        ZipArchiveEntry manifestEntry =
            candidate.entry();

        if (
            !zipFile.canReadEntryData(
                manifestEntry
            )
                || manifestEntry
                    .isUnixSymlink()
                || isSpecialUnixFile(
                    manifestEntry
                )
                || (
                    manifestEntry
                        .getGeneralPurposeBit()
                        != null
                    && manifestEntry
                        .getGeneralPurposeBit()
                        .usesEncryption()
                )
        ) {
            addError(
                issues,
                ThemePackageIssueCode
                    .MANIFEST_INVALID,
                manifestPath,
                "theme.yaml 条目本身不安全，无法读取。"
            );

            return new ManifestReadResult(
                null,
                archiveRoot
            );
        }

        byte[] manifestBytes =
            readManifestBytes(
                zipFile,
                manifestEntry,
                issues
            );

        if (manifestBytes == null) {
            return new ManifestReadResult(
                null,
                archiveRoot
            );
        }

        String manifestText =
            decodeUtf8Manifest(
                manifestBytes,
                manifestPath,
                issues
            );

        if (manifestText == null) {
            return new ManifestReadResult(
                null,
                archiveRoot
            );
        }

        try {
            ThemeManifest manifest =
                themeManifestParser.parse(
                    manifestText
                );

            return new ManifestReadResult(
                manifest,
                archiveRoot
            );
        } catch (ThemeManifestException error) {
            addError(
                issues,
                ThemePackageIssueCode
                    .MANIFEST_INVALID,
                manifestPath,
                "theme.yaml 解析失败："
                    + safeMessage(error)
            );

            return new ManifestReadResult(
                null,
                archiveRoot
            );
        }
    }

    /**
     * 判断 ZIP 条目是否可能是主题清单。
     *
     * 使用不区分大小写匹配，
     * 以便对 Theme.yaml 产生明确错误，
     * 而不是简单报告缺少清单。
     */
    private boolean isManifestCandidate(
        ZipArchiveEntry entry,
        String normalizedName
    ) {
        if (
            entry == null
                || entry.isDirectory()
                || normalizedName == null
                || normalizedName.isBlank()
        ) {
            return false;
        }

        return "theme.yaml".equalsIgnoreCase(
            manifestFileName(
                normalizedName
            )
        );
    }

    /**
     * 获取路径中的文件名。
     */
    private String manifestFileName(
        String normalizedName
    ) {
        if (
            normalizedName == null
                || normalizedName.isBlank()
        ) {
            return "";
        }

        int separator =
            normalizedName.lastIndexOf('/');

        if (separator < 0) {
            return normalizedName;
        }

        return normalizedName.substring(
            separator + 1
        );
    }

    /**
     * 判断清单路径是否已通过基础路径安全条件。
     */
    private boolean isSafeManifestLocation(
        String manifestPath
    ) {
        return manifestPath != null
            && !manifestPath.isBlank()
            && !manifestPath.startsWith("/")
            && !manifestPath.contains(":")
            && !WINDOWS_DRIVE_PATTERN
                .matcher(manifestPath)
                .matches()
            && !containsUnsafePathSegment(
                manifestPath
            )
            && !containsControlCharacter(
                manifestPath
            );
    }

    /**
     * 识别主题包根目录。
     *
     * <p>允许：</p>
     *
     * <pre>
     * theme.yaml
     *
     * sample-theme/theme.yaml
     * </pre>
     *
     * <p>拒绝：</p>
     *
     * <pre>
     * outer/inner/theme.yaml
     * </pre>
     *
     * @return 空字符串代表清单直接位于 ZIP 根目录；
     *         null 代表结构非法
     */
    private String determineArchiveRoot(
        String manifestPath,
        List<ThemePackageIssue> issues
    ) {
        String[] segments =
            manifestPath.split(
                "/",
                -1
            );

        if (
            segments.length == 1
                && "theme.yaml".equals(
                    segments[0]
                )
        ) {
            return "";
        }

        if (
            segments.length == 2
                && !segments[0].isBlank()
                && "theme.yaml".equals(
                    segments[1]
                )
        ) {
            return segments[0];
        }

        addError(
            issues,
            ThemePackageIssueCode
                .ROOT_STRUCTURE_INVALID,
            manifestPath,
            "主题包只允许 theme.yaml 位于 ZIP 根目录，"
                + "或位于一个单层主题文件夹中。"
        );

        return null;
    }

    /**
     * 验证单层主题文件夹模式下，
     * 所有条目都必须位于同一个主题根目录中。
     */
    private void validateArchiveRootStructure(
        String archiveRoot,
        List<String> normalizedEntryNames,
        List<ThemePackageIssue> issues
    ) {
        if (
            archiveRoot == null
                || archiveRoot.isBlank()
                || normalizedEntryNames == null
        ) {
            return;
        }

        String canonicalRoot =
            canonicalEntryName(
                archiveRoot
            );

        String rootPrefix =
            canonicalRoot + "/";

        for (
            String normalizedEntryName :
            normalizedEntryNames
        ) {
            String canonicalName =
                canonicalEntryName(
                    normalizedEntryName
                );

            if (
                canonicalName.equals(
                    canonicalRoot
                )
                    || canonicalName.startsWith(
                        rootPrefix
                    )
            ) {
                continue;
            }

            addError(
                issues,
                ThemePackageIssueCode
                    .ROOT_STRUCTURE_INVALID,
                normalizedEntryName,
                "主题包使用单层主题目录包装时，"
                    + "所有文件都必须位于目录 "
                    + archiveRoot
                    + " 内。"
            );

            return;
        }
    }

    /**
     * 安全读取 theme.yaml 内容。
     *
     * 即使 ZIP 元数据中的大小未知，
     * 也会在实际读取时执行硬限制。
     */
    private byte[] readManifestBytes(
        ZipFile zipFile,
        ZipArchiveEntry manifestEntry,
        List<ThemePackageIssue> issues
    ) {
        long declaredSize =
            manifestEntry.getSize();

        if (
            declaredSize
                > policy.maxManifestBytes()
        ) {
            addError(
                issues,
                ThemePackageIssueCode
                    .MANIFEST_SIZE_EXCEEDED,
                manifestEntry.getName(),
                "theme.yaml 大小超过限制："
                    + declaredSize
                    + " 字节，最大允许 "
                    + policy.maxManifestBytes()
                    + " 字节。"
            );

            return null;
        }

        try (
            InputStream input =
                zipFile.getInputStream(
                    manifestEntry
                );
            ByteArrayOutputStream output =
                new ByteArrayOutputStream()
        ) {
            byte[] buffer =
                new byte[READ_BUFFER_SIZE];

            long totalRead = 0L;

            int read;

            while (
                (read = input.read(buffer))
                    != -1
            ) {
                totalRead += read;

                if (
                    totalRead
                        > policy.maxManifestBytes()
                ) {
                    addError(
                        issues,
                        ThemePackageIssueCode
                            .MANIFEST_SIZE_EXCEEDED,
                        manifestEntry.getName(),
                        "theme.yaml 实际读取大小超过限制："
                            + policy.maxManifestBytes()
                            + " 字节。"
                    );

                    return null;
                }

                output.write(
                    buffer,
                    0,
                    read
                );
            }

            return output.toByteArray();
        } catch (IOException error) {
            addError(
                issues,
                ThemePackageIssueCode
                    .MANIFEST_INVALID,
                manifestEntry.getName(),
                "无法读取 theme.yaml："
                    + safeMessage(error)
            );

            return null;
        }
    }

    /**
     * 严格使用 UTF-8 解码清单。
     *
     * 非法 UTF-8 不允许被替换字符静默修复。
     */
    private String decodeUtf8Manifest(
        byte[] manifestBytes,
        String manifestPath,
        List<ThemePackageIssue> issues
    ) {
        try {
            return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(
                    CodingErrorAction.REPORT
                )
                .onUnmappableCharacter(
                    CodingErrorAction.REPORT
                )
                .decode(
                    ByteBuffer.wrap(
                        manifestBytes
                    )
                )
                .toString();
        } catch (CharacterCodingException error) {
            addError(
                issues,
                ThemePackageIssueCode
                    .MANIFEST_INVALID,
                manifestPath,
                "theme.yaml 必须使用有效的 UTF-8 编码。"
            );

            return null;
        }
    }

    /**
     * 标准化 ZIP 条目路径分隔符。
     */
    private String normalizeEntryName(
        String entryName
    ) {
        if (entryName == null) {
            return "";
        }

        return entryName
            .replace(
                '\\',
                '/'
            )
            .trim();
    }

    /**
     * 生成跨平台重复检查路径。
     */
    private String canonicalEntryName(
        String normalizedName
    ) {
        String value = normalizedName
            .replaceAll(
                "/+",
                "/"
            );

        while (
            value.endsWith("/")
                && value.length() > 1
        ) {
            value = value.substring(
                0,
                value.length() - 1
            );
        }

        return value
            .toLowerCase(Locale.ROOT);
    }

    /**
     * 判断是否包含点路径或父目录路径。
     */
    private boolean containsUnsafePathSegment(
        String normalizedName
    ) {
        String[] segments =
            normalizedName.split(
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
     * 计算有效路径层级。
     */
    private int calculatePathDepth(
        String normalizedName
    ) {
        int depth = 0;

        for (
            String segment :
            normalizedName.split(
                "/",
                -1
            )
        ) {
            if (!segment.isBlank()) {
                depth++;
            }
        }

        return depth;
    }

    /**
     * 判断路径是否包含 ASCII 控制字符。
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
     * 安全显示条目名称。
     */
    private String printableEntryName(
        String value
    ) {
        if (value == null) {
            return "";
        }

        StringBuilder result =
            new StringBuilder();

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
                result.append('?');
            } else {
                result.append(character);
            }
        }

        return result.toString();
    }

    /**
     * 判断 Unix 模式是否表示特殊文件。
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

        int unixMode = entry.getUnixMode();

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
     * 检查主题包中的文件类型和开发垃圾文件。
     *
     * @param entry ZIP 条目
     * @param normalizedName 标准化路径
     * @param issues 问题列表
     */
    private void inspectFilePolicy(
        ZipArchiveEntry entry,
        String normalizedName,
        List<ThemePackageIssue> issues
    ) {
        if (
            entry == null
                || entry.isDirectory()
                || normalizedName == null
                || normalizedName.isBlank()
        ) {
            return;
        }

        String fileName =
            entryFileName(
                normalizedName
            ).toLowerCase(Locale.ROOT);

        String extension =
            fileExtension(
                fileName
            );

        if (
            DANGEROUS_FILE_NAMES.contains(
                fileName
            )
                || DANGEROUS_FILE_EXTENSIONS
                    .contains(extension)
        ) {
            if (
                !hasIssueForEntry(
                    issues,
                    ThemePackageIssueCode
                        .DANGEROUS_FILE_TYPE,
                    normalizedName
                )
            ) {
                addError(
                    issues,
                    ThemePackageIssueCode
                        .DANGEROUS_FILE_TYPE,
                    normalizedName,
                    "主题包不允许包含可执行文件、"
                        + "服务端脚本或服务器配置文件："
                        + normalizedName
                );
            }
        }

        if (
            UNRECOMMENDED_FILE_NAMES.contains(
                fileName
            )
                || containsUnrecommendedPathSegment(
                    normalizedName
                )
        ) {
            if (
                !hasIssueForEntry(
                    issues,
                    ThemePackageIssueCode
                        .UNRECOMMENDED_FILE,
                    normalizedName
                )
            ) {
                addWarning(
                    issues,
                    ThemePackageIssueCode
                        .UNRECOMMENDED_FILE,
                    normalizedName,
                    "主题包包含不建议发布的系统文件、"
                        + "开发目录或依赖文件："
                        + normalizedName
                );
            }
        }
    }

    /**
     * 实际读取一个 ZIP 条目的内容，
     * 执行单文件、总大小和压缩比硬限制。
     *
     * <p>
     * 本方法只把字节读入固定大小缓冲区并丢弃，
     * 不会把文件解压到磁盘。
     * </p>
     *
     * @param zipFile ZIP 文件
     * @param entry ZIP 条目
     * @param normalizedName 标准化条目名
     * @param currentTotal 当前累计解压字节
     * @param issues 问题列表
     * @return 本条目实际读取字节数
     */
    private long inspectEntryContent(
        ZipFile zipFile,
        ZipArchiveEntry entry,
        String normalizedName,
        long currentTotal,
        List<ThemePackageIssue> issues
    ) {
        if (
            !isEntryContentReadable(
                zipFile,
                entry,
                normalizedName
            )
                || hasIssue(
                    issues,
                    ThemePackageIssueCode
                        .TOTAL_UNCOMPRESSED_SIZE_EXCEEDED
                )
        ) {
            return 0L;
        }

        long actualSize = 0L;
        boolean fullyRead = true;

        try (
            InputStream input =
                zipFile.getInputStream(entry)
        ) {
            byte[] buffer =
                new byte[READ_BUFFER_SIZE];

            int read;

            while (
                (read = input.read(buffer))
                    != -1
            ) {
                actualSize =
                    safeAdd(
                        actualSize,
                        read
                    );

                long resultingTotal =
                    safeAdd(
                        currentTotal,
                        actualSize
                    );

                boolean stopReading = false;

                if (
                    actualSize
                        > policy.maxSingleFileBytes()
                ) {
                    if (
                        !hasIssueForEntry(
                            issues,
                            ThemePackageIssueCode
                                .SINGLE_FILE_SIZE_EXCEEDED,
                            normalizedName
                        )
                    ) {
                        addError(
                            issues,
                            ThemePackageIssueCode
                                .SINGLE_FILE_SIZE_EXCEEDED,
                            normalizedName,
                            "主题文件实际解压大小超过限制："
                                + actualSize
                                + " 字节，最大允许 "
                                + policy.maxSingleFileBytes()
                                + " 字节。"
                        );
                    }

                    stopReading = true;
                }

                if (
                    resultingTotal
                        > policy
                            .maxTotalUncompressedBytes()
                ) {
                    if (
                        !hasIssue(
                            issues,
                            ThemePackageIssueCode
                                .TOTAL_UNCOMPRESSED_SIZE_EXCEEDED
                        )
                    ) {
                        addError(
                            issues,
                            ThemePackageIssueCode
                                .TOTAL_UNCOMPRESSED_SIZE_EXCEEDED,
                            normalizedName,
                            "主题包实际总解压大小超过限制："
                                + resultingTotal
                                + " 字节，最大允许 "
                                + policy
                                    .maxTotalUncompressedBytes()
                                + " 字节。"
                        );
                    }

                    stopReading = true;
                }

                if (stopReading) {
                    fullyRead = false;
                    break;
                }
            }
        } catch (IOException error) {
            fullyRead = false;

            if (
                !hasIssueForEntry(
                    issues,
                    ThemePackageIssueCode
                        .ENTRY_UNREADABLE,
                    normalizedName
                )
            ) {
                addError(
                    issues,
                    ThemePackageIssueCode
                        .ENTRY_UNREADABLE,
                    normalizedName,
                    "读取 ZIP 条目内容失败："
                        + safeMessage(error)
                );
            }
        }

        if (
            fullyRead
                && actualSize
                    >= COMPRESSION_RATIO_MIN_BYTES
        ) {
            inspectCompressionRatio(
                entry,
                normalizedName,
                actualSize,
                issues
            );
        }

        return actualSize;
    }

    /**
     * 检查实际解压字节和压缩字节的比例。
     *
     * @param entry ZIP 条目
     * @param normalizedName 标准化名称
     * @param actualSize 实际读取字节
     * @param issues 问题列表
     */
    private void inspectCompressionRatio(
        ZipArchiveEntry entry,
        String normalizedName,
        long actualSize,
        List<ThemePackageIssue> issues
    ) {
        long compressedSize =
            entry.getCompressedSize();

        double ratio =
            compressedSize <= 0L
                ? Double.POSITIVE_INFINITY
                : (double) actualSize
                    / (double) compressedSize;

        if (
            ratio
                <= policy.maxCompressionRatio()
        ) {
            return;
        }

        if (
            hasIssueForEntry(
                issues,
                ThemePackageIssueCode
                    .COMPRESSION_RATIO_EXCEEDED,
                normalizedName
            )
        ) {
            return;
        }

        addError(
            issues,
            ThemePackageIssueCode
                .COMPRESSION_RATIO_EXCEEDED,
            normalizedName,
            "主题文件压缩比异常，疑似压缩炸弹："
                + String.format(
                    Locale.ROOT,
                    "%.2f",
                    ratio
                )
                + " 倍，最大允许 "
                + policy.maxCompressionRatio()
                + " 倍。"
        );
    }

    /**
     * 判断条目能否进入实际内容读取阶段。
     */
    private boolean isEntryContentReadable(
        ZipFile zipFile,
        ZipArchiveEntry entry,
        String normalizedName
    ) {
        if (
            zipFile == null
                || entry == null
                || entry.isDirectory()
                || normalizedName == null
                || normalizedName.isBlank()
        ) {
            return false;
        }

        if (
            normalizedName.startsWith("/")
                || normalizedName.contains(":")
                || WINDOWS_DRIVE_PATTERN
                    .matcher(normalizedName)
                    .matches()
                || containsUnsafePathSegment(
                    normalizedName
                )
                || containsControlCharacter(
                    normalizedName
                )
        ) {
            return false;
        }

        if (
            !zipFile.canReadEntryData(entry)
                || entry.isUnixSymlink()
                || isSpecialUnixFile(entry)
        ) {
            return false;
        }

        return entry.getGeneralPurposeBit()
            == null
            || !entry
                .getGeneralPurposeBit()
                .usesEncryption();
    }

    /**
     * 获取条目文件名。
     */
    private String entryFileName(
        String normalizedName
    ) {
        if (
            normalizedName == null
                || normalizedName.isBlank()
        ) {
            return "";
        }

        int separator =
            normalizedName.lastIndexOf('/');

        if (separator < 0) {
            return normalizedName;
        }

        return normalizedName.substring(
            separator + 1
        );
    }

    /**
     * 获取文件扩展名。
     */
    private String fileExtension(
        String fileName
    ) {
        if (
            fileName == null
                || fileName.isBlank()
        ) {
            return "";
        }

        int dot =
            fileName.lastIndexOf('.');

        if (
            dot < 0
                || dot == fileName.length() - 1
        ) {
            return "";
        }

        return fileName.substring(dot);
    }

    /**
     * 判断路径中是否包含开发目录或系统目录。
     */
    private boolean containsUnrecommendedPathSegment(
        String normalizedName
    ) {
        String[] segments =
            normalizedName
                .toLowerCase(Locale.ROOT)
                .split(
                    "/",
                    -1
                );

        for (String segment : segments) {
            if (
                UNRECOMMENDED_PATH_SEGMENTS
                    .contains(segment)
            ) {
                return true;
            }
        }

        return false;
    }

    /**
     * 计算主题 ZIP 的 SHA-256。
     */
    private String calculatePackageSha256(
        Path packagePath,
        List<ThemePackageIssue> issues
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
                    new byte[READ_BUFFER_SIZE];

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
            addError(
                issues,
                ThemePackageIssueCode
                    .PACKAGE_HASH_FAILED,
                "",
                "计算主题包 SHA-256 失败："
                    + safeMessage(error)
            );

            return "";
        }
    }

    /**
     * 判断指定条目是否已经存在同类问题。
     */
    private boolean hasIssueForEntry(
        List<ThemePackageIssue> issues,
        ThemePackageIssueCode code,
        String entryName
    ) {
        String normalizedEntryName =
            entryName == null
                ? ""
                : entryName.trim();

        return issues
            .stream()
            .anyMatch(
                issue ->
                    issue.code() == code
                        && issue
                            .entryName()
                            .equals(
                                normalizedEntryName
                            )
            );
    }

    /**
     * 安全读取主题 ZIP 文件大小。
     */
    private long readPackageSizeSafely(
        Path packagePath,
        List<ThemePackageIssue> issues
    ) {
        try {
            return Files.size(packagePath);
        } catch (IOException error) {
            addError(
                issues,
                ThemePackageIssueCode
                    .ARCHIVE_OPEN_FAILED,
                "",
                "无法读取主题包大小："
                    + safeMessage(error)
            );

            return 0L;
        }
    }

    /**
     * 防止 long 加法溢出。
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
     * 判断问题列表是否包含指定代码。
     */
    private boolean hasIssue(
        List<ThemePackageIssue> issues,
        ThemePackageIssueCode code
    ) {
        return issues
            .stream()
            .anyMatch(
                issue ->
                    issue.code() == code
            );
    }

    /**
     * 创建不带 SHA-256 的兼容校验结果。
     *
     * 早期路径错误和文件不存在等情况，
     * 无法计算主题包哈希。
     */
    private ThemePackageValidationResult
        createResult(
            ThemeManifest manifest,
            String archiveRoot,
            long packageSize,
            int entryCount,
            long totalUncompressedSize,
            List<ThemePackageIssue> issues
        ) {

        return createResult(
            manifest,
            archiveRoot,
            packageSize,
            entryCount,
            totalUncompressedSize,
            "",
            issues
        );
    }

    /**
     * 创建完整校验结果。
     */
    private ThemePackageValidationResult
        createResult(
            ThemeManifest manifest,
            String archiveRoot,
            long packageSize,
            int entryCount,
            long totalUncompressedSize,
            String sha256,
            List<ThemePackageIssue> issues
        ) {

        return new ThemePackageValidationResult(
            manifest,
            archiveRoot,
            Math.max(
                packageSize,
                0L
            ),
            Math.max(
                entryCount,
                0
            ),
            Math.max(
                totalUncompressedSize,
                0L
            ),
            sha256,
            issues
        );
    }

    /**
     * 添加错误问题。
     */
    private void addError(
        List<ThemePackageIssue> issues,
        ThemePackageIssueCode code,
        String entryName,
        String message
    ) {
        issues.add(
            ThemePackageIssue.error(
                code,
                entryName,
                message
            )
        );
    }

    /**
     * 添加警告问题。
     */
    private void addWarning(
        List<ThemePackageIssue> issues,
        ThemePackageIssueCode code,
        String entryName,
        String message
    ) {
        issues.add(
            ThemePackageIssue.warning(
                code,
                entryName,
                message
            )
        );
    }

    /**
     * 获取安全异常说明。
     */
    private String safeMessage(
        Exception error
    ) {
        if (
            error == null
                || error.getMessage() == null
                || error.getMessage().isBlank()
        ) {
            return "未知主题包读取错误";
        }

        return error.getMessage();
    }

    /**
     * 主题清单候选条目。
     */
    private record ManifestCandidate(
        ZipArchiveEntry entry,
        String normalizedName
    ) {
    }

    /**
     * 主题清单读取结果。
     */
    private record ManifestReadResult(
        ThemeManifest manifest,
        String archiveRoot
    ) {

        /**
         * 创建空读取结果。
         */
        private static ManifestReadResult
            empty() {

            return new ManifestReadResult(
                null,
                ""
            );
        }
    }
}
