/**
 * 后台左侧菜单配置。
 *
 * AdminLayout.vue 根据本文件渲染分组、图标和子菜单；每个可点击 path 必须与
 * router/index.ts 对应。当前菜单只展示已经接入真实 API 或真实配置扫描的能力，
 * 尚未形成闭环的 AI、插件、市场功能不使用占位菜单伪装。
 */
import {
  isLicenseFeatureGranted,
  type LicenseFeature,
} from './license-features'
import type { LicenseStatus } from '../api/license'

export interface AdminMenuItem {
  key: string
  title: string
  path?: string
  /**
   * 菜单图标只保存稳定的语义键。
   * 真实图标组件由 AdminLayout 按需映射，避免把 Emoji 当作产品图标，
   * 也避免一次注册整个图标库。
   */
  icon?: AdminMenuIcon
  /** 模块商业授权要求；子项未声明时继承父菜单要求。 */
  requiredFeature?: LicenseFeature
  children?: AdminMenuItem[]
}

export type AdminMenuIcon =
  | 'dashboard'
  | 'users'
  | 'forum'
  | 'content'
  | 'theme'
  | 'plugin'
  | 'license'
  | 'system'

/**
 * Aquafish 后台菜单。
 *
 * Step 17-26-0：
 * 用户模块从普通 CMS 用户列表，升级为 CMS + BBS + AI 社区用户中心。
 *
 * 设计原则：
 * 1. 用户组：前台用户身份、发帖、回帖、上传、积分、阅读等社区权限；
 * 2. 管理员：能进入后台的人；
 * 3. 管理组：后台管理员分组，例如超级管理员、内容审核员、运营人员；
 * 4. 权限节点：后续单独做底层权限系统，用来控制菜单、按钮、接口。
 */
