/**
 * 首次安装状态路由守卫。
 *
 * 权威状态来自后端 /api/setup/status；它负责阻止未安装系统进入后台，也负责让已安装
 * 系统永远不再挂载 SetupPage.vue。服务端 SetupAccessWebFilter 是最终安全防线。
 */
import type { RouteLocationRaw, Router } from 'vue-router'
import { fetchSetupStatus, type SetupStatus } from '../api/setup-status'
import {
  INSTALLED_SETUP_NOTICE_PATH,
  installedSetupNoticeLocation,
} from './setup-completion-destination'
import {
  isDevelopmentReinstallMaintenanceLocation,
} from './setup-maintenance-mode'

/**
 * 数据库状态无法确认时使用的独立故障页。
 *
 * 该路径不能位于 /setup 下，否则用户会误以为系统退回了首次安装流程。
 */
export const SYSTEM_UNAVAILABLE_PATH = '/system/unavailable'

interface GuardedRouter extends Router {
  __aquafishSetupStatusGuardInstalled?: boolean
}

/**
 * 已安装状态在单次页面生命周期内不会回退，因此确认后允许缓存。
 * 未安装状态不能缓存：安装向导完成后必须在下一次导航重新读取数据库状态，
 * 否则会把已经安装的系统错误地继续送回 /setup。
 */
let installedConfirmed = false

/**
 * 根据权威安装状态决定页面导航。
 *
 * 关联功能：首次安装、后台登录、后台任意子页面。
 * 实现结果：
 * 1. 未安装或状态不可用时，后台页面统一进入 /setup；
 * 2. 已安装后禁止挂载安装向导，只渲染“系统已经安装”的轻量提示页；
 * 3. 提示后前往用户在首次安装完成页选择的入口，没有记录时进入后台登录。
 */
export function resolveSetupNavigation(
  status: Pick<SetupStatus, 'installed' | 'canInstall' | 'stateAvailable'>,
  targetPath: string,
): true | RouteLocationRaw {
  if (status.installed) {
    if (targetPath === '/setup') {
      return installedSetupNoticeLocation()
    }
    if (targetPath === SYSTEM_UNAVAILABLE_PATH) {
      return { path: '/admin', replace: true }
    }
    return true
  }

  /*
   * 只有后端明确确认“状态可用并且允许安装”时才能打开安装器。
   * DATABASE_UNAVAILABLE、INVALID_RECORD 或其他未知状态必须失败关闭，
   * 但不能再伪装成首次安装，而是进入独立故障页等待数据库恢复。
   */
  if (!status.stateAvailable || !status.canInstall) {
    if (targetPath === SYSTEM_UNAVAILABLE_PATH) {
      return true
    }

    return {
      path: SYSTEM_UNAVAILABLE_PATH,
      query: {
        redirect: safeRecoveryTarget(targetPath),
      },
      replace: true,
    }
  }

  if (targetPath === SYSTEM_UNAVAILABLE_PATH) {
    return { path: '/setup', replace: true }
  }

  if (targetPath === INSTALLED_SETUP_NOTICE_PATH) {
    return { path: '/setup', replace: true }
  }

  return targetPath === '/setup'
    ? true
    : { path: '/setup', replace: true }
}

/**
 * 安装全局路由守卫。
 *
 * 必须先于后台登录守卫注册。否则空白系统会先因“未登录”进入登录页，
 * 用户提交登录后才看到“系统尚未安装”，这正是本次修复的问题。
 */
export function installSetupStatusGuard(router: Router): void {
  const guardedRouter = router as GuardedRouter

  if (guardedRouter.__aquafishSetupStatusGuardInstalled) {
    return
  }

  guardedRouter.__aquafishSetupStatusGuardInstalled = true

  router.beforeEach(async (to) => {
    const targetPath = to.path || '/'

    /*
     * 本机开发重装验收只允许重新挂载 SetupPage。
     * 页面和后端接口仍会继续读取权威安装状态并执行各自安全校验。
     */
    if (
      isDevelopmentReinstallMaintenanceLocation(
        targetPath,
        to.query.maintenance,
      )
    ) {
      return true
    }

    if (installedConfirmed) {
      return resolveSetupNavigation(
        {
          installed: true,
          canInstall: false,
          stateAvailable: true,
        },
        targetPath,
      )
    }

    try {
      const status = await fetchSetupStatus()
      installedConfirmed = status.installed
      return resolveSetupNavigation(status, targetPath)
    } catch {
      /*
       * 状态接口不可访问时不能猜测系统已安装，也不能开放安装器。
       * 独立故障页只提供恢复提示和重新检测；安装写接口仍由后端闸门保护。
       */
      return targetPath === SYSTEM_UNAVAILABLE_PATH
        ? true
        : {
            path: SYSTEM_UNAVAILABLE_PATH,
            query: {
              redirect: safeRecoveryTarget(targetPath),
            },
            replace: true,
          }
    }
  })
}

/**
 * 故障恢复后只允许返回站内后台路径，避免把查询参数变成开放重定向入口。
 */
function safeRecoveryTarget(targetPath: string): string {
  if (
    targetPath === '/admin'
    || targetPath.startsWith('/admin/')
  ) {
    return targetPath
  }

  return '/admin'
}
