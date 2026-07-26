import { readFileSync, statSync } from 'node:fs'
import { isAbsolute, resolve } from 'node:path'

import { parse } from 'yaml'

import { AQ_PLUGIN_DEFAULT_MANIFEST } from './constants'

const MAX_MANIFEST_BYTES = 256 * 1024
const PLUGIN_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]{0,119}$/

type UnknownRecord = Record<string, unknown>

export interface AqPluginBuildManifest {
  id: string
  displayName: string
  version: string
  requires: string
  path: string
}

function object(value: unknown): UnknownRecord {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as UnknownRecord
    : {}
}

function text(...values: unknown[]): string {
  for (const value of values) {
    const normalized = value === null || value === undefined
      ? ''
      : String(value).trim()
    if (normalized) {
      return normalized
    }
  }
  return ''
}

export function resolveAqPluginManifestPath(
  manifestPath = AQ_PLUGIN_DEFAULT_MANIFEST,
  workingDirectory = process.cwd(),
): string {
  return isAbsolute(manifestPath)
    ? manifestPath
    : resolve(workingDirectory, manifestPath)
}

/**
 * 读取并校验与 Java YamlPluginDescriptorFinder 一致的最小清单字段。
 *
 * 构建器不会执行 YAML 标签或清单中的代码，只读取插件 ID、显示名称和版本。
 */
export function readAqPluginBuildManifest(
  manifestPath = AQ_PLUGIN_DEFAULT_MANIFEST,
  workingDirectory = process.cwd(),
): AqPluginBuildManifest {
  const resolvedPath = resolveAqPluginManifestPath(manifestPath, workingDirectory)
  const stats = statSync(resolvedPath)
  if (!stats.isFile()) {
    throw new Error(`Aquafish 插件清单不是普通文件：${resolvedPath}`)
  }
  if (stats.size > MAX_MANIFEST_BYTES) {
    throw new Error(`Aquafish 插件清单超过 ${MAX_MANIFEST_BYTES} 字节：${resolvedPath}`)
  }

  const root = object(parse(readFileSync(resolvedPath, 'utf8')))
  if (!Object.keys(root).length) {
    throw new Error(`Aquafish 插件清单根节点必须是对象：${resolvedPath}`)
  }
  const metadata = object(root.metadata)
  const spec = object(root.spec)
  const id = text(root.id, root.key, metadata.name)
  if (!PLUGIN_ID_PATTERN.test(id)) {
    throw new Error('插件 ID 只能包含字母、数字、点、下划线和短横线，长度 1-120。')
  }
  const version = text(root.version, spec.version)
  if (!version) {
    throw new Error(`Aquafish 插件清单缺少版本：${resolvedPath}`)
  }

  return {
    id,
    displayName: text(
      root.name,
      spec.displayName,
      metadata.displayName,
      id,
    ),
    version,
    requires: text(root.requires, spec.requires),
    path: resolvedPath,
  }
}

/** 将插件 ID 转换成稳定、合法且与宿主清单绑定的浏览器全局变量名。 */
export function getAqPluginGlobalName(pluginId: string): string {
  if (!PLUGIN_ID_PATTERN.test(pluginId)) {
    throw new Error(`无效的 Aquafish 插件 ID：${pluginId}`)
  }
  return `AqPlugin_${pluginId.replace(/[^A-Za-z0-9_$]/g, '_')}`
}
