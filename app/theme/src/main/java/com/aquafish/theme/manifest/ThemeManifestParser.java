package com.aquafish.theme.manifest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/**
 * Aquafish 正式 theme.yaml 解析器。
 *
 * <p>
 * 本解析器使用 Jackson YAML，
 * 替代旧 ThemeScanner 中逐行拆分字符串的简易实现。
 * </p>
 *
 * <p>当前兼容：</p>
 *
 * <ul>
 *     <li>id 作为正式主题唯一标识；</li>
 *     <li>旧 name 字段可以作为 id 的后备来源；</li>
 *     <li>author 可以是字符串或包含 name、url 的对象；</li>
 *     <li>缺少 engine 的旧主题默认使用 thymeleaf；</li>
 *     <li>支持 apiVersion 和 api-version 两种写法；</li>
 *     <li>支持 requires.aquafish 和 requires.java。</li>
 * </ul>
 */
@Component
public class ThemeManifestParser {

    /**
     * YAML ObjectMapper。
     */
    private final ObjectMapper objectMapper;

    /**
     * 创建默认主题清单解析器。
     */
    public ThemeManifestParser() {
        this(
            new ObjectMapper(
                new YAMLFactory()
            )
        );
    }

    /**
     * 创建可注入 ObjectMapper 的解析器。
     *
     * 该构造方法主要方便测试和未来统一 Jackson 配置。
     *
     * @param objectMapper YAML ObjectMapper
     */
    ThemeManifestParser(
        ObjectMapper objectMapper
    ) {
        if (objectMapper == null) {
            throw new IllegalArgumentException(
                "YAML ObjectMapper 不能为空。"
            );
        }

        this.objectMapper = objectMapper;
    }

    /**
     * 从 theme.yaml 文件解析主题清单。
     *
     * @param themeYamlFile theme.yaml 路径
     * @return 标准化主题清单
     */
    public ThemeManifest parse(
        Path themeYamlFile
    ) {
        if (themeYamlFile == null) {
            throw new ThemeManifestException(
                "theme.yaml 路径不能为空。"
            );
        }

        Path normalizedFile =
            themeYamlFile
                .toAbsolutePath()
                .normalize();

        if (!Files.isRegularFile(normalizedFile)) {
            throw new ThemeManifestException(
                "theme.yaml 文件不存在："
                    + normalizedFile
            );
        }

        final String content;

        try {
            content = Files.readString(
                normalizedFile,
                StandardCharsets.UTF_8
            );
        } catch (IOException error) {
            throw new ThemeManifestException(
                "读取 theme.yaml 失败："
                    + normalizedFile,
                error
            );
        }

        try {
            return parse(content);
        } catch (ThemeManifestException error) {
            throw new ThemeManifestException(
                "解析 theme.yaml 失败："
                    + normalizedFile
                    + "。"
                    + error.getMessage(),
                error
            );
        }
    }

    /**
     * 从 YAML 文本解析主题清单。
     *
     * @param yamlContent YAML 内容
     * @return 标准化主题清单
     */
    public ThemeManifest parse(
        String yamlContent
    ) {
        if (
            yamlContent == null
                || yamlContent.isBlank()
        ) {
            throw new ThemeManifestException(
                "theme.yaml 内容不能为空。"
            );
        }

        final JsonNode root;

        try {
            root = objectMapper.readTree(
                yamlContent
            );
        } catch (JsonProcessingException error) {
            throw new ThemeManifestException(
                "theme.yaml 语法无效："
                    + safeMessage(error),
                error
            );
        }

        if (
            root == null
                || !root.isObject()
        ) {
            throw new ThemeManifestException(
                "theme.yaml 根节点必须是对象。"
            );
        }

        String id = firstNonBlank(
            readScalar(
                root,
                "id"
            ),
            readScalar(
                root,
                "name"
            )
        );

        if (id == null) {
            throw new ThemeManifestException(
                "theme.yaml 缺少主题唯一标识 id。"
            );
        }

        String title = firstNonBlank(
            readScalar(
                root,
                "title"
            ),
            readScalar(
                root,
                "displayName"
            ),
            readScalar(
                root,
                "display-name"
            ),
            id
        );

        String version = firstNonBlank(
            readScalar(
                root,
                "version"
            ),
            "0.0.0"
        );

        String engine = firstNonBlank(
            readScalar(
                root,
                "engine"
            ),
            "thymeleaf"
        );

        ThemeAuthor author =
            readAuthor(root);

        String parent = firstNonBlank(
            readScalar(
                root,
                "parent"
            )
        );

        String description = firstNonBlank(
            readScalar(
                root,
                "description"
            ),
            ""
        );

        int apiVersion = readInteger(
            root,
            1,
            "apiVersion",
            "api-version"
        );

        ThemeRequirements requirements =
            readRequirements(root);

        try {
            return new ThemeManifest(
                id,
                title,
                version,
                engine,
                author,
                parent,
                description,
                apiVersion,
                requirements
            );
        } catch (IllegalArgumentException error) {
            throw new ThemeManifestException(
                "theme.yaml 字段校验失败："
                    + error.getMessage(),
                error
            );
        }
    }

