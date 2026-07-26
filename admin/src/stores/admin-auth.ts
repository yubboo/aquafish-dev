/**
 * 后台管理员会话状态仓库。
 *
 * 关联 admin-auth.ts 和 admin-auth-guard.ts：集中处理登录、读取当前用户、退出以及
 * 本地状态清理；真正的会话凭据位于后端 HttpOnly Cookie，不保存到 localStorage。
 */
import { reactive } from 'vue'
import {
  getCurrentAdminUser,
  loginAdmin,
  logoutAdmin,
  bridgeSession,
  type AdminAuthUser,
  type AdminLoginRequest,
} from '../api/admin-auth'

interface AdminAuthState {
  user: AdminAuthUser | null
  loading: boolean
  initialized: boolean
  error: string
}

const state = reactive<AdminAuthState>({
  user: null,
  loading: false,
  initialized: false,
  error: '',
})

let ensureUserPromise: Promise<boolean> | null = null

/** 清空浏览器内的用户快照；不会也不能直接删除服务端 HttpOnly Cookie。 */
function clearLocalState(): void {
  state.user = null
  state.initialized = true
  ensureUserPromise = null
}

/**
 * 提交账号密码并保存后端返回的脱敏管理员信息。
 * 登录失败时同步清空旧用户，防止页面继续显示已经失效的身份。
 */
async function login(request: AdminLoginRequest): Promise<AdminAuthUser> {
  state.loading = true
  state.error = ''
  try {
    const result = await loginAdmin(request)
    if (!result.success || !result.data) {
      throw new Error(result.message || '后台登录失败')
    }
    state.user = result.data.user
    state.initialized = true
    return result.data.user
  } catch (error) {
    clearLocalState()
    state.error = error instanceof Error ? error.message : '后台登录失败'
    throw error
  } finally {
    state.loading = false
  }
}

/** 使用现有 Cookie 向后端重新确认当前管理员，常用于刷新页面后的会话恢复。 */
async function fetchMe(): Promise<AdminAuthUser> {
  state.loading = true
  state.error = ''
  try {
    const result = await getCurrentAdminUser()
    if (!result.success || !result.data) {
      throw new Error(result.message || '登录已过期，请重新登录。')
    }
    state.user = result.data
    state.initialized = true
    return result.data
  } catch (error) {
    clearLocalState()
    state.error = error instanceof Error ? error.message : '登录已过期，请重新登录。'
    throw error
  } finally {
    state.loading = false
  }
}

/**
 * 路由守卫使用的布尔入口：已有用户直接放行，否则调用 fetchMe 验证服务端会话。
 * 后台会话失效时自动尝试从前台会员会话桥接。
 */
async function ensureUser(): Promise<boolean> {
  if (state.user) {
    return true
  }
  if (ensureUserPromise) {
    return ensureUserPromise
  }

  ensureUserPromise = (async () => {
    try {
      await fetchMe()
      return true
    } catch {
      // 后台会话失效，尝试从前台会员会话桥接
      try {
        const bridgeResult = await bridgeSession()
        if (bridgeResult.success && bridgeResult.data) {
          state.user = bridgeResult.data
          state.initialized = true
          state.error = ''
          return true
        }
      } catch {
        // 桥接也失败，用户确实未登录或没有后台权限
      }
      return false
    }
  })()

  try {
    return await ensureUserPromise
  } finally {
    ensureUserPromise = null
  }
}

/** 无论后端退出请求是否成功都清空本地快照，避免前端残留已退出用户。 */
async function logout(): Promise<void> {
  try {
    await logoutAdmin()
  } finally {
    clearLocalState()
  }
}

export const adminAuthStore = {
  state,
  get user() {
    return state.user
  },
  login,
  fetchMe,
  ensureUser,
  logout,
  clear: clearLocalState,
}
