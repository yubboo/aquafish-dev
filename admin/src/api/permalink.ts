/**
 * aqadmin 固定链接设置领域 API。
 *
 * 页面只维护表单交互，真实预览、校验和持久化全部通过统一 Axios 客户端交给后端。
 */
import { requestAqData } from '@aquafish/api-client'

import { aqAdminApiClient } from './aqadmin-api-client'

export type PermalinkMode = 'short' | 'halo' | 'discuz' | 'custom'

export interface PermalinkSettings {
  mode: PermalinkMode
  articlePattern: string
  pagePattern: string
  categoryPattern: string
  tagPattern: string
  forumPattern: string
  threadPattern: string
  userPattern: string
  enableDiscuzCompat: boolean
  enableHaloCompat: boolean
  enableOldLinkRedirect: boolean
}

export interface PermalinkPreview {
  mode: PermalinkMode
  article: string
  page: string
  category: string
  tag: string
  forum: string
  thread: string
  user: string
  examples: string[]
}

export interface PermalinkSettingsResponse {
  settings: PermalinkSettings
  preview: PermalinkPreview
  storagePath: string
}

export function fetchPermalinkSettings(): Promise<PermalinkSettingsResponse> {
  return requestAqData<PermalinkSettingsResponse>(aqAdminApiClient, {
    url: '/api/admin/settings/permalink',
    method: 'GET',
  })
}

export function previewPermalinkSettings(
  settings: PermalinkSettings,
): Promise<PermalinkPreview> {
  return requestAqData<PermalinkPreview>(aqAdminApiClient, {
    url: '/api/admin/settings/permalink/preview',
    method: 'POST',
    data: settings,
  })
}

export function savePermalinkSettings(
  settings: PermalinkSettings,
): Promise<PermalinkSettingsResponse> {
  return requestAqData<PermalinkSettingsResponse>(aqAdminApiClient, {
    url: '/api/admin/settings/permalink',
    method: 'PUT',
    data: settings,
  })
}
