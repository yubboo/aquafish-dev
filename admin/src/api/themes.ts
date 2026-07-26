/**
 * 主题领域中非 JSON 的下载接口。
 *
 * 主题列表和生命周期暂时仍通过 admin-workspace 兼容层访问；ZIP 下载使用统一 Axios
 * 二进制请求，避免页面自行解析认证和授权错误。
 */
import { requestAqBlob } from '@aquafish/api-client'

import { aqAdminApiClient } from './aqadmin-api-client'

export function downloadThemeArchive(themeId: string): Promise<Blob> {
  return requestAqBlob(aqAdminApiClient, {
    url: `/api/admin/themes/${encodeURIComponent(themeId)}/export`,
    method: 'GET',
  })
}
