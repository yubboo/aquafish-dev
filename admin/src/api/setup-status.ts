/**
 * 安装状态接口返回结构。
 *
 * 关联后端：GET /api/setup/status。
 * 该接口只暴露首次安装导航需要的安全字段，不包含服务器路径、数据库密码
 * 或 install.lock 具体内容。
 */
import { requestAqData } from '@aquafish/api-client'

import { aqAdminApiClient } from './aqadmin-api-client'

export interface SetupStatus {
  installed: boolean
  locked: boolean
  canInstall: boolean
  stateAvailable: boolean
  databaseState: string
  applicationConfigExists: boolean
  installedAt: string | null
  note: string | null
}

/**
 * 从后端读取数据库权威安装状态。
 *
 * 功能边界：
 * 1. 只负责读取和校验状态，不在前端猜测 install.lock；
 * 2. 不缓存“未安装”，确保安装完成后的下一次导航能立即重新确认；
 * 3. 网络失败或响应结构无效时抛出异常，由路由守卫安全送往安装状态页。
 */
export function fetchSetupStatus(): Promise<SetupStatus> {
  return requestAqData<SetupStatus>(aqAdminApiClient, {
    url: '/api/setup/status',
    method: 'GET',
  })
}
