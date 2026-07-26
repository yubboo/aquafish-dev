/**
 * aqadmin 唯一 Axios 客户端。
 *
 * 页面只能通过领域 API 间接使用本实例，禁止自行 axios.create() 绕过 Cookie、CSRF
 * 和授权状态处理。
 */
import {
  createAqApiClient,
  type AqRequestConfig,
} from '@aquafish/api-client'

import {
  redirectToAdminFeatureRequired,
  redirectToAdminLicense,
  redirectToAdminLogin,
} from './admin-api-navigation'

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')

export const aqAdminApiClient = createAqApiClient({
  baseURL: API_BASE_URL,
  onUnauthorized: redirectToAdminLogin,
  onLicenseRequired: redirectToAdminLicense,
  onFeatureRequired: redirectToAdminFeatureRequired,
})

/**
 * 把迁移前的 RequestInit 适配到统一 Axios 配置。
 *
 * 迁移完成后页面不再直接使用它；领域 API 仍可借此保持现有函数签名和上传 FormData。
 */
export function toAqRequestConfig(
  url: string,
  init?: RequestInit,
): AqRequestConfig<BodyInit | null> {
  return {
    url,
    method: init?.method,
    headers: init?.headers as AqRequestConfig['headers'],
    data: init?.body,
    signal: init?.signal || undefined,
  }
}
