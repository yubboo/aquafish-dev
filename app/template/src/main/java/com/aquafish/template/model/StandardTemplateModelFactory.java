package com.aquafish.template.model;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aquafish 标准模板模型工厂。
 *
 * 当前阶段：
 * Step 17-20-7：CMS / BBS 模板变量标准化第一版。
 *
 * 当前作用：
 * 1. 统一 CMS 模板变量；
 * 2. 统一 BBS 模板变量；
 * 3. 给开发诊断接口提供标准示例数据；
 * 4. 后续给 content/forum/user 模块做 ViewModel 参考。
 *
 * 注意：
 * 这个类当前先返回 Map。
 *
 * 原因：
 * 1. Thymeleaf 对 Map 支持很好；
 * 2. 模板开发阶段更灵活；
 * 3. 后续数据库模型没定死前，不急着绑定实体类；
 * 4. 等真实 CMS / BBS 表设计完成后，再逐步替换成强类型 ViewModel。
 *
 * 重要说明：
 * 这里不要使用超长 Map.of(...)。
 * Java 的 Map.of(...) 对键值对数量有限制，超过后会编译失败。
 * 所以本文件统一使用 LinkedHashMap + put，避免后续继续踩坑。
 */
public final class StandardTemplateModelFactory {

    private StandardTemplateModelFactory() {
    }

    /**
     * 创建开发阶段标准示例模型。
     *
     * @param type 当前模板类型 key
     * @return 标准模板模型
     */
    public static Map<String, Object> demoModel(String type) {
        Map<String, Object> model = new LinkedHashMap<>();

        putCommonModel(model, type);
        putCmsModel(model);
        putBbsModel(model);
        putUserModel(model);

        return model;
    }

    /**
     * 放入所有前台页面通用变量。
     *
     * @param model 模板模型
     * @param type 当前模板类型
     */
    private static void putCommonModel(Map<String, Object> model, String type) {
        model.put(TemplateModelKeys.SITE, site());
        model.put(TemplateModelKeys.SEO, seo(type));
        model.put(TemplateModelKeys.BREADCRUMBS, breadcrumbs(type));
        model.put(TemplateModelKeys.PAGINATION, pagination());
        model.put(TemplateModelKeys.KEYWORD, "Aquafish");
        model.put(TemplateModelKeys.RENDER_TYPE, type);

        /*
         * 兼容早期模板里的 pageTitle / pageSummary。
         *
         * 后续正式模板推荐使用：
         * seo.title
         * seo.description
         */
        model.put("pageTitle", "Aquafish 页面示例");
        model.put("pageSummary", "这是 Aquafish 开发阶段模板渲染示例页面。");
    }

    /**
     * 放入 CMS 博客相关变量。
     *
     * @param model 模板模型
     */
    private static void putCmsModel(Map<String, Object> model) {
        Map<String, Object> article = article();
        List<Map<String, Object>> articles = articles();

        model.put(TemplateModelKeys.ARTICLE, article);
        model.put(TemplateModelKeys.ARTICLES, articles);

        /*
         * 兼容早期模板里的 post / posts。
         *
         * 后续 CMS 正式模板推荐使用：
         * article
         * articles
         */
        model.put("post", article);
        model.put("posts", articles);
        model.put("postTitle", article.get("title"));
        model.put("postSummary", article.get("summary"));

        model.put(TemplateModelKeys.PAGE, page());
        model.put(TemplateModelKeys.CATEGORY, category());
        model.put(TemplateModelKeys.TAG, tag());
    }

    /**
     * 放入 BBS 论坛相关变量。
     *
     * @param model 模板模型
     */
    private static void putBbsModel(Map<String, Object> model) {
        Map<String, Object> forum = forum();
        Map<String, Object> thread = thread();
        List<Map<String, Object>> threads = threads();

        model.put(TemplateModelKeys.FORUM, forum);
        model.put(TemplateModelKeys.FORUMS, forums());

        model.put(TemplateModelKeys.THREAD, thread);
        model.put(TemplateModelKeys.THREADS, threads);
        model.put(TemplateModelKeys.REPLIES, replies());

        /*
         * 兼容早期模板里的 threadTitle / threadSummary。
         *
         * 后续 BBS 正式模板推荐使用：
         * thread.subject
         * thread.content
         */
        model.put("threadTitle", thread.get("subject"));
        model.put("threadSummary", thread.get("summary"));
    }

    /**
     * 放入用户相关变量。
     *
     * @param model 模板模型
     */
    private static void putUserModel(Map<String, Object> model) {
        Map<String, Object> author = author();

        model.put(TemplateModelKeys.AUTHOR, author);
        model.put("user", author);

        model.put(TemplateModelKeys.STATS, stats());

        model.put("errorTitle", "开发阶段错误页");
        model.put("errorMessage", "这是用于验证 error 模板的示例错误信息。");
    }

    /**
     * 站点信息。
     *
     * 模板推荐使用：
     * site.name
     * site.description
     * site.url
     *
     * @return 站点信息
     */
    private static Map<String, Object> site() {
        Map<String, Object> site = new LinkedHashMap<>();

        site.put("name", "Aquafish");
        site.put("description", "CMS + BBS + AI 平台");
        site.put("url", "http://127.0.0.1:8520");
        site.put("logo", "");
        site.put("locale", "zh-CN");

        return site;
    }

    /**
     * SEO 信息。
     *
     * 模板推荐使用：
     * seo.title
     * seo.description
     * seo.keywords
     * seo.canonicalUrl
     *
     * @param type 当前模板类型
     * @return SEO 信息
     */
    private static Map<String, Object> seo(String type) {
        Map<String, Object> seo = new LinkedHashMap<>();

        seo.put("title", "Aquafish " + type + " 页面");
        seo.put("description", "Aquafish CMS + BBS + AI 平台开发阶段模板渲染页面。");
        seo.put("keywords", "Aquafish,CMS,BBS,AI,论坛,博客");
        seo.put("canonicalUrl", "http://127.0.0.1:8520/");

        return seo;
    }

    /**
     * 面包屑导航。
     *
     * 模板推荐使用：
     * breadcrumbs
     *
     * @param type 当前模板类型
     * @return 面包屑列表
     */
    private static List<Map<String, Object>> breadcrumbs(String type) {
        return List.of(
            breadcrumb("首页", "/"),
            breadcrumb(type, "#")
        );
    }

    /**
     * 创建单个面包屑。
     *
     * @param name 名称
     * @param url 链接
     * @return 面包屑
     */
    private static Map<String, Object> breadcrumb(String name, String url) {
        Map<String, Object> breadcrumb = new LinkedHashMap<>();

        breadcrumb.put("name", name);
        breadcrumb.put("url", url);

        return breadcrumb;
    }

    /**
     * 分页信息。
     *
     * 模板推荐使用：
     * pagination.page
     * pagination.pageSize
     * pagination.total
     * pagination.totalPages
     *
     * @return 分页信息
     */
    private static Map<String, Object> pagination() {
        Map<String, Object> pagination = new LinkedHashMap<>();

        pagination.put("page", 1);
        pagination.put("pageSize", 20);
        pagination.put("total", 46);
        pagination.put("totalPages", 3);
        pagination.put("hasPrevious", false);
        pagination.put("hasNext", true);

        return pagination;
    }

    /**
     * 作者信息。
     *
     * 模板推荐使用：
     * author.displayName
     * author.avatar
     *
     * @return 作者信息
     */
    private static Map<String, Object> author() {
        Map<String, Object> author = new LinkedHashMap<>();

        author.put("id", 1);
        author.put("username", "admin");
        author.put("displayName", "Aquafish 管理员");
        author.put("avatar", "");
        author.put("bio", "这是 Aquafish 开发阶段示例作者。");
        author.put("joinedAt", "2026-07-10 08:00:00");

        return author;
    }

    /**
     * 页面统计信息。
     *
     * 模板推荐使用：
     * stats.views
     * stats.likes
     * stats.comments
     *
     * @return 统计信息
     */
    private static Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        stats.put("views", 1288);
        stats.put("likes", 96);
        stats.put("comments", 18);
        stats.put("replies", 32);
        stats.put("threads", 12);
        stats.put("articles", 8);

        return stats;
    }

    /**
     * CMS 文章详情。
     *
     * 模板推荐使用：
     * article.id
     * article.slug
     * article.title
     * article.summary
     * article.content
     * article.authorName
     *
     * @return 文章详情
     */
    private static Map<String, Object> article() {
        Map<String, Object> article = new LinkedHashMap<>();

        article.put("id", 1);
        article.put("slug", "aquafish-demo-article");
        article.put("title", "Aquafish 文章详情示例");
        article.put("summary", "这里后续会展示 CMS 文章正文、分类、标签和评论。");
        article.put("content", "这是 CMS 博客文章详情页的示例正文。后续会接入真实文章表、分类、标签、评论、SEO 和插件挂载点。");
        article.put("cover", "");
        article.put("authorName", "Aquafish 管理员");
        article.put("publishedAt", "2026-07-10 08:00:00");
        article.put("updatedAt", "2026-07-10 08:00:00");
        article.put("categoryName", "开发记录");
        article.put("tags", List.of("CMS", "BBS", "AI"));

        return article;
    }

    /**
     * CMS 文章列表。
     *
     * 模板推荐使用：
     * articles
     *
     * 兼容旧模板：
     * posts
     *
     * @return 文章列表
     */
    private static List<Map<String, Object>> articles() {
        return List.of(
            articleListItem(
                1,
                "first-demo-article",
                "第一篇 Aquafish 示例文章",
                "这是用于测试 CMS 列表模板的文章摘要。",
                "/content/1",
                "Aquafish 管理员",
                "2026-07-10 08:00:00"
            ),
            articleListItem(
                2,
                "second-demo-article",
                "第二篇 Aquafish 示例文章",
                "这是用于测试 CMS 列表模板的文章摘要。",
                "/content/2",
                "Aquafish 管理员",
                "2026-07-10 08:10:00"
            )
        );
    }

    /**
     * CMS 文章列表项。
     *
     * @return 文章列表项
     */
    private static Map<String, Object> articleListItem(
        int id,
        String slug,
        String title,
        String summary,
        String url,
        String authorName,
        String publishedAt
    ) {
        Map<String, Object> article = new LinkedHashMap<>();

        article.put("id", id);
        article.put("slug", slug);
        article.put("title", title);
        article.put("summary", summary);
        article.put("url", url);
        article.put("authorName", authorName);
        article.put("publishedAt", publishedAt);

        return article;
    }

    /**
     * CMS 单页。
     *
     * 模板推荐使用：
     * page.title
     * page.summary
     * page.content
     *
     * @return 单页信息
     */
    private static Map<String, Object> page() {
        Map<String, Object> page = new LinkedHashMap<>();

        page.put("id", 1);
        page.put("slug", "about");
        page.put("title", "Aquafish 单页示例");
        page.put("summary", "这是开发阶段用于验证 page 模板渲染的示例数据。");
        page.put("content", "这里后续可以用于关于我们、用户协议、隐私政策、帮助中心等单页内容。");

        return page;
    }

    /**
     * CMS 分类。
     *
     * 模板推荐使用：
     * category.name
     * category.description
     *
     * @return 分类信息
     */
    private static Map<String, Object> category() {
        Map<String, Object> category = new LinkedHashMap<>();

        category.put("id", 1);
        category.put("slug", "dev-log");
        category.put("name", "开发记录");
        category.put("description", "这里展示 Aquafish CMS 分类页。");

        return category;
    }

    /**
     * CMS 标签。
     *
     * 模板推荐使用：
     * tag.name
     * tag.description
     *
     * @return 标签信息
     */
    private static Map<String, Object> tag() {
        Map<String, Object> tag = new LinkedHashMap<>();

        tag.put("id", 1);
        tag.put("slug", "aquafish");
        tag.put("name", "Aquafish");
        tag.put("description", "这里展示 Aquafish CMS 标签页。");

        return tag;
    }

    /**
     * BBS 当前板块。
     *
     * 模板推荐使用：
     * forum.name
     * forum.description
     *
     * @return 板块信息
     */
    private static Map<String, Object> forum() {
        Map<String, Object> forum = new LinkedHashMap<>();

        forum.put("id", 1);
        forum.put("slug", "general");
        forum.put("name", "Aquafish 综合讨论区");
        forum.put("description", "这里展示 Aquafish 论坛板块说明。");
        forum.put("threadCount", 128);
        forum.put("postCount", 689);
        forum.put("todayPostCount", 18);

        return forum;
    }

    /**
     * BBS 板块列表。
     *
     * 模板推荐使用：
     * forums
     *
     * @return 板块列表
     */
    private static List<Map<String, Object>> forums() {
        return List.of(
            forumListItem(
                1,
                "general",
                "综合讨论区",
                "站点公告、闲聊、反馈和综合讨论。",
                128,
                689
            ),
            forumListItem(
                2,
                "cms",
                "CMS 博客交流",
                "文章、分类、标签、SEO、主题展示。",
                56,
                218
            ),
            forumListItem(
                3,
                "bbs",
                "BBS 论坛交流",
                "板块、帖子、回复、权限、积分。",
                72,
                331
            )
        );
    }

    /**
     * BBS 板块列表项。
     *
     * @return 板块列表项
     */
    private static Map<String, Object> forumListItem(
        int id,
        String slug,
        String name,
        String description,
        int threadCount,
        int postCount
    ) {
        Map<String, Object> forum = new LinkedHashMap<>();

        forum.put("id", id);
        forum.put("slug", slug);
        forum.put("name", name);
        forum.put("description", description);
        forum.put("threadCount", threadCount);
        forum.put("postCount", postCount);

        return forum;
    }

    /**
     * BBS 帖子详情。
     *
     * 模板推荐使用：
     * thread.subject
     * thread.content
     * thread.authorName
     *
     * @return 帖子详情
     */
    private static Map<String, Object> thread() {
        Map<String, Object> thread = new LinkedHashMap<>();

        thread.put("id", 1);
        thread.put("slug", "aquafish-demo-thread");
        thread.put("subject", "Aquafish 帖子详情示例");
        thread.put("title", "Aquafish 帖子详情示例");
        thread.put("summary", "这里后续会展示帖子正文、回复、作者、点赞、收藏等信息。");
        thread.put("content", "这是 BBS 帖子详情页的示例正文。后续会接入真实帖子表、回复表、板块权限、置顶、精华、浏览量和插件挂载点。");
        thread.put("authorName", "Aquafish 管理员");
        thread.put("createdAt", "2026-07-10 08:00:00");
        thread.put("updatedAt", "2026-07-10 08:00:00");
        thread.put("replyCount", 32);
        thread.put("viewCount", 1288);
        thread.put("isTop", true);
        thread.put("isDigest", false);
        thread.put("isLocked", false);

        return thread;
    }

    /**
     * BBS 帖子列表。
     *
     * 模板推荐使用：
     * threads
     *
     * @return 帖子列表
     */
    private static List<Map<String, Object>> threads() {
        return List.of(
            threadListItem(
                1,
                "first-demo-thread",
                "第一个 Aquafish 示例帖子",
                "这是用于测试论坛列表模板的帖子摘要。",
                "/forum/thread/1",
                "Aquafish 管理员",
                12,
                256,
                "2026-07-10 08:00:00"
            ),
            threadListItem(
                2,
                "second-demo-thread",
                "第二个 Aquafish 示例帖子",
                "这是用于测试论坛列表模板的帖子摘要。",
                "/forum/thread/2",
                "Aquafish 用户",
                8,
                188,
                "2026-07-10 08:15:00"
            )
        );
    }

    /**
     * BBS 帖子列表项。
     *
     * @return 帖子列表项
     */
    private static Map<String, Object> threadListItem(
        int id,
        String slug,
        String subject,
        String summary,
        String url,
        String authorName,
        int replyCount,
        int viewCount,
        String createdAt
    ) {
        Map<String, Object> thread = new LinkedHashMap<>();

        thread.put("id", id);
        thread.put("slug", slug);
        thread.put("subject", subject);
        thread.put("title", subject);
        thread.put("summary", summary);
        thread.put("url", url);
        thread.put("authorName", authorName);
        thread.put("replyCount", replyCount);
        thread.put("viewCount", viewCount);
        thread.put("createdAt", createdAt);

        return thread;
    }

    /**
     * BBS 回复列表。
     *
     * 模板推荐使用：
     * replies
     *
     * @return 回复列表
     */
    private static List<Map<String, Object>> replies() {
        return List.of(
            replyListItem(
                1,
                1,
                "这是第一条示例回复。",
                "Aquafish 用户 A",
                LocalDateTime.now().minusMinutes(20).toString()
            ),
            replyListItem(
                2,
                2,
                "这是第二条示例回复。",
                "Aquafish 用户 B",
                LocalDateTime.now().minusMinutes(10).toString()
            )
        );
    }

    /**
     * BBS 回复列表项。
     *
     * @return 回复列表项
     */
    private static Map<String, Object> replyListItem(
        int id,
        int floor,
        String content,
        String authorName,
        String createdAt
    ) {
        Map<String, Object> reply = new LinkedHashMap<>();

        reply.put("id", id);
        reply.put("floor", floor);
        reply.put("content", content);
        reply.put("authorName", authorName);
        reply.put("createdAt", createdAt);

        return reply;
    }
}
