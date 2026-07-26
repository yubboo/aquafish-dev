/**
 * 本机开发环境的安全重装验收模式。
 *
 * 只有 Vite DEV、/setup、maintenance=reinstall 和浏览器回环地址同时满足时生效。
 * 正式构建中的 import.meta.env.DEV 会被固定为 false。
 */
export const SETUP_REINSTALL_MAINTENANCE_HEADER =
  'X-Aquafish-Setup-Maintenance'
export const SETUP_REINSTALL_MAINTENANCE_VALUE = 'reinstall'

/** Vue Router 查询参数可能是字符串、字符串数组或空值，这里统一取第一个值。 */
function firstQueryValue(value: unknown): string {
  if (Array.isArray(value)) return String(value[0] || '')
  return typeof value === 'string' ? value : ''
}

/** 只认可 localhost、IPv4 回环和 IPv6 回环。 */
function isLoopbackBrowserHost(): boolean {
  if (typeof window === 'undefined') return false

  const hostname = window.location.hostname.toLowerCase()

  return hostname === 'localhost'
    || hostname === '127.0.0.1'
    || hostname === '::1'
    || hostname === '[::1]'
}

/**
 * 判断当前导航是否是本机开发重装验收入口。
 *
 * 该函数只决定是否显示向导；真正的后端接口仍有 dev profile、回环来源、
 * 精确请求头、数据库状态和危险确认等多层校验。
 */
export function isDevelopmentReinstallMaintenanceLocation(
  targetPath: string,
  maintenanceValue: unknown,
): boolean {
  return import.meta.env.DEV
    && targetPath === '/setup'
    && firstQueryValue(maintenanceValue)
      === SETUP_REINSTALL_MAINTENANCE_VALUE
    && isLoopbackBrowserHost()
}

/**
 * 安装 API 在本机开发重装模式下携带精确维护请求头。
 * 普通安装、生产构建和非回环地址返回空对象。
 */
export function setupMaintenanceRequestHeaders(): Record<string, string> {
  if (typeof window === 'undefined') return {}

  const maintenanceValue =
    new URLSearchParams(window.location.search)
      .get('maintenance')

  if (
    !isDevelopmentReinstallMaintenanceLocation(
      window.location.pathname,
      maintenanceValue,
    )
  ) {
    return {}
  }

  return {
    [SETUP_REINSTALL_MAINTENANCE_HEADER]:
      SETUP_REINSTALL_MAINTENANCE_VALUE,
  }
}
