package com.aquafish.core.permalink;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 固定链接生成请求。
 *
 * 当前阶段：
 * Step 17-21-5：固定链接生成器 PermalinkBuilder。
 *
 * 设计说明：
 * 这里先不绑定数据库实体。
 *
 * 原因：
 * 1. CMS / BBS 表结构还没有完全定死；
 * 2. 固定链接属于底层能力，应该先独立；
 * 3. 后续 article、thread、forum 等实体确定后，再由业务模块组装这个请求；
 * 4. 这样可以避免模板层、业务层到处自己拼链接。
 *
 * 支持占位符：
 * {id}
 * {slug}
 * {key}
 * {fid}
 * {tid}
 * {uid}
 * {name}
 * {username}
 */
public record PermalinkBuildRequest(
    PermalinkTargetType type,
    Long id,
    String slug,
    String key,
    Long fid,
    Long tid,
    Long uid,
    String name,
    String username,
    Map<String, String> values
) {

    /**
     * 返回安全的目标类型。
     */
    public PermalinkTargetType safeType() {
        return type == null ? PermalinkTargetType.ARTICLE : type;
    }

    /**
     * 把请求转换成占位符参数。
     */
    public Map<String, String> toPlaceholderValues() {
        Map<String, String> result = new LinkedHashMap<>();

        putIfNotBlank(result, "id", id == null ? null : String.valueOf(id));
        putIfNotBlank(result, "slug", slug);
        putIfNotBlank(result, "key", key);
        putIfNotBlank(result, "fid", fid == null ? null : String.valueOf(fid));
        putIfNotBlank(result, "tid", tid == null ? null : String.valueOf(tid));
        putIfNotBlank(result, "uid", uid == null ? null : String.valueOf(uid));
        putIfNotBlank(result, "name", name);
        putIfNotBlank(result, "username", username);

        if (values != null && !values.isEmpty()) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                putIfNotBlank(result, entry.getKey(), entry.getValue());
            }
        }

        fillAliases(result);

        return result;
    }

    /**
     * 给常见字段补别名。
     *
     * 例如：
     * 1. forum 可能用 id，也可能用 fid；
     * 2. thread 可能用 id，也可能用 tid；
     * 3. user 可能用 name，也可能用 username。
     */
    private void fillAliases(Map<String, String> result) {
        if (!result.containsKey("id")) {
            if (result.containsKey("tid")) {
                result.put("id", result.get("tid"));
            } else if (result.containsKey("fid")) {
                result.put("id", result.get("fid"));
            } else if (result.containsKey("uid")) {
                result.put("id", result.get("uid"));
            }
        }

        if (!result.containsKey("fid") && result.containsKey("id") && safeType() == PermalinkTargetType.FORUM) {
            result.put("fid", result.get("id"));
        }

        if (!result.containsKey("tid") && result.containsKey("id") && safeType() == PermalinkTargetType.THREAD) {
            result.put("tid", result.get("id"));
        }

        if (!result.containsKey("uid") && result.containsKey("id") && safeType() == PermalinkTargetType.USER) {
            result.put("uid", result.get("id"));
        }

        if (!result.containsKey("key") && result.containsKey("slug")) {
            result.put("key", result.get("slug"));
        }

        if (!result.containsKey("slug") && result.containsKey("key")) {
            result.put("slug", result.get("key"));
        }

        if (!result.containsKey("name") && result.containsKey("username")) {
            result.put("name", result.get("username"));
        }

        if (!result.containsKey("username") && result.containsKey("name")) {
            result.put("username", result.get("name"));
        }
    }

    private static void putIfNotBlank(Map<String, String> result, String key, String value) {
        if (key == null || key.isBlank()) {
            return;
        }

        if (value == null || value.isBlank()) {
            return;
        }

        result.put(key.trim(), value.trim());
    }

    public static PermalinkBuildRequest article(long id, String slug) {
        return new PermalinkBuildRequest(
            PermalinkTargetType.ARTICLE,
            id,
            slug,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    public static PermalinkBuildRequest page(long id, String slug) {
        return new PermalinkBuildRequest(
            PermalinkTargetType.PAGE,
            id,
            slug,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    public static PermalinkBuildRequest category(long id, String key) {
        return new PermalinkBuildRequest(
            PermalinkTargetType.CATEGORY,
            id,
            key,
            key,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    public static PermalinkBuildRequest tag(long id, String key) {
        return new PermalinkBuildRequest(
            PermalinkTargetType.TAG,
            id,
            key,
            key,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    public static PermalinkBuildRequest forum(long fid, String key) {
        return new PermalinkBuildRequest(
            PermalinkTargetType.FORUM,
            fid,
            key,
            key,
            fid,
            null,
            null,
            null,
            null,
            null
        );
    }

    public static PermalinkBuildRequest thread(long tid, String slug) {
        return new PermalinkBuildRequest(
            PermalinkTargetType.THREAD,
            tid,
            slug,
            null,
            null,
            tid,
            null,
            null,
            null,
            null
        );
    }

    public static PermalinkBuildRequest user(long uid, String name) {
        return new PermalinkBuildRequest(
            PermalinkTargetType.USER,
            uid,
            name,
            null,
            null,
            null,
            uid,
            name,
            name,
            null
        );
    }
}
