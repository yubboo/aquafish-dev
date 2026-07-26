import type { Plugin } from 'vite'

import {
  AQ_PLUGIN_ENTRY_FILE,
  AQ_PLUGIN_EXTERNALS,
  AQ_PLUGIN_STYLE_FILE,
  AQ_PLUGIN_UI_MANIFEST_FILE,
} from './constants'
import type { AqPluginBuildManifest } from './manifest'

export interface AqPluginUiBundleManifest {
  schemaVersion: 1
  pluginId: string
  pluginVersion: string
  format: 'iife'
  globalName: string
  entry: string
  style: string | null
  externals: readonly string[]
}

type AqOutputBundle = Record<string, { fileName: string }>

function hasBundleFile(bundle: AqOutputBundle, fileName: string): boolean {
  return Object.values(bundle).some(item => item.fileName === fileName)
}

/** 在插件 UI 目录中生成宿主可安全读取的固定产物清单。 */
export function createAqPluginUiManifestPlugin(
  manifest: AqPluginBuildManifest,
  globalName: string,
): Plugin {
  return {
    name: 'aquafish-plugin-ui-manifest',
    apply: 'build',
    generateBundle: {
      order: 'post',
      handler(_options, bundle) {
        if (!hasBundleFile(bundle, AQ_PLUGIN_ENTRY_FILE)) {
          this.error(`插件 UI 缺少入口产物 ${AQ_PLUGIN_ENTRY_FILE}`)
        }
        const uiManifest: AqPluginUiBundleManifest = {
          schemaVersion: 1,
          pluginId: manifest.id,
          pluginVersion: manifest.version,
          format: 'iife',
          globalName,
          entry: AQ_PLUGIN_ENTRY_FILE,
          style: hasBundleFile(bundle, AQ_PLUGIN_STYLE_FILE)
            ? AQ_PLUGIN_STYLE_FILE
            : null,
          externals: AQ_PLUGIN_EXTERNALS,
        }
        this.emitFile({
          type: 'asset',
          fileName: AQ_PLUGIN_UI_MANIFEST_FILE,
          source: `${JSON.stringify(uiManifest, null, 2)}\n`,
        })
      },
    },
  }
}
