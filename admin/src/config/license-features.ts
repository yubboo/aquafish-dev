/**
 * 后台模块授权代码、中文名称和兼容规则。
 *
 * 关联后端 LicenseFeature.java、路由守卫、后台菜单和授权不足页面。前端规则只用于
 * 导航体验，真正不可绕过的安全判断仍由后端 LicenseEnforcementWebFilter 完成。
 */
import type { LicenseStatus } from '../api/license'

export const LICENSE_FEATURE_LABELS = {
  content: '内容管理',
  theme: '主题管理',
  plugin: '插件管理',
  forum: '论坛管理',
  market: '应用市场',
  ai: 'AI 能力',
  search: '站内搜索',
  updates: '更新服务',
} as const

export type LicenseFeature = keyof typeof LICENSE_FEATURE_LABELS

/** 判断字符串是否是前后端约定的可控模块代码。 */
export function isLicenseFeature(value: unknown): value is LicenseFeature {
  return typeof value === 'string' && value in LICENSE_FEATURE_LABELS
}

/**
 * 判断脱敏授权状态是否允许使用指定模块。
 *
 * enforcementEnabled=false 表示开发环境主动关闭授权强制，全部模块放行；正式环境
 * 必须是有效授权并包含模块代码。cms 是 content/theme 的早期兼容总包。
 */
export function isLicenseFeatureGranted(
  status: Pick<LicenseStatus, 'valid' | 'enforcementEnabled' | 'features'> | null,
  requiredFeature: LicenseFeature,
): boolean {
  if (!status) {
    return false
  }
  if (!status.enforcementEnabled) {
    return true
  }
  if (!status.valid) {
    return false
  }

  const features = new Set(
    status.features.map((feature) => feature.trim().toLowerCase()).filter(Boolean),
  )
  if (features.has(requiredFeature)) {
    return true
  }
  return (requiredFeature === 'content' || requiredFeature === 'theme')
    && features.has('cms')
}

/**
 * 根据后台 URL 返回所需模块，必须与后端 API 前缀映射保持一致。
 * 授权管理首页和授权不足页属于基础恢复入口，不要求额外模块。
 */
export function requiredLicenseFeatureForAdminPath(path: string): LicenseFeature | null {
  if (matchesPath(path, '/admin/license/updates')) return 'updates'
  if (matchesPath(path, '/admin/forum')) return 'forum'
  if (matchesPath(path, '/admin/content') || matchesPath(path, '/admin/cms')) return 'content'
  if (matchesPath(path, '/admin/theme') || matchesPath(path, '/admin/themes')) return 'theme'
  if (matchesPath(path, '/admin/plugin') || matchesPath(path, '/admin/plugins')) return 'plugin'
  if (matchesPath(path, '/admin/market')) return 'market'
  if (matchesPath(path, '/admin/ai')) return 'ai'
  if (matchesPath(path, '/admin/search')) return 'search'
  return null
}

/** 只匹配完整 URL 段，避免 /admin/aired 被误判为 /admin/ai。 */
function matchesPath(path: string, prefix: string): boolean {
  return path === prefix || path.startsWith(`${prefix}/`)
}
