/**
 * 后台管理员登录守卫。
 *
 * 它在安装和授权守卫之后运行，通过 adminAuthStore.ensureUser() 向后端确认 HttpOnly
 * 会话；未登录时跳转到统一会员登录页 /login，登录后通过 bridge 机制回到后台。
 *
 * Aquafish 采用一套用户体系：所有用户（包括管理员）都通过 /login 和 /register
 * 完成认证；后台不再拥有独立的登录页。具备管理员角色的会员访问 /admin 时，
 * ensureUser() 会通过 /api/admin/auth/bridge 自动获取后台会话。
 */
import type { Router } from 'vue-router'
import { adminAuthStore } from '../stores/admin-auth'

interface GuardedRouter extends Router {
  __aquafishAdminAuthGuardInstalled?: boolean
}

/**
 * 后台登录守卫。
 *
 * 当前阶段：
 * Step 17-24：Vue 后台登录页接入真实后端接口。
 * Step 60：移除后台独立登录页，统一为一套会员登入/注册体系。
 */
export function installAdminAuthGuard(router: Router): void {
  const guardedRouter = router as GuardedRouter

  if (guardedRouter.__aquafishAdminAuthGuardInstalled) {
    return
  }

  guardedRouter.__aquafishAdminAuthGuardInstalled = true

  router.beforeEach(async (to) => {
    const path = to.path || ''
    const isAdminPath = path === '/admin' || path.startsWith('/admin/')
    const isLoginPath = path === '/admin/login'

    if (!isAdminPath) {
      return true
    }

    if (isLoginPath) {
      // 统一入口：后台不再有独立登录页，跳转到前台会员登录
      const ok = await adminAuthStore.ensureUser()
      if (ok) {
        return '/admin'
      }
      // 未登录 → 外部跳转到统一会员登录页
      window.location.href = '/login?redirect=' + encodeURIComponent('/admin')
      return false
    }

    const ok = await adminAuthStore.ensureUser()

    if (!ok) {
      // 未登录 → 外部跳转到统一会员登录页，登录后回到当前页面
      window.location.href = '/login?redirect=' + encodeURIComponent(to.fullPath)
      return false
    }

    return true
  })
}
