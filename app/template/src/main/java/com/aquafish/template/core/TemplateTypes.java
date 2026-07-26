package com.aquafish.template.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Aquafish 内置模板类型集合。
 *
 * 当前阶段：
 * Step 17-20-2：先定义第一批内置模板类型。
 *
 * 注意：
 * 这些模板类型属于 template 核心层，不属于 forum/content/user 某个单独业务模块。
 *
 * 业务模块后续应该使用：
 * TemplateTypes.THREAD
 * TemplateTypes.POST
 * TemplateTypes.INDEX
 *
 * 而不是自己到处写：
 * "forum/viewthread.html"
 * "content/view.html"
 */
public final class TemplateTypes {

    private TemplateTypes() {
    }

    public static final TemplateType INDEX = new TemplateType(
        "index",
        "index.html",
        "首页",
        "网站首页模板。"
    );

    public static final TemplateType POST = new TemplateType(
        "post",
        "content/view.html",
        "文章详情页",
        "CMS 文章详情页模板。"
    );

    public static final TemplateType PAGE = new TemplateType(
        "page",
        "content/page.html",
        "单页",
        "CMS 单页面模板。"
    );

    public static final TemplateType CATEGORY = new TemplateType(
        "category",
        "content/list.html",
        "分类页",
        "CMS 分类列表页模板。"
    );

    public static final TemplateType TAG = new TemplateType(
        "tag",
        "content/tag.html",
        "标签页",
        "CMS 标签列表页模板。"
    );

    public static final TemplateType ARCHIVES = new TemplateType(
        "archives",
        "content/archives.html",
        "归档页",
        "CMS 归档页模板。"
    );

    public static final TemplateType SEARCH = new TemplateType(
        "search",
        "search.html",
        "搜索页",
        "站内搜索结果页模板。"
    );

    public static final TemplateType ERROR = new TemplateType(
        "error",
        "error.html",
        "错误页",
        "前台错误提示页模板。"
    );

    public static final TemplateType FORUM = new TemplateType(
        "forum",
        "forum/index.html",
        "论坛首页",
        "BBS 论坛首页模板。"
    );

    public static final TemplateType FORUM_LIST = new TemplateType(
        "forum-list",
        "forum/list.html",
        "板块列表页",
        "BBS 板块列表模板。"
    );

    public static final TemplateType FORUM_DETAIL = new TemplateType(
        "forum-detail",
        "forum/forumdisplay.html",
        "板块详情页",
        "BBS 单个板块详情模板。"
    );

    public static final TemplateType THREAD = new TemplateType(
        "thread",
        "forum/viewthread.html",
        "帖子详情页",
        "BBS 帖子详情页模板。"
    );

    public static final TemplateType THREAD_LIST = new TemplateType(
        "thread-list",
        "forum/thread-list.html",
        "帖子列表页",
        "BBS 帖子列表模板。"
    );

    public static final TemplateType USER_HOME = new TemplateType(
        "user-home",
        "member/home.html",
        "用户主页",
        "用户个人主页模板。"
    );

    public static final TemplateType LOGIN = new TemplateType(
        "login",
        "member/login.html",
        "登录页",
        "前台会员登录页模板。"
    );

    public static final TemplateType REGISTER = new TemplateType(
        "register",
        "member/register.html",
        "注册页",
        "前台会员注册页模板。"
    );

    /**
     * 内置模板类型列表。
     *
     * 使用 List.of 保证不可变，避免运行时被随便改掉。
     */
    private static final List<TemplateType> BUILT_IN_TYPES = List.of(
        INDEX,
        POST,
        PAGE,
        CATEGORY,
        TAG,
        ARCHIVES,
        SEARCH,
        ERROR,
        FORUM,
        FORUM_LIST,
        FORUM_DETAIL,
        THREAD,
        THREAD_LIST,
        USER_HOME,
        LOGIN,
        REGISTER
    );

    /**
     * 按 key 建立索引。
     *
     * 这样业务模块或诊断接口可以通过 key 快速找到模板类型。
     */
    private static final Map<String, TemplateType> BY_KEY = buildIndex();

    /**
     * 获取所有内置模板类型。
     *
     * @return 内置模板类型列表
     */
    public static List<TemplateType> all() {
        return BUILT_IN_TYPES;
    }

    /**
     * 根据 key 查找模板类型。
     *
     * @param key 模板类型 key
     * @return 模板类型
     */
    public static Optional<TemplateType> find(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(BY_KEY.get(key.trim().toLowerCase(Locale.ROOT)));
    }

    /**
     * 根据 key 获取模板类型。
     *
     * 如果找不到，直接抛异常。
     *
     * 后续业务模块如果传错模板类型，应该尽早失败，而不是静默找不到模板。
     *
     * @param key 模板类型 key
     * @return 模板类型
     */
    public static TemplateType require(String key) {
        return find(key).orElseThrow(() -> new IllegalArgumentException("未知模板类型：" + key));
    }

    private static Map<String, TemplateType> buildIndex() {
        Map<String, TemplateType> result = new LinkedHashMap<>();

        for (TemplateType type : BUILT_IN_TYPES) {
            if (result.containsKey(type.key())) {
                throw new IllegalStateException("重复的模板类型 key：" + type.key());
            }

            result.put(type.key(), type);
        }

        return Map.copyOf(result);
    }
}