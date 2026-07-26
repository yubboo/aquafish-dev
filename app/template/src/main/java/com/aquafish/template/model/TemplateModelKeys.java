package com.aquafish.template.model;

/**
 * Aquafish 前台模板变量 key 统一定义。
 *
 * 当前阶段：
 * Step 17-20-7：CMS / BBS 模板变量标准化第一版。
 *
 * 为什么需要这个类？
 *
 * 因为主题模板里不能今天用 article，明天用 post，
 * 今天用 thread.subject，明天用 thread.title。
 *
 * CMS 和 BBS 是 Aquafish 的两大核心。
 * 所以前台模板变量必须提前统一。
 *
 * 后续 forum/content/user 模块组装 ViewModel 时，
 * 应该优先使用这里定义好的 key。
 */
public final class TemplateModelKeys {

    private TemplateModelKeys() {
    }

    /**
     * 站点信息。
     *
     * 模板使用：
     * site.name
     * site.description
     * site.url
     */
    public static final String SITE = "site";

    /**
     * SEO 信息。
     *
     * 模板使用：
     * seo.title
     * seo.description
     * seo.keywords
     */
    public static final String SEO = "seo";

    /**
     * 面包屑导航。
     *
     * 模板使用：
     * breadcrumbs
     */
    public static final String BREADCRUMBS = "breadcrumbs";

    /**
     * 分页信息。
     *
     * 模板使用：
     * pagination.page
     * pagination.pageSize
     * pagination.total
     * pagination.totalPages
     */
    public static final String PAGINATION = "pagination";

    /**
     * CMS 文章详情。
     *
     * 模板使用：
     * article.title
     * article.summary
     * article.content
     * article.authorName
     */
    public static final String ARTICLE = "article";

    /**
     * CMS 单页。
     *
     * 模板使用：
     * page.title
     * page.summary
     * page.content
     */
    public static final String PAGE = "page";

    /**
     * CMS 分类。
     *
     * 模板使用：
     * category.name
     * category.description
     */
    public static final String CATEGORY = "category";

    /**
     * CMS 标签。
     *
     * 模板使用：
     * tag.name
     * tag.description
     */
    public static final String TAG = "tag";

    /**
     * CMS 文章列表。
     *
     * 模板使用：
     * articles
     *
     * 注意：
     * 后续正式模板推荐使用 articles。
     * 早期兼容 posts，避免旧模板马上失效。
     */
    public static final String ARTICLES = "articles";

    /**
     * 兼容旧模板的文章列表 key。
     *
     * 模板使用：
     * posts
     */
    public static final String POSTS = "posts";

    /**
     * BBS 板块。
     *
     * 模板使用：
     * forum.name
     * forum.description
     */
    public static final String FORUM = "forum";

    /**
     * BBS 板块列表。
     *
     * 模板使用：
     * forums
     */
    public static final String FORUMS = "forums";

    /**
     * BBS 帖子详情。
     *
     * 模板使用：
     * thread.subject
     * thread.content
     * thread.authorName
     */
    public static final String THREAD = "thread";

    /**
     * BBS 帖子列表。
     *
     * 模板使用：
     * threads
     */
    public static final String THREADS = "threads";

    /**
     * BBS 回复列表。
     *
     * 模板使用：
     * replies
     */
    public static final String REPLIES = "replies";

    /**
     * 当前页面作者。
     *
     * 模板使用：
     * author.displayName
     * author.avatar
     */
    public static final String AUTHOR = "author";

    /**
     * 当前页面统计。
     *
     * 模板使用：
     * stats.views
     * stats.likes
     * stats.comments
     */
    public static final String STATS = "stats";

    /**
     * 搜索关键词。
     */
    public static final String KEYWORD = "keyword";

    /**
     * 当前模板类型。
     *
     * 开发诊断时使用。
     */
    public static final String RENDER_TYPE = "renderType";
}