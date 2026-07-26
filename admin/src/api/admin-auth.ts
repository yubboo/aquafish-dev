/**
 * 后台管理员认证 API。
 *
 * 关联 AdminLoginPage.vue、admin-auth store 和后端 AdminAuthController；使用
 * credentials=include 接收/携带 HttpOnly Cookie，前端不读取也不持久化会话 Token。
 */
import {
  AqApiError,
  requestAqEnvelope,
  type AqApiEnvelope,
} from '@aquafish/api-client'

import {
  aqAdminApiClient,
  toAqRequestConfig,
} from './aqadmin-api-client'

export type ApiResult<T> = AqApiEnvelope<T>

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

/**
 * 认证 Store 依赖“失败也返回 ApiResult”的旧契约，这里只做兼容转换；底层请求安全、
 * Cookie、CSRF 和错误分类仍全部由统一 Axios 客户端负责。
 */
async function requestJson<T>(path: string, init: RequestInit): Promise<ApiResult<T>> {
  try {
    return await requestAqEnvelope<T, BodyInit | null>(
      aqAdminApiClient,
      toAqRequestConfig(path, init),
    )
  } catch (error) {
    if (error instanceof AqApiError) {
      return {
        success: false,
        code: error.code,
        message: error.message,
        data: (error.data ?? null) as T | null,
      }
    }
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
