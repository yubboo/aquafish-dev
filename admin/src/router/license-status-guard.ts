/**
 * 系统平台与细分模块授权路由守卫。
 *
 * 安装、登录守卫通过后，本守卫只限制 forum/content/ai 等高级模块。未授权管理员
 * 仍能进入控制台、用户和基础系统设置；真正不可绕过的 API 边界由后端过滤器完成。
 */
import type { RouteLocationRaw, Router } from 'vue-router'
import type { LicenseStatus } from '../api/license'
import {
  isLicenseFeatureGranted,
  requiredLicenseFeatureForAdminPath,
} from '../config/license-features'
import {
  clearLicenseStatus,
  currentLicenseStatus,
  loadLicenseStatus,
  updateLicenseStatus,
} from '../stores/license-status'

interface GuardedRouter extends Router {
  __aquafishLicenseStatusGuardInstalled?: boolean
}

type NavigationLicenseStatus = Pick<LicenseStatus, 'usable'>
  & Partial<Pick<LicenseStatus, 'valid' | 'enforcementEnabled' | 'features'>>

/** 授权恢复入口始终可进入，否则用户无法激活或了解缺少的模块。 */
function isLicenseRecoveryPath(path: string): boolean {
  return path === '/admin/license' || path === '/admin/license/feature-required'
}

/**
 * 纯函数形式的授权导航规则，便于单元测试覆盖平台未激活与模块未授权分支。
 */
export function resolveLicenseNavigation(
  status: NavigationLicenseStatus,
  targetPath: string,
): true | RouteLocationRaw {
  if (isLicenseRecoveryPath(targetPath)) {
    return true
  }
  const requiredFeature = requiredLicenseFeatureForAdminPath(targetPath)
  if (!requiredFeature) {
    return true
  }

  if (!status.usable) {
    return {
      path: '/admin/license',
      replace: true,
      query: targetPath.startsWith('/admin')
        ? { redirect: targetPath }
        : undefined,
    }
  }

  if (!isLicenseFeatureGranted({
    valid: status.valid ?? false,
    enforcementEnabled: status.enforcementEnabled ?? true,
    features: status.features ?? [],
  }, requiredFeature)) {
    return {
      path: '/admin/license/feature-required',
      replace: true,
      query: {
        feature: requiredFeature,
        redirect: targetPath,
      },
    }
  }
  return true
}

/**
 * 注册后台许可证路由守卫。
 *
 * 注册顺序必须在安装守卫和登录守卫之后：空白系统先安装，未登录用户先登录，
 * 已登录管理员最后检查平台和模块授权。共享状态只优化导航，后端仍会实时验签。
 */
export function installLicenseStatusGuard(router: Router): void {
  const guardedRouter = router as GuardedRouter
  if (guardedRouter.__aquafishLicenseStatusGuardInstalled) {
    return
  }
  guardedRouter.__aquafishLicenseStatusGuardInstalled = true

  router.beforeEach(async (to) => {
    const targetPath = to.path || '/'
    const isAdminBusinessPath = targetPath === '/admin' || targetPath.startsWith('/admin/')
    if (!isAdminBusinessPath) {
      return true
    }
    if (isLicenseRecoveryPath(targetPath)) {
      return true
    }

    try {
      const status = currentLicenseStatus.value || await loadLicenseStatus()
      return resolveLicenseNavigation(status, targetPath)
    } catch {
      // 状态接口异常时进入授权页展示具体错误，不把网络异常误判为已授权。
      return resolveLicenseNavigation({ usable: false }, targetPath)
    }
  })
}

/** 激活或状态查询成功后，同步授权页、侧栏菜单与下一次路由判断。 */
export function confirmUsableLicense(status: LicenseStatus): void {
  updateLicenseStatus(status)
}

/** 取消激活后同步返回状态；无返回状态时清空缓存并在下次导航重新查询。 */
export function clearUsableLicenseConfirmation(status?: LicenseStatus): void {
  if (status) {
    updateLicenseStatus(status)
    return
  }
  clearLicenseStatus()
}
