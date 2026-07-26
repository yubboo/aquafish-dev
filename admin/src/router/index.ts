/**
 * Aquafish 后台与本地开发站点入口路由。
 *
 * 核心域菜单全部对应真实页面；未开发的 AI、插件、市场能力不再用占位路由伪装。
 * 生产环境的公开 URL 由 Java 主题控制器直接响应，本地 Vite 根路径通过 /site 代理交还后端。
 */
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const AdminLayout = () => import('../layouts/AdminLayout.vue')

function workspaceRoute(
  path: string,
  name: string,
  title: string,
  description: string,
  domain: string,
  resource: string,
): RouteRecordRaw {
  return {
    path,
    name,
    component: () => import('../pages/workspace/AdminDataWorkspacePage.vue'),
    props: { title, description, domain, resource },
    meta: { title },
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'public.handoff',
    component: () => import('../pages/public/PublicSiteHandoffPage.vue'),
    meta: { public: true, title: 'Aquafish 前台' },
  },
  {
    path: '/system/unavailable',
    name: 'system.unavailable',
    component: () => import('../pages/common/SystemUnavailablePage.vue'),
    meta: { public: true, title: '系统暂时不可用' },
  },
  {
    path: '/setup/installed',
    name: 'setup.installed',
    component: () => import('../pages/setup/SetupInstalledRedirectPage.vue'),
    meta: { public: true, title: '系统已经安装' },
  },
  {
    path: '/setup',
    name: 'setup',
    component: () => import('../pages/setup/SetupPage.vue'),
    meta: { public: true, title: '系统安装向导' },
  },
  {
    path: '/admin',
    component: AdminLayout,
    children: [
      {
        path: '',
        name: 'admin.dashboard',
        component: () => import('../pages/dashboard/DashboardPage.vue'),
        meta: { title: '控制台' },
      },
      {
        path: 'users',
        name: 'admin.users.manage',
        component: () => import('../pages/users/UserManagePage.vue'),
        props: {
          pageTitle: '用户管理',
          pageDescription: '查看、搜索、创建和管理 Aquafish 用户。',
          adminOnlyDefault: false,
        },
        meta: { title: '用户管理' },
      },
      {
        path: 'users/admins',
        name: 'admin.users.admins',
        component: () => import('../pages/users/UserManagePage.vue'),
        props: {
          pageTitle: '管理员',
          pageDescription: '管理具备后台身份的账号。',
          adminOnlyDefault: true,
        },
        meta: { title: '管理员' },
      },
      {
        path: 'users/groups',
        name: 'admin.users.groups',
        component: () => import('../pages/users/UserSimpleListPage.vue'),
        props: {
          type: 'groups',
          title: '用户组',
          description: '前台社区身份、积分、发帖与阅读权限。',
        },
        meta: { title: '用户组' },
      },
      {
        path: 'users/admin-groups',
        name: 'admin.users.adminGroups',
        component: () => import('../pages/users/UserSimpleListPage.vue'),
        props: {
          type: 'adminGroups',
          title: '管理组',
          description: '后台管理员分组与管理权限。',
        },
        meta: { title: '管理组' },
      },
      {
        path: 'users/roles',
        name: 'admin.users.roles',
        component: () => import('../pages/users/UserSimpleListPage.vue'),
        props: {
          type: 'roles',
          title: '角色列表',
          description: '系统登录和后台访问角色。',
        },
        meta: { title: '角色列表' },
      },
      workspaceRoute('users/profile-fields', 'admin.users.profileFields', '用户栏目',
        '查看可用资料字段、公开性、必填和审核规则。', 'users', 'profile-fields'),
      workspaceRoute('users/statistics', 'admin.users.statistics', '资料统计',
        '查看用户发帖、关注、积分与最后活跃数据。', 'users', 'statistics'),
      workspaceRoute('users/tags', 'admin.users.tags', '用户标签',
        '查看已配置用户标签及启用状态。', 'users', 'tags'),
      workspaceRoute('users/bans', 'admin.users.bans', '禁止用户',
        '查看用户封禁范围、原因与到期时间。', 'users', 'bans'),
      workspaceRoute('users/ip-bans', 'admin.users.ipBans', '禁止 IP',
        '查看 IP 封禁规则和生效状态。', 'users', 'ip-bans'),
      workspaceRoute('users/points', 'admin.users.points', '积分奖惩',
        '查看积分变化、来源与变更后余额。', 'users', 'points'),
      workspaceRoute('users/recommended-follows', 'admin.users.recommendedFollows', '推荐关注',
        '查看用户关注与推荐关系数据。', 'users', 'relationships'),
      workspaceRoute('users/recommended-friends', 'admin.users.recommendedFriends', '推荐好友',
        '查看好友与其他用户关系数据。', 'users', 'relationships'),
      workspaceRoute('users/profile-audits', 'admin.users.profileAudits', '资料审核',
        '查看资料字段审核队列与处理结果。', 'users', 'profile-audits'),
      workspaceRoute('users/verifications', 'admin.users.verifications', '认证记录',
        '查看用户认证申请、审核人与有效期。', 'users', 'verifications'),

      {
        path: 'forum/sections',
        name: 'admin.forum.sections',
        component: () => import('../pages/forum/ForumSectionsPage.vue'),
        meta: { title: '板块管理' },
      },
      workspaceRoute('forum/posts', 'admin.forum.posts', '帖子管理',
        '查看论坛主题状态、审核、置顶、精华与统计。', 'forum', 'threads'),
      workspaceRoute('forum/replies', 'admin.forum.replies', '回帖管理',
        '查看论坛回复楼层、内容状态与审核状态。', 'forum', 'replies'),
      workspaceRoute('forum/reports', 'admin.forum.reports', '举报管理',
        '查看举报目标、原因、受理人和处理结果。', 'forum', 'reports'),
      workspaceRoute('forum/moderation', 'admin.forum.moderation', '审核记录',
        '查看论坛不可覆盖的管理与审核动作。', 'forum', 'moderation'),

      {
        path: 'content/articles',
        name: 'admin.content.articles',
        component: () => import('../pages/content/ContentArticlesPage.vue'),
        meta: { title: '文章管理' },
      },
      workspaceRoute('content/categories', 'admin.content.categories', '分类管理',
        '查看 CMS 分类层级、别名、排序与文章数量。', 'content', 'categories'),
      workspaceRoute('content/tags', 'admin.content.tags', '标签管理',
        '查看 CMS 标签、别名和文章数量。', 'content', 'tags'),
      workspaceRoute('content/pages', 'admin.content.pages', '单页管理',
        '查看关于、帮助、协议等独立页面。', 'content', 'pages'),
      workspaceRoute('content/comments', 'admin.content.comments', '评论管理',
        '查看文章和页面评论及审核状态。', 'content', 'comments'),

      {
        path: 'themes/current',
        name: 'admin.themes.current',
        component: () => import('../pages/theme/ThemeManagementPage.vue'),
        props: { mode: 'current' },
        meta: { title: '当前主题' },
      },
      {
        path: 'themes',
        name: 'admin.themes.list',
        component: () => import('../pages/theme/ThemeManagementPage.vue'),
        props: { mode: 'list' },
        meta: { title: '主题列表' },
      },
      {
        path: 'themes/settings',
        name: 'admin.themes.settings',
        component: () => import('../pages/theme/ThemeManagementPage.vue'),
        props: { mode: 'settings' },
        meta: { title: '主题设置' },
      },
      {
        path: 'themes/diagnosis',
        name: 'admin.themes.diagnosis',
        component: () => import('../pages/theme/ThemeManagementPage.vue'),
        props: { mode: 'diagnosis' },
        meta: { title: '模板诊断' },
      },

      {
        path: 'plugins',
        name: 'admin.plugins.status',
        component: () => import('../pages/plugin/PluginManagementPage.vue'),
        meta: { title: '插件运行状态' },
      },

      {
        path: 'license',
        name: 'admin.license',
        component: () => import('../pages/license/LicenseManagementPage.vue'),
        meta: { title: '系统平台授权' },
      },
      { path: 'license/activation', redirect: '/admin/license' },
      {
        path: 'license/bind',
        name: 'admin.license.bind',
        component: () => import('../pages/license/LicenseManagementPage.vue'),
        meta: { title: '授权设备绑定' },
      },
      {
        path: 'license/online',
        name: 'admin.license.online',
        component: () => import('../pages/license/LicenseManagementPage.vue'),
        meta: { title: '在线授权校验' },
      },
      {
        path: 'license/updates',
        name: 'admin.license.updates',
        component: () => import('../pages/license/LicenseUpdateStatusPage.vue'),
        meta: { title: '授权更新服务' },
      },
      {
        path: 'license/feature-required',
        name: 'admin.license.featureRequired',
        component: () => import('../pages/license/LicenseFeatureRequiredPage.vue'),
        meta: { title: '模块授权不足' },
      },
      {
        path: 'system/basic',
        name: 'admin.system.basic',
        component: () => import('../pages/system/SystemSettingsStatusPage.vue'),
        props: { section: 'basic' },
        meta: { title: '基础设置' },
      },
      {
        path: 'system/permalink',
        name: 'admin.system.permalink',
        component: () => import('../pages/system/permalink/PermalinkSettingsPage.vue'),
        meta: { title: '固定链接' },
      },
      {
        path: 'system/mail',
        name: 'admin.system.mail',
        component: () => import('../pages/system/SystemSettingsStatusPage.vue'),
        props: { section: 'mail' },
        meta: { title: '邮件设置' },
      },
      {
        path: 'system/storage',
        name: 'admin.system.storage',
        component: () => import('../pages/system/SystemSettingsStatusPage.vue'),
        props: { section: 'storage' },
        meta: { title: '存储设置' },
      },
      {
        path: 'system/security',
        name: 'admin.system.security',
        component: () => import('../pages/system/SystemSettingsStatusPage.vue'),
        props: { section: 'security' },
        meta: { title: '安全设置' },
      },
      {
        path: 'system/logs',
        name: 'admin.system.logs',
        component: () => import('../pages/system/SystemSettingsStatusPage.vue'),
        props: { section: 'logs' },
        meta: { title: '系统日志' },
      },
    ],
  },
  { path: '/admin/theme/list', redirect: '/admin/themes' },
  { path: '/:pathMatch(.*)*', redirect: '/admin' },
]

export default createRouter({
  history: createWebHistory(),
  routes,
})
