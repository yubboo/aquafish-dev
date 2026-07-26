/**
 * 安装完成后的页面入口偏好。
 *
 * 仅记住用户在完成页选择的跳转目标，供 SetupInstalledRedirectPage.vue 使用；它不参与
 * 安装状态判定，并对内部路径和外部 HTTP(S) 地址做白名单化，避免脚本协议跳转。
 */
import type { RouteLocationRaw } from 'vue-router'

/**
 * 安装完成后的入口偏好只影响页面导航，不参与“是否已安装”的安全判断。
 * 权威安装状态始终来自后端数据库；localStorage 被修改最多只会改变跳转页面。
 */
export const SETUP_DESTINATION_STORAGE_KEY = 'aquafish.setup.destination.v1'
export const INSTALLED_SETUP_NOTICE_PATH = '/setup/installed'
export const DEFAULT_SETUP_DESTINATION = '/login?redirect=%2Fadmin&installed=1'

const INTERNAL_DESTINATIONS = new Set([
  DEFAULT_SETUP_DESTINATION,
  '/admin/themes',
  '/login?redirect=%2Fadmin',
])

interface SetupDestinationStorage {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
}

/**
 * 仅接受安装完成页提供的两个后台入口，或完整的 HTTP(S) 站点地址。
 * 明确拒绝 javascript:、协议相对地址和超长值，避免存储值变成开放重定向入口。
 */
export function normalizeSetupDestination(value: unknown): string {
  const candidate = typeof value === 'string' ? value.trim() : ''
  if (!candidate || candidate.length > 2048) return DEFAULT_SETUP_DESTINATION
  if (INTERNAL_DESTINATIONS.has(candidate)) return candidate
  if (candidate.startsWith('/') || candidate.startsWith('//')) return DEFAULT_SETUP_DESTINATION

  try {
    const url = new URL(candidate)
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) {
      return DEFAULT_SETUP_DESTINATION
    }
    return url.href
  } catch {
    return DEFAULT_SETUP_DESTINATION
  }
}

/** 安全取得 localStorage；隐私模式或服务端渲染不可用时返回 null。 */
function browserStorage(): SetupDestinationStorage | null {
  try {
    return typeof window === 'undefined' ? null : window.localStorage
  } catch {
    return null
  }
}

/** 保存用户在首次安装完成页主动选择的后续入口。 */
export function rememberSetupDestination(
  destination: string,
  storage: SetupDestinationStorage | null = browserStorage(),
): string {
  const normalized = normalizeSetupDestination(destination)
  try {
    storage?.setItem(SETUP_DESTINATION_STORAGE_KEY, normalized)
  } catch {
    // 浏览器禁用本地存储时仍允许正常跳转，未来访问 /setup 时回退到后台登录。
  }
  return normalized
}

/** 读取已保存入口；无记录、损坏或被篡改时安全回退到后台登录。 */
export function readSetupDestination(
  storage: SetupDestinationStorage | null = browserStorage(),
): string {
  try {
    return normalizeSetupDestination(storage?.getItem(SETUP_DESTINATION_STORAGE_KEY))
  } catch {
    return DEFAULT_SETUP_DESTINATION
  }
}

/**
 * 已安装系统访问 /setup 时只进入轻量提示页，不再挂载安装向导组件。
 */
export function installedSetupNoticeLocation(): RouteLocationRaw {
  return {
    path: INSTALLED_SETUP_NOTICE_PATH,
    replace: true,
  }
}

/** 判断规范化后的入口是否需要使用 window.location 而不是 Vue Router。 */
export function isExternalSetupDestination(destination: string): boolean {
  return /^https?:\/\//i.test(normalizeSetupDestination(destination))
}
