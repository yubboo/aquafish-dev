/**
 * 后台页面生命周期内共享的脱敏授权状态。
 *
 * 路由守卫、侧栏菜单和授权页面共用这一份状态，避免每个组件重复请求。后端仍会对
 * 每次业务 API 实时验签，所以这里的缓存只优化界面，不构成安全边界。
 */
import { shallowRef } from 'vue'
import { fetchLicenseStatus, type LicenseStatus } from '../api/license'

export const currentLicenseStatus = shallowRef<LicenseStatus | null>(null)

let pendingRequest: Promise<LicenseStatus> | null = null

/** 读取授权状态；并发调用共用同一个请求，force=true 用于激活后的主动复核。 */
export async function loadLicenseStatus(force = false): Promise<LicenseStatus> {
  if (!force && currentLicenseStatus.value) {
    return currentLicenseStatus.value
  }
  if (!force && pendingRequest) {
    return pendingRequest
  }

  pendingRequest = fetchLicenseStatus()
    .then((status) => {
      currentLicenseStatus.value = status
      return status
    })
    .finally(() => {
      pendingRequest = null
    })
  return pendingRequest
}

/** 激活或取消激活接口已经返回最新状态时，立即同步所有菜单和路由。 */
export function updateLicenseStatus(status: LicenseStatus): void {
  currentLicenseStatus.value = status
}

/** 登录退出或状态未知时清空界面缓存，下一次导航会重新向后端查询。 */
export function clearLicenseStatus(): void {
  currentLicenseStatus.value = null
  pendingRequest = null
}