export const adminMenus: AdminMenuItem[] = [
  {
    key: 'dashboard',
    title: '控制台',
    path: '/admin',
    icon: 'dashboard',
  },
  {
    key: 'users',
    title: '用户',
    icon: 'users',
    children: [
      {
        key: 'users.manage',
        title: '用户管理',
        path: '/admin/users',
      },
      {
        key: 'users.profile-fields',
        title: '用户栏目',
        path: '/admin/users/profile-fields',
      },
      {
        key: 'users.statistics',
        title: '资料统计',
        path: '/admin/users/statistics',
      },
      {
        key: 'users.tags',
        title: '用户标签',
        path: '/admin/users/tags',
      },
      {
        key: 'users.bans',
        title: '禁止用户',
        path: '/admin/users/bans',
      },
      {
        key: 'users.ip-bans',
        title: '禁止 IP',
        path: '/admin/users/ip-bans',
      },
      {
        key: 'users.points',
        title: '积分奖惩',
        path: '/admin/users/points',
      },
      {
        key: 'users.recommended-follows',
        title: '推荐关注',
        path: '/admin/users/recommended-follows',
      },
      {
        key: 'users.recommended-friends',
        title: '推荐好友',
        path: '/admin/users/recommended-friends',
      },
      {
        key: 'users.profile-audits',
        title: '资料审核',
        path: '/admin/users/profile-audits',
      },
      {
        key: 'users.verifications',
        title: '认证设置',
        path: '/admin/users/verifications',
      },
      {
        key: 'users.groups',
        title: '用户组',
        path: '/admin/users/groups',
      },
      {
        key: 'users.admins',
        title: '管理员',
        path: '/admin/users/admins',
      },
      {
        key: 'users.admin-groups',
        title: '管理组',
        path: '/admin/users/admin-groups',
      },
    ],
  },
  {
    key: 'forum',
    title: '论坛管理',
    icon: 'forum',
    requiredFeature: 'forum',
    children: [
      {
        key: 'forum.sections',
        title: '版块管理',
        path: '/admin/forum/sections',
      },
      {
        key: 'forum.posts',
        title: '帖子管理',
        path: '/admin/forum/posts',
      },
      {
        key: 'forum.replies',
        title: '回帖管理',
        path: '/admin/forum/replies',
      },
      {
        key: 'forum.reports',
        title: '举报管理',
        path: '/admin/forum/reports',
      },
      {
        key: 'forum.moderation',
        title: '审核队列',
        path: '/admin/forum/moderation',
      },
    ],
  },
  {
    key: 'content',
    title: '内容管理',
    icon: 'content',
    requiredFeature: 'content',
    children: [
      {
        key: 'content.articles',
        title: '文章管理',
        path: '/admin/content/articles',
      },
      {
        key: 'content.categories',
        title: '分类管理',
        path: '/admin/content/categories',
      },
      {
        key: 'content.tags',
        title: '标签管理',
        path: '/admin/content/tags',
      },
      {
        key: 'content.pages',
        title: '单页管理',
        path: '/admin/content/pages',
      },
      {
        key: 'content.comments',
        title: '评论管理',
        path: '/admin/content/comments',
      },
    ],
  },
  {
    key: 'theme',
    title: '主题管理',
    icon: 'theme',
    requiredFeature: 'theme',
    children: [
      {
        key: 'theme.current',
        title: '当前主题',
        path: '/admin/themes/current',
      },
      {
        key: 'theme.list',
        title: '主题列表',
        path: '/admin/themes',
      },
      {
        key: 'theme.settings',
        title: '主题设置',
        path: '/admin/themes/settings',
      },
      {
        key: 'theme.diagnosis',
        title: '模板诊断',
        path: '/admin/themes/diagnosis',
      },
    ],
  },
  {
    key: 'plugin',
    title: '插件管理',
    icon: 'plugin',
    requiredFeature: 'plugin',
    children: [
      {
        key: 'plugin.status',
        title: '运行状态',
        path: '/admin/plugins',
      },
    ],
  },
  {
    key: 'license',
    title: '授权管理',
    icon: 'license',
    children: [
      {
        key: 'license.platform',
        title: '授权状态',
        path: '/admin/license',
      },
      {
        key: 'license.bind',
        title: '设备绑定',
        path: '/admin/license/bind',
      },
      {
        key: 'license.online',
        title: '在线校验',
        path: '/admin/license/online',
      },
      {
        key: 'license.updates',
        title: '更新服务',
        path: '/admin/license/updates',
        requiredFeature: 'updates',
      },
    ],
  },
  {
    key: 'system',
    title: '系统设置',
    icon: 'system',
    children: [
      {
        key: 'system.basic',
        title: '基础设置',
        path: '/admin/system/basic',
      },
      {
        key: 'system.permalink',
        title: '固定链接',
        path: '/admin/system/permalink',
      },
      {
        key: 'system.mail',
        title: '邮件设置',
        path: '/admin/system/mail',
      },
      {
        key: 'system.storage',
        title: '存储设置',
        path: '/admin/system/storage',
      },
      {
        key: 'system.security',
        title: '安全设置',
        path: '/admin/system/security',
      },
      {
        key: 'system.logs',
        title: '系统日志',
        path: '/admin/system/logs',
      },
    ],
  },
]

/**
 * 按当前授权过滤侧栏菜单。
 *
 * <p>过滤只是为了不展示客户无法使用的入口；直接输入 URL 时仍由路由守卫提示，
 * 直接调用 API 时仍由后端拒绝。父菜单授权会传给子项，子项可用 requiredFeature
 * 覆盖，例如“更新服务”位于授权管理组内但单独需要 updates。</p>
 */
export function filterAdminMenusByLicense(
  menus: AdminMenuItem[],
  status: LicenseStatus | null,
  inheritedFeature?: LicenseFeature,
): AdminMenuItem[] {
  // 授权状态尚在读取或读取失败时保留完整导航，避免页面刷新后菜单瞬间消失。
  // 真正的访问控制仍由路由守卫和后端 LicenseEnforcementWebFilter 执行。
  if (status === null) {
    return menus.map((menu) => ({
      ...menu,
      children: menu.children
        ? filterAdminMenusByLicense(
          menu.children,
          null,
          menu.requiredFeature || inheritedFeature,
        )
        : undefined,
    }))
  }

  return menus.flatMap((menu) => {
    const requiredFeature = menu.requiredFeature || inheritedFeature
    if (requiredFeature && !isLicenseFeatureGranted(status, requiredFeature)) {
      return []
    }

    const children = menu.children
      ? filterAdminMenusByLicense(menu.children, status, requiredFeature)
      : undefined
    if (menu.children?.length && !children?.length) {
      return []
    }
    return [{ ...menu, children }]
  })
}
