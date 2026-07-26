/** P0-3-2B-15C：弹窗重装确认、耗时恢复与服务页视觉收口：回归测试。 */
import { describe, expect, it } from 'vitest'
import type { SetupDeploymentContext } from '../../api/setup'
import {
  buildSetupSteps,
  canAcceptAgreement,
  databaseAllowsNewInstall,
  databaseCanContinue,
  databaseInspectionAllowsConnectionPass,
  databaseReinstallConfirmationReady,
  databaseRequiresRecovery,
  defaultDatabasePort,
  defaultSiteUrl,
  formatElapsedSeconds,
} from './setup-wizard'

/**
 * 安装向导纯逻辑回归测试。
 * 重点防止后续视觉调整重新拆出冗余步骤、绕过协议门槛或填错数据库默认端口。
 */
function context(overrides: Partial<SetupDeploymentContext> = {}): SetupDeploymentContext {
  return {
    deploymentType: 'archive',
    deploymentLabel: '分发包部署',
    databaseSource: 'installer',
    redisSource: 'installer',
    databaseManaged: false,
    redisManaged: false,
    redisConfigured: false,
    licenseRequired: true,
    licenseVersion: '1.0',
    environmentReady: true,
    database: {
      type: 'mysql',
      host: '127.0.0.1',
      port: 3306,
      name: 'aquafish',
      username: 'aquafish',
      tablePrefix: 'aq_',
      passwordConfigured: false,
    },
    checks: [],
    ...overrides,
  }
}

describe('buildSetupSteps', () => {
  it('只生成环境、服务、站点、安装、完成五个正式步骤', () => {
    expect(buildSetupSteps(context()).map(step => step.key)).toEqual([
      'environment',
      'services',
      'identity',
      'install',
      'complete',
    ])
  })

  it('1Panel 和 Docker 托管模式不重复显示数据库配置或连接测试步骤', () => {
    expect(buildSetupSteps(context({
      deploymentType: 'onepanel',
      databaseManaged: true,
      redisManaged: true,
    })).map(step => step.key)).toEqual([
      'environment',
      'identity',
      'install',
      'complete',
    ])
  })
})

describe('agreement gate', () => {
  it('倒计时未结束且未读完时不能同意', () => {
    expect(canAcceptAgreement(7, false)).toBe(false)
  })

  it('倒计时结束或滚动到底部时均可同意', () => {
    expect(canAcceptAgreement(0, false)).toBe(true)
    expect(canAcceptAgreement(9, true)).toBe(true)
  })
})

describe('database defaults', () => {
  it('三种数据库使用正确默认端口', () => {
    expect(defaultDatabasePort('mysql')).toBe(3306)
    expect(defaultDatabasePort('mariadb')).toBe(3306)
    expect(defaultDatabasePort('postgresql')).toBe(5432)
  })
})

describe('site url default', () => {
  it('分发安装自动使用浏览器当前 IP、协议和端口', () => {
    expect(defaultSiteUrl('http://156.239.2.141:8080')).toBe('http://156.239.2.141:8080')
    expect(defaultSiteUrl('https://www.example.com')).toBe('https://www.example.com')
  })

  it('不把安装路径、文件地址或非法地址写入站点配置', () => {
    expect(defaultSiteUrl('https://www.example.com/setup?step=3')).toBe('https://www.example.com')
    expect(defaultSiteUrl('file:///D:/aquafish/index.html')).toBe('')
    expect(defaultSiteUrl('not-a-url')).toBe('')
  })
})

describe('connection elapsed time', () => {
  it('把毫秒耗时转换为秒', () => {
    expect(
      formatElapsedSeconds(5),
    ).toBe('0.005 秒')

    expect(
      formatElapsedSeconds(125),
    ).toBe('0.125 秒')

    expect(
      formatElapsedSeconds(1000),
    ).toBe('1 秒')

    expect(
      formatElapsedSeconds(1250),
    ).toBe('1.25 秒')
  })

  it('非法或负数耗时显示为 0 秒', () => {
    expect(
      formatElapsedSeconds(Number.NaN),
    ).toBe('0 秒')

    expect(
      formatElapsedSeconds(-10),
    ).toBe('0 秒')
  })
})