    /**
     * 读取作者信息。
     *
     * author 可以写成：
     *
     * <pre>
     * author: Aquafish Team
     * </pre>
     *
     * 或者：
     *
     * <pre>
     * author:
     *   name: Aquafish Team
     *   url: https://example.com
     * </pre>
     *
     * @param root YAML 根节点
     * @return 作者信息
     */
    private ThemeAuthor readAuthor(
        JsonNode root
    ) {
        JsonNode authorNode =
            root.get("author");

        if (
            authorNode == null
                || authorNode.isNull()
        ) {
            return ThemeAuthor.empty();
        }

        if (authorNode.isValueNode()) {
            return new ThemeAuthor(
                authorNode.asText(),
                ""
            );
        }

        if (!authorNode.isObject()) {
            throw new ThemeManifestException(
                "theme.yaml 的 author 必须是字符串或对象。"
            );
        }

        return new ThemeAuthor(
            firstNonBlank(
                readScalar(
                    authorNode,
                    "name"
                ),
                ""
            ),
            firstNonBlank(
                readScalar(
                    authorNode,
                    "url"
                ),
                readScalar(
                    authorNode,
                    "website"
                ),
                ""
            )
        );
    }

    /**
     * 读取运行版本要求。
     *
     * @param root YAML 根节点
     * @return 兼容要求
     */
    private ThemeRequirements readRequirements(
        JsonNode root
    ) {
        JsonNode requiresNode =
            root.get("requires");

        if (
            requiresNode == null
                || requiresNode.isNull()
        ) {
            return ThemeRequirements.empty();
        }

        if (!requiresNode.isObject()) {
            throw new ThemeManifestException(
                "theme.yaml 的 requires 必须是对象。"
            );
        }

        return new ThemeRequirements(
            firstNonBlank(
                readScalar(
                    requiresNode,
                    "aquafish"
                ),
                ""
            ),
            firstNonBlank(
                readScalar(
                    requiresNode,
                    "java"
                ),
                ""
            )
        );
    }

    /**
     * 读取整数配置。
     *
     * @param root YAML 根节点
     * @param defaultValue 默认值
     * @param fieldNames 可兼容字段名
     * @return 整数值
     */
    private int readInteger(
        JsonNode root,
        int defaultValue,
        String... fieldNames
    ) {
        for (String fieldName : fieldNames) {
            JsonNode node =
                root.get(fieldName);

            if (
                node == null
                    || node.isNull()
            ) {
                continue;
            }

            if (node.isInt()) {
                return node.intValue();
            }

            if (node.isTextual()) {
                String text = node
                    .asText()
                    .trim();

                try {
                    return Integer.parseInt(text);
                } catch (NumberFormatException error) {
                    throw new ThemeManifestException(
                        "theme.yaml 字段 "
                            + fieldName
                            + " 必须是整数。",
                        error
                    );
                }
            }

            throw new ThemeManifestException(
                "theme.yaml 字段 "
                    + fieldName
                    + " 必须是整数。"
            );
        }

        return defaultValue;
    }

    /**
     * 读取标量字段。
     *
     * @param objectNode 对象节点
     * @param fieldName 字段名
     * @return 字段文本，不存在时返回 null
     */
    private String readScalar(
        JsonNode objectNode,
        String fieldName
    ) {
        JsonNode node =
            objectNode.get(fieldName);

        if (
            node == null
                || node.isNull()
        ) {
            return null;
        }

        if (!node.isValueNode()) {
            throw new ThemeManifestException(
                "theme.yaml 字段 "
                    + fieldName
                    + " 必须是普通值。"
            );
        }

        String value = node
            .asText()
            .trim();

        return value.isBlank()
            ? null
            : value;
    }

    /**
     * 返回第一个非空文本。
     *
     * @param values 候选值
     * @return 第一个有效值
     */
    private String firstNonBlank(
        String... values
    ) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (
                value != null
                    && !value.isBlank()
            ) {
                return value.trim();
            }
        }

        return null;
    }

    /**
     * 获取不为空的异常说明。
     *
     * @param error 异常
     * @return 安全错误文字
     */
    private String safeMessage(
        Exception error
    ) {
        if (
            error == null
                || error.getMessage() == null
                || error.getMessage().isBlank()
        ) {
            return "未知 YAML 解析错误";
        }

        return error.getMessage();
    }
}
