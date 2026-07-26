/**
 * 后台管理员认证 API。
 *
 * 关联 AdminLoginPage.vue、admin-auth store 和后端 AdminAuthController；使用
 * credentials=include 接收/携带 HttpOnly Cookie，前端不读取也不持久化会话 Token。
 */
export interface ApiResult<T> {
  success: boolean
  code: string
  message: string
  data: T | null
}

export interface AdminLoginRequest {
  username: string
  password: string
  rememberMe: boolean
}

export interface AdminAuthUser {
  id: number
  username: string
  email: string
  displayName: string
  avatar: string
  status: string
  roles: string[]
  superAdmin: boolean
}

export interface AdminLoginResponse {
  expiresAt: string
  expiresInSeconds: number
  user: AdminAuthUser
}

export interface AdminLogoutResult {
  loggedOut: boolean
  note: string
}

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')

/** 合并可选 API 基地址，开发环境为空时继续使用 Vite 同源代理。 */
function apiUrl(path: string): string {
  return `${API_BASE_URL}${path}`
}

/**
 * 安全解析 ApiResult；空响应和非 JSON 响应也转换为统一失败结构，避免页面解析异常。
 */
async function readJson<T>(response: Response): Promise<ApiResult<T>> {
  const text = await response.text()

  if (!text) {
    return {
      success: false,
      code: 'EMPTY_RESPONSE',
      message: '接口没有返回内容',
      data: null,
    }
  }

  try {
    return JSON.parse(text) as ApiResult<T>
  } catch {
    return {
      success: false,
      code: 'INVALID_JSON',
      message: text,
      data: null,
    }
  }
}

/**
 * 认证请求统一入口：始终携带 Cookie，并把 HTTP、网络和业务错误规范化为 ApiResult。
 */
async function requestJson<T>(path: string, init: RequestInit): Promise<ApiResult<T>> {
  try {
    const response = await fetch(apiUrl(path), {
      ...init,
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        ...(init.headers || {}),
      },
    })

    const result = await readJson<T>(response)

    if (!response.ok && result.success) {
      return {
        success: false,
        code: `HTTP_${response.status}`,
        message: response.statusText || '接口请求失败',
        data: result.data,
      }
    }

    return result
  } catch (error) {
    return {
      success: false,
      code: 'NETWORK_ERROR',
      message: error instanceof Error ? error.message : '网络请求失败',
      data: null,
    }
  }
}

/** 提交后台登录表单；会话 Cookie 由后端 Set-Cookie 创建，返回值只包含用户快照。 */
export function loginAdmin(request: AdminLoginRequest): Promise<ApiResult<AdminLoginResponse>> {
  return requestJson<AdminLoginResponse>('/api/admin/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })
}

/** 使用当前 Cookie 查询管理员资料，用于刷新恢复和路由守卫鉴权。 */
export function getCurrentAdminUser(): Promise<ApiResult<AdminAuthUser>> {
  return requestJson<AdminAuthUser>('/api/admin/auth/me', {
    method: 'GET',
  })
}

/**
 * 从前台会员会话桥接到后台管理员会话。
 *
 * 当前台已登录管理员点击"管理后台"时，浏览器只携带 AQUAFISH_MEMBER_SESSION Cookie。
 * 本接口使用该 Cookie 验证会员身份，确认具备管理员角色后由后端补发 AQUAFISH_ADMIN_SESSION Cookie。
 * 成功后返回当前管理员用户快照，后续后台 API 可直接通过守卫。
 *
 * 调用场景：
 * - admin-auth store 的 ensureUser 在 /api/admin/auth/me 返回 401 时自动调用
 */
export function bridgeSession(): Promise<ApiResult<AdminAuthUser>> {
  return requestJson<AdminAuthUser>('/api/admin/auth/bridge', {
    method: 'GET',
  })
}

/** 请求后端注销当前会话并删除 Cookie。 */
export function logoutAdmin(): Promise<ApiResult<AdminLogoutResult>> {
  return requestJson<AdminLogoutResult>('/api/admin/auth/logout', {
    method: 'POST',
  })
}
