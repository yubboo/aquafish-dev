/**
 * aqadmin 后台 API 的统一安全导航。
 *
 * Fetch 兼容层与 Axios 核心客户端共用本文件，防止迁移期间两套请求实现产生不同跳转。
 */

/** 401 时保留当前后台 URL，并整页进入统一会员登录页。 */
export function redirectToAdminLogin(): void {
  if (window.location.pathname === '/login') {
    return
  }
  const currentPath = window.location.pathname + window.location.search
  const redirect = currentPath.startsWith('/admin')
    ? `?redirect=${encodeURIComponent(currentPath)}`
    : ''
  window.location.replace(`/login${redirect}`)
}

/** 423 时保留当前后台 URL，并进入授权页。 */
export function redirectToAdminLicense(): void {
  if (window.location.pathname === '/admin/license') {
    return
  }
  const currentPath = window.location.pathname + window.location.search
  const redirect = currentPath.startsWith('/admin')
    ? `?redirect=${encodeURIComponent(currentPath)}`
    : ''
  window.location.replace(`/admin/license${redirect}`)
}

/** 403 + LICENSE_FEATURE_REQUIRED 时进入模块授权不足页。 */
export function redirectToAdminFeatureRequired(requiredFeature: string): void {
  if (window.location.pathname === '/admin/license/feature-required') {
    return
  }
  const currentPath = window.location.pathname + window.location.search
  const query = new URLSearchParams()
  if (requiredFeature) {
    query.set('feature', requiredFeature)
  }
  if (currentPath.startsWith('/admin')) {
    query.set('redirect', currentPath)
  }
  window.location.replace(`/admin/license/feature-required?${query.toString()}`)
}
