/**
 * aqadmin 插件 UI 宿主 API。
 *
 * 服务端只返回已启动并通过固定清单协议校验的本机插件，不接受插件提供远程入口地址。
 */
import { requestAqData } from '@aquafish/api-client'

import { aqAdminApiClient } from './aqadmin-api-client'

export interface AqPluginUiDescriptor {
  pluginId: string
  pluginVersion: string
  globalName: string
  entry: 'main.js'
  style: 'style.css' | null
  externals: string[]
  grantedPermissions: string[]
}

export interface AqPluginUiCatalogFailure {
  pluginId: string
  message: string
}

export interface AqPluginUiCatalog {
  items: AqPluginUiDescriptor[]
  failures: AqPluginUiCatalogFailure[]
}

/** 读取当前管理端可以加载的插件 UI 清单。 */
export function loadPluginUiCatalog(): Promise<AqPluginUiCatalog> {
  return requestAqData<AqPluginUiCatalog>(
    aqAdminApiClient,
    {
      url: '/api/admin/plugins/ui',
      method: 'GET',
    },
  )
}

