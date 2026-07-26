/**
 * Aquafish 首次安装 API 类型与请求封装。
 *
 * 关联功能：安装状态、真实环境检测、数据库/Redis 连接测试、数据库迁移和最终提交。
 * 数据库与 Redis 密码只允许出现在请求体中，任何响应类型都不声明密码字段。
 */
import { requestAqData } from '@aquafish/api-client'

import { setupMaintenanceRequestHeaders } from '../router/setup-maintenance-mode'
import {
  aqAdminApiClient,
  toAqRequestConfig,
} from './aqadmin-api-client'

export interface InstallStatus {
  installed: boolean
  locked: boolean
  canInstall: boolean
  stateAvailable: boolean
  databaseState: string
  applicationConfigExists: boolean
  installedAt: string | null
  note: string | null
}

export interface SetupEnvironmentCheck {
  key: string
  label: string
  passed: boolean
  required: boolean
  detail: string
}

export interface SetupDatabaseSummary {
  type: string
  host: string
  port: number
  name: string
  username: string
  tablePrefix: string
  passwordConfigured: boolean
}

export interface SetupDeploymentContext {
  deploymentType: 'archive' | 'docker' | 'onepanel'
  deploymentLabel: string
  databaseSource: 'installer' | 'environment'
  redisSource: 'installer' | 'environment'
  databaseManaged: boolean
  redisManaged: boolean
  redisConfigured: boolean
  licenseRequired: boolean
  licenseVersion: string
  environmentReady: boolean
  database: SetupDatabaseSummary
  checks: SetupEnvironmentCheck[]
}

export interface ConnectionTestResult {
  connected: boolean
  skipped?: boolean
  elapsedMillis: number
  message?: string
}

export type SetupDatabaseMode =
  | 'NEW_INSTALL'
  | 'EXISTING_INSTALLED'
  | 'INCOMPLETE_INSTALLATION'
  | 'INCOMPATIBLE_DATABASE'
  | 'STATE_UNAVAILABLE'

export interface SetupDatabaseInspection {
  mode: SetupDatabaseMode
  newInstallAllowed: boolean
  recoveryAllowed: boolean
  residueCleanupAllowed: boolean
  fullReinstallAllowed: boolean
  installationState: string
  currentVersion: string
  latestVersion: string
  pendingMigrations: number
  existingAquafishTables: number
  expectedAquafishTables: number
  migrationsTableExists: boolean
  migrationHistoryConsistent: boolean
  installedAt: string
  installedVersion: string
  note: string
}

export interface SetupExistingInstallationRecoveryResult {
  recovered: boolean
  applicationConfigFile: string
  installLockFile: string
  installedAt: string
  databaseName: string
  tablePrefix: string
  message: string
}

export interface SetupDatabaseResetResult {
  reset: boolean
  previousMode: SetupDatabaseMode
  currentMode: SetupDatabaseMode
  processedTableCount: number
  databaseName: string
  tablePrefix: string
  message: string
}

export interface DatabaseTableStatus {
  logicalName: string
  tableName: string
  exists: boolean
  action: string
}

export interface DatabaseMigrationPreview {
  connected: boolean
  canMigrate: boolean
  migrationsTableExists: boolean
  unmanagedDatabase: boolean
  migrationsTable: string
  currentVersion: string
  pendingMigrations: number
  tables: DatabaseTableStatus[]
  note: string
  errorMessage: string
}

export interface DatabaseMigrationResult {
  databaseType: 'mysql' | 'mariadb' | 'postgresql'
  previousVersion: string
  currentVersion: string
  pendingBefore: number
  pendingAfter: number
  migrated: boolean
  message: string
}

/**
 * 统一检查 HTTP 状态与 Aquafish 业务状态；业务失败即使返回 HTTP 200 也会抛错，
 * 因而错误的数据库或 Redis 参数无法被前端当成“测试通过”。
 */
export async function setupApi<T>(url: string, init?: RequestInit): Promise<T> {
  const requestInit: RequestInit = {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...setupMaintenanceRequestHeaders(),
      ...(init?.headers || {}),
    },
  }
  return requestAqData<T, BodyInit | null>(
    aqAdminApiClient,
    toAqRequestConfig(url, requestInit),
  )
}

/** 以 JSON POST 调用安装写接口，未传 data 时不发送空请求体。 */
export function setupPost<T>(url: string, data?: unknown): Promise<T> {
  return setupApi<T>(url, {
    method: 'POST',
    ...(data === undefined ? {} : { body: JSON.stringify(data) }),
  })
}