describe('database connection pass gate', () => {
  const inspection = {
    newInstallAllowed: false,
    recoveryAllowed: false,
    residueCleanupAllowed: false,
    fullReinstallAllowed: false,
    installationState: '',
    currentVersion: '',
    latestVersion: 'V22',
    pendingMigrations: 0,
    existingAquafishTables: 0,
    expectedAquafishTables: 71,
    migrationsTableExists: false,
    migrationHistoryConsistent: true,
    installedAt: '',
    installedVersion: '',
    note: '',
  }

  it('STATE_UNAVAILABLE 不能显示为数据库已通过', () => {
    expect(
      databaseInspectionAllowsConnectionPass({
        ...inspection,
        mode: 'STATE_UNAVAILABLE',
      }),
    ).toBe(false)
  })

  it.each([
    'NEW_INSTALL',
    'EXISTING_INSTALLED',
    'INCOMPLETE_INSTALLATION',
    'INCOMPATIBLE_DATABASE',
  ] as const)('%s 是可解释的识别结果', mode => {
    expect(
      databaseInspectionAllowsConnectionPass({
        ...inspection,
        mode,
      }),
    ).toBe(true)
  })
})

describe('database identity gate', () => {
  const base = {
    newInstallAllowed: false,
    recoveryAllowed: false,
    residueCleanupAllowed: false,
    fullReinstallAllowed: false,
    installationState: '',
    currentVersion: '',
    latestVersion: 'V22',
    pendingMigrations: 0,
    existingAquafishTables: 0,
    expectedAquafishTables: 71,
    migrationsTableExists: false,
    migrationHistoryConsistent: true,
    installedAt: '',
    installedVersion: '',
    note: '',
  }

  it('空前缀数据库允许首次安装', () => {
    expect(
      databaseAllowsNewInstall({
        ...base,
        mode: 'NEW_INSTALL',
        newInstallAllowed: true,
      }),
    ).toBe(true)
  })

  it('已安装数据库只允许恢复', () => {
    const installed = {
      ...base,
      mode: 'EXISTING_INSTALLED' as const,
      recoveryAllowed: true,
    }

    expect(
      databaseAllowsNewInstall(
        installed,
      ),
    ).toBe(false)

    expect(
      databaseRequiresRecovery(
        installed,
      ),
    ).toBe(true)
  })
})

describe('database recovery and reinstall choice', () => {
  const installed = {
    mode: 'EXISTING_INSTALLED' as const,
    newInstallAllowed: false,
    recoveryAllowed: true,
    residueCleanupAllowed: false,
    fullReinstallAllowed: true,
    installationState: 'INSTALLED',
    currentVersion: 'V22',
    latestVersion: 'V22',
    pendingMigrations: 0,
    existingAquafishTables: 71,
    expectedAquafishTables: 71,
    migrationsTableExists: true,
    migrationHistoryConsistent: true,
    installedAt: '2026-07-20T00:00:00Z',
    installedVersion: '22',
    note: '',
  }

  it('默认不重装时允许恢复已有系统', () => {
    expect(
      databaseCanContinue(
        installed,
        false,
        false,
      ),
    ).toBe(true)
  })

  it('勾选重装后必须完成全部危险确认', () => {
    expect(
      databaseReinstallConfirmationReady(
        false,
        'erfish',
        'erfish',
      ),
    ).toBe(false)

    expect(
      databaseReinstallConfirmationReady(
        true,
        'wrong',
        'erfish',
      ),
    ).toBe(false)

    expect(
      databaseReinstallConfirmationReady(
        true,
        'erfish',
        'erfish',
      ),
    ).toBe(true)

    expect(
      databaseReinstallConfirmationReady(
        true,
        '重新安装',
        'erfish',
      ),
    ).toBe(true)

    expect(
      databaseCanContinue(
        installed,
        true,
        false,
      ),
    ).toBe(false)

    expect(
      databaseCanContinue(
        installed,
        true,
        true,
      ),
    ).toBe(true)
  })
})
