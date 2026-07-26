/** P0-3-2B-15C：弹窗重装确认、耗时恢复与服务页视觉收口：识别门槛。 */
/**
 * 安装向导的纯流程规则。
 *
 * SetupPage.vue 使用这里生成分发包与托管部署的不同步骤，并复用协议放行条件和数据库
 * 默认端口。保持为无副作用函数，便于 setup-wizard.test.ts 覆盖流程分支。
 */
import type {
  SetupDatabaseInspection,
  SetupDeploymentContext,
} from '../../api/setup'

/**
 * 欢迎页与协议页属于安装入口，不挤进步骤条。
 * 分发包保留数据库与 Redis 配置步骤；1Panel/Docker 已由平台完成服务配置，
 * 不再把平台内部连接参数重复交给用户确认或测试。
 */
export type SetupStepKey =
  | 'environment'
  | 'services'
  | 'identity'
  | 'install'
  | 'complete'

export interface SetupStep {
  key: SetupStepKey
  title: string
  shortTitle: string
  description: string
}

/**
 * 根据服务端可信部署上下文生成安装步骤。
 *
 * 分发包可编辑并真实测试连接；1Panel/Docker 跳过服务配置页面，安装事务直接
 * 使用服务端环境变量执行迁移。平台密码不会进入浏览器。
 */
export function buildSetupSteps(context: SetupDeploymentContext): SetupStep[] {
  const steps: SetupStep[] = [
    {
      key: 'environment',
      title: '真实环境检测',
      shortTitle: '环境检测',
      description: '实时验证 Java、目录写入、磁盘空间、JVM 内存和数据库驱动。',
    },
    {
      key: 'identity',
      title: '站点与超级管理员',
      shortTitle: '站点设置',
      description: '一次填写站点公开信息和第一个超级管理员账号。',
    },
    {
      key: 'install',
      title: '确认并执行安装',
      shortTitle: '执行安装',
      description: '复核配置后写入运行设置、执行数据库迁移并原子提交安装状态。',
    },
    {
      key: 'complete',
      title: '安装完成',
      shortTitle: '完成',
      description: 'Aquafish 已准备就绪。',
    },
  ]

  if (!context.databaseManaged) {
    steps.splice(1, 0, {
      key: 'services',
      title: '数据库与 Redis',
      shortTitle: '服务配置',
      description: '分发包先选择数据库与可选 Redis，再在双栏页面完成真实连接测试。',
    })
  }

  return steps
}

/** 协议倒计时结束或滚动到底部，任一条件满足即可同意。 */
export function canAcceptAgreement(secondsRemaining: number, readToEnd: boolean): boolean {
  return secondsRemaining <= 0 || readToEnd
}

/** 切换数据库卡片时使用官方默认端口，不保留上一类型的错误端口。 */
export function defaultDatabasePort(type: 'mysql' | 'mariadb' | 'postgresql'): number {
  return type === 'postgresql' ? 5432 : 3306
}

/**
 * 使用用户当前实际打开安装器的 HTTP(S) 来源作为站点地址默认值。
 *
 * 只保留协议、主机和端口，不携带 /setup 等安装路径；file:、扩展协议和非法地址返回空值，
 * 避免把不可访问的本地路径写进站点公开配置。
 */
export function defaultSiteUrl(browserOrigin: string): string {
  try {
    const url = new URL(browserOrigin)
    if (url.protocol !== 'http:' && url.protocol !== 'https:') {
      return ''
    }
    return url.origin
  } catch {
    return ''
  }
}

/**
 * 把后端返回的毫秒转换成用户可读的秒。
 *
 * 例如：
 * 5 ms -> 0.005 秒
 * 125 ms -> 0.125 秒
 * 1000 ms -> 1 秒
 * 1250 ms -> 1.25 秒
 */
export function formatElapsedSeconds(
  elapsedMillis: number,
): string {
  const safeMillis =
    Number.isFinite(elapsedMillis)
      ? Math.max(0, elapsedMillis)
      : 0

  const seconds = safeMillis / 1000
  const precision = seconds < 1 ? 3 : 2

  const value = seconds
    .toFixed(precision)
    .replace(/\.?0+$/, '')

  return `${value} 秒`
}

/**
 * 数据库网络连接成功后，还必须拿到可解释的安装状态。
 * STATE_UNAVAILABLE 不能显示成“数据库已通过”。
 */
export function databaseInspectionAllowsConnectionPass(
  inspection: SetupDatabaseInspection | null,
): boolean {
  return Boolean(
    inspection
      && inspection.mode !== 'STATE_UNAVAILABLE',
  )
}

/**
 * 只有明确识别为 NEW_INSTALL 时才允许首次安装。
 */
export function databaseAllowsNewInstall(
  inspection: SetupDatabaseInspection | null,
): boolean {
  return Boolean(
    inspection
      && inspection.mode === 'NEW_INSTALL'
      && inspection.newInstallAllowed,
  )
}

/**
 * 完整已安装数据库必须进入恢复流程。
 */
export function databaseRequiresRecovery(
  inspection: SetupDatabaseInspection | null,
): boolean {
  return Boolean(
    inspection
      && inspection.mode === 'EXISTING_INSTALLED'
      && inspection.recoveryAllowed,
  )
}

/**
 * 用户必须勾选数据丢失确认，并输入当前数据库名或“重新安装”，
 * 才能通过前端危险操作门槛。
 */
export function databaseReinstallConfirmationReady(
  dataLossConfirmed: boolean,
  confirmationText: string,
  databaseName: string,
): boolean {
  if (!dataLossConfirmed) return false

  const text = confirmationText.trim()
  const expectedName = databaseName.trim()

  return Boolean(
    text
      && (
        text === expectedName
        || text === '重新安装'
      ),
  )
}

/**
 * 根据数据库识别状态和用户选择判断服务步骤能否继续。
 */
export function databaseCanContinue(
  inspection: SetupDatabaseInspection | null,
  reinstallRequested: boolean,
  confirmationReady: boolean,
): boolean {
  if (!inspection) return false

  if (inspection.mode === 'NEW_INSTALL') {
    return inspection.newInstallAllowed
  }

  if (inspection.mode === 'EXISTING_INSTALLED') {
    if (!reinstallRequested) {
      return inspection.recoveryAllowed
    }

    return (
      inspection.fullReinstallAllowed
      && confirmationReady
    )
  }

  if (inspection.mode === 'INCOMPLETE_INSTALLATION') {
    return (
      inspection.residueCleanupAllowed
      && reinstallRequested
      && confirmationReady
    )
  }

  return false
}
