/**
 * 系统平台授权 API 类型与请求封装。
 *
 * 关联 LicenseManagementPage.vue 和 license-status-guard.ts，负责状态查询、授权码激活
 * 与本机取消激活。授权码只在激活请求体中发送，页面只接收脱敏后的 LicenseStatus。
 */
export type LicenseStatusCode =
  | 'NOT_ACTIVATED'
  | 'VALID'
  | 'EXPIRED'
  | 'NOT_YET_VALID'
  | 'INSTANCE_MISMATCH'
  | 'PRODUCT_MISMATCH'
  | 'SUSPENDED'
  | 'REVOKED'
  | 'DEVICE_UNBOUND'
  | 'ONLINE_CHECK_REQUIRED'
  | 'INVALID'
  | 'CONFIGURATION_ERROR'

/**
 * 后端返回的脱敏授权状态。
 *
 * 关联功能：后台授权页、全局授权路由守卫、HTTP 423 自动跳转。
 * 原始授权码不会由后端重新返回，避免浏览器或日志泄露授权凭据。
 */
export interface LicenseStatus {
  status: LicenseStatusCode
  valid: boolean
  usable: boolean
  enforcementEnabled: boolean
  instanceId: string
  licenseId: string | null
  edition: string | null
  customer: string | null
  issuedAt: string | null
  expiresAt: string | null
  features: string[]
  entitlements: Array<{ type: string; id: string }>
  /** 后端校验过的客户授权中心入口；不会携带设备码、订单或管理员凭据。 */
  portalUrl: string | null
  online: {
    enabled: boolean
    state: string
    lastCheckedAt: string | null
    graceExpiresAt: string | null
    nextRefreshAt: string | null
    message: string
  } | null
  message: string
}

interface ApiResult<T> {
  success: boolean
  code: string
  message: string
  data: T
}

/**
 * 解析授权接口统一响应；任何非 2xx 或 success=false 都转换为可展示的 Error。
 */
async function parseApiResult<T>(response: Response): Promise<T> {
  const text = await response.text()
  const body = text ? JSON.parse(text) as ApiResult<T> : null

  if (!response.ok || !body?.success) {
    throw new Error(body?.message || `授权接口请求失败：HTTP ${response.status}`)
  }

  return body.data
}

/** 查询当前设备码、验签状态、有效期与已授权功能项，不返回原始授权码。 */
export async function fetchLicenseStatus(): Promise<LicenseStatus> {
  const response = await fetch('/api/admin/license/status', {
    method: 'GET',
    headers: { Accept: 'application/json' },
  })
  return parseApiResult<LicenseStatus>(response)
}

/** 主动等待一次有超时限制的在线中心查询，供授权页“重新校验”按钮使用。 */
export async function refreshOnlineLicenseStatus(): Promise<LicenseStatus> {
  const response = await fetch('/api/admin/license/online/refresh', {
    method: 'POST',
    headers: { Accept: 'application/json' },
  })
  return parseApiResult<LicenseStatus>(response)
}

/** 把用户粘贴的授权码提交后端验签；后端仅在完整校验成功后覆盖本地授权文件。 */
export async function activateLicense(licenseCode: string): Promise<LicenseStatus> {
  const response = await fetch('/api/admin/license/activation', {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ licenseCode }),
  })
  return parseApiResult<LicenseStatus>(response)
}

/**
 * 使用授权中心 AQO1 短码在线激活。设备码由 Aquafish 后端读取并提交，浏览器不传设备码。
 */
export async function activateOnlineLicense(activationCode: string): Promise<LicenseStatus> {
  const response = await fetch('/api/admin/license/online/activation', {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ activationCode }),
  })
  return parseApiResult<LicenseStatus>(response)
}

/** 删除本机授权文件但保留稳定设备码，随后返回新的未激活状态。 */
export async function deactivateLicense(): Promise<LicenseStatus> {
  const response = await fetch('/api/admin/license/activation', {
    method: 'DELETE',
    headers: { Accept: 'application/json' },
  })
  return parseApiResult<LicenseStatus>(response)
}
