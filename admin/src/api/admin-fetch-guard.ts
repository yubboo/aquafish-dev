/**
 * 后台 fetch 全局安全包装器。
 *
 * main.ts 只安装一次：为同源后台写请求补充 CSRF Token，并在后端返回未登录或未授权
 * 状态时统一跳转，避免每个 API 文件重复实现 Cookie、CSRF 和错误导航逻辑。
 */
let installed = false
let csrfToken = ''

/** 判断请求是否属于需要 Cookie、CSRF 和统一错误导航的后台 API。 */
function isAdminApiUrl(input: RequestInfo | URL): boolean {
  const url = typeof input === 'string'
    ? input
    : input instanceof URL
      ? input.pathname
      : input.url
  return url.includes('/api/admin/')
}

/** 识别 CSRF Token 获取接口，避免给"获取 Token"请求再次递归附加 Token。 */
function isCsrfApiUrl(input: RequestInfo | URL): boolean {
  const url = typeof input === 'string'
    ? input
    : input instanceof URL
      ? input.pathname
      : input.url
  return url.includes('/api/admin/auth/csrf')
}

/**
 * 识别用于路由守卫认证检查的请求。
 *
 * 这些请求由 admin-auth store 的 ensureUser/fetchMe/bridgeSession 调用；
 * 它们的 401 响应不能触发单独跳转登录页，否则会中断 ensureUser 内部的
 * bridgeSession 回退逻辑。auth guard 自身负责在认证失败后导航到登录页。
 */
function isAuthCheckRequest(input: RequestInfo | URL): boolean {
  const url = typeof input === 'string'
    ? input
    : input instanceof URL
      ? input.pathname
      : input.url
  return url.includes('/api/admin/auth/me')
    || url.includes('/api/admin/auth/bridge')
}

/** 按 HTTP 语义判断写请求；GET、HEAD、OPTIONS 不附加 CSRF Token。 */
function isUnsafeMethod(init?: RequestInit): boolean {
  const method = String(init?.method || 'GET').toUpperCase()
  return !['GET', 'HEAD', 'OPTIONS'].includes(method)
}

/** 401 时保留当前后台 URL 并整页进入统一会员登录页，清理当前页面可能存在的敏感状态。 */
function redirectToLogin(): void {
  if (window.location.pathname === '/login') {
    return
  }
  const currentPath = window.location.pathname + window.location.search
  const redirect = currentPath.startsWith('/admin')
    ? `?redirect=${encodeURIComponent(currentPath)}`
    : ''
  window.location.replace(`/login${redirect}`)
}

/** 423 时保留当前后台 URL 并进入授权页，激活后可以回到原页面。 */
function redirectToLicense(): void {
  if (window.location.pathname === '/admin/license') {
    return
  }
  const currentPath = window.location.pathname + window.location.search
  const redirect = currentPath.startsWith('/admin')
    ? `?redirect=${encodeURIComponent(currentPath)}`
    : ''
  window.location.replace(`/admin/license${redirect}`)
}

/**
 * 403 + LICENSE_FEATURE_REQUIRED 时进入模块授权不足页。
 * requiredFeature 来自后端固定枚举，但仍经过 URL 编码；当前页面作为升级后的返回地址。
 */
function redirectToFeatureRequired(requiredFeature: string): void {
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

/**
 * 后台请求统一使用 HttpOnly 会话 Cookie，并为写请求附加 CSRF 令牌。
 */
export function installAdminFetchGuard(): void {
  if (installed) {
    return
  }
  installed = true
  const rawFetch = window.fetch.bind(window)

  /**
   * 延迟获取并缓存当前页面生命周期的 CSRF Token。
   * 后端拒绝写请求后会清空缓存，使下一次写操作重新领取，兼容 Token 轮换。
   */
  async function ensureCsrfToken(): Promise<string> {
    if (csrfToken) {
      return csrfToken
    }
    const response = await rawFetch('/api/admin/auth/csrf', {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    const body = await response.json().catch(() => null)
    const token = body?.data?.token
    if (!response.ok || !token) {
      throw new Error(body?.message || '无法获取请求安全令牌。')
    }
    csrfToken = String(token)
    return csrfToken
  }

  /*
   * 只包装 /api/admin/：其余 fetch 保持浏览器原行为。后台请求强制携带 Cookie，写操作
   * 附加 X-XSRF-TOKEN，并根据 401/423 统一进入登录或授权页面。
   */
  window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
    if (!isAdminApiUrl(input)) {
      return rawFetch(input, init)
    }

    const headers = new Headers(init?.headers || {})
    if (isUnsafeMethod(init) && !isCsrfApiUrl(input)) {
      headers.set('X-XSRF-TOKEN', await ensureCsrfToken())
    }

    const response = await rawFetch(input, {
      ...(init || {}),
      credentials: 'include',
      headers,
    })

    let forbiddenBody: { code?: string; data?: { requiredFeature?: string } } | null = null
    if (response.status === 403) {
      forbiddenBody = await response.clone().json().catch(() => null)
    }
    if (forbiddenBody?.code === 'LICENSE_FEATURE_REQUIRED') {
      redirectToFeatureRequired(String(forbiddenBody.data?.requiredFeature || ''))
    } else if (response.status === 403 && isUnsafeMethod(init)) {
      // 非模块授权 403 才可能是 CSRF 失效，下一次写请求重新获取 Token。
      csrfToken = ''
    }
    /*
     * 路由守卫使用的认证检查请求（/me、/bridge）的 401 不能触发跳转，
     * 否则 ensureUser 内部的 bridgeSession 回退逻辑无法执行。
     * 登录/桥接失败后由 admin-auth-guard 统一决定跳转到登录页。
     */
    if (response.status === 401
      && !String(input).includes('/api/admin/auth/login')
      && !isAuthCheckRequest(input)) {
      redirectToLogin()
    }
    /*
     * 后端许可证过滤器使用 423 表示"已登录但系统尚未激活"。
     * 授权 API 自身不能再次跳转，否则错误授权码的提示会被页面刷新吞掉。
     */
    if (response.status === 423 && !String(input).includes('/api/admin/license/')) {
      redirectToLicense()
    }
    return response
  }
}
