import type { AqUiPluginDefinition } from '@aquafish/ui-shared'
import { shallowReactive } from 'vue'
import type { Router } from 'vue-router'

import {
  loadPluginUiCatalog,
  type AqPluginUiCatalog,
  type AqPluginUiDescriptor,
} from '../api/plugins'
import {
  registerAqUiPlugin,
  type AqUiPluginRegistration,
} from './extension-registry'
import { installAqPluginHostGlobals } from './host-globals'

const AUTOMATIC_SYNC_INTERVAL_MS = 30_000

export interface AqPluginUiRuntimeFailure {
  pluginId: string
  stage: 'catalog' | 'style' | 'script' | 'definition' | 'extension'
  message: string
}

export interface AqPluginUiRuntimeState {
  loading: boolean
  loadedPluginIds: string[]
  failures: AqPluginUiRuntimeFailure[]
  lastSynchronizedAt: string
}

export interface AqPluginUiAssetHandle {
  remove: () => void
}

export interface AqPluginUiLoaderDependencies {
  loadCatalog: () => Promise<AqPluginUiCatalog>
  loadScript: (url: string) => Promise<AqPluginUiAssetHandle>
  loadStyle: (url: string) => Promise<AqPluginUiAssetHandle>
  readGlobal: (name: string) => unknown
  clearGlobal: (name: string) => void
}

export interface AqPluginUiLoader {
  state: AqPluginUiRuntimeState
  sync: (force?: boolean) => Promise<void>
  dispose: () => void
}

interface LoadedPlugin {
  descriptor: AqPluginUiDescriptor
  script: AqPluginUiAssetHandle
  style: AqPluginUiAssetHandle | null
  registration: AqUiPluginRegistration
}

/**
 * 创建可测试的插件 UI 加载器。
 *
 * 同步失败不会卸载上一轮已正常加载的插件；只有后端成功返回新目录后，才根据实际启停状态
 * 执行增量卸载和加载，避免临时网络错误导致整个管理端扩展瞬间消失。
 */
export function createAqPluginUiLoader(
  router: Router,
  dependencies: AqPluginUiLoaderDependencies,
): AqPluginUiLoader {
  const state = shallowReactive<AqPluginUiRuntimeState>({
    loading: false,
    loadedPluginIds: [],
    failures: [],
    lastSynchronizedAt: '',
  })
  const loaded = new Map<string, LoadedPlugin>()
  let lastAttempt = 0
  let activeSync: Promise<void> | null = null

  async function performSync(force = false): Promise<void> {
    const now = Date.now()
    if (!force && now - lastAttempt < AUTOMATIC_SYNC_INTERVAL_MS) {
      return
    }
    if (activeSync) {
      return activeSync
    }
    lastAttempt = now
    state.loading = true
    activeSync = synchronize()
      .finally(() => {
        state.loading = false
        activeSync = null
      })
    return activeSync
  }

  async function synchronize(): Promise<void> {
    let catalog: AqPluginUiCatalog
    try {
      catalog = await dependencies.loadCatalog()
    } catch (error) {
      state.failures = [{
        pluginId: 'aqadmin',
        stage: 'catalog',
        message: errorMessage(error),
      }]
      throw error
    }

    const failures: AqPluginUiRuntimeFailure[] = catalog.failures.map(
      failure => ({
        pluginId: failure.pluginId,
        stage: 'catalog',
        message: failure.message,
      }),
    )
    const desired = new Map(
      catalog.items.map(item => [item.pluginId, item]),
    )

    for (const [pluginId, current] of loaded) {
      const next = desired.get(pluginId)
      if (!next
        || descriptorFingerprint(next)
          !== descriptorFingerprint(current.descriptor)) {
        unload(pluginId)
      }
    }

    for (const descriptor of catalog.items) {
      if (loaded.has(descriptor.pluginId)) {
        continue
      }
      try {
        const plugin = await loadOne(descriptor)
        loaded.set(descriptor.pluginId, plugin)
        failures.push(
          ...plugin.registration.failures.map(message => ({
            pluginId: descriptor.pluginId,
            stage: 'extension' as const,
            message,
          })),
        )
      } catch (error) {
        failures.push({
          pluginId: descriptor.pluginId,
          stage: failureStage(error),
          message: errorMessage(error),
        })
      }
    }

    state.loadedPluginIds = [...loaded.keys()].sort(
      (left, right) => left.localeCompare(right),
    )
    state.failures = failures
    state.lastSynchronizedAt = new Date().toISOString()
  }

  async function loadOne(
    descriptor: AqPluginUiDescriptor,
  ): Promise<LoadedPlugin> {
    const base = pluginAssetBase(descriptor)
    let style: AqPluginUiAssetHandle | null = null
    let script: AqPluginUiAssetHandle | null = null
    if (dependencies.readGlobal(descriptor.globalName) !== undefined) {
      throw stageError(
        'definition',
        `插件全局变量已被占用：${descriptor.globalName}`,
      )
    }
    try {
      if (descriptor.style) {
        style = await loadWithStage(
          'style',
          () => dependencies.loadStyle(
            `${base}${descriptor.style}${versionQuery(descriptor)}`,
          ),
        )
      }
      script = await loadWithStage(
        'script',
        () => dependencies.loadScript(
          `${base}${descriptor.entry}${versionQuery(descriptor)}`,
        ),
      )
      const definition = definitionFromGlobal(
        dependencies.readGlobal(descriptor.globalName),
      )
      const registration = await loadWithStage(
        'definition',
        () => registerAqUiPlugin(
          definition,
          {
            application: 'aqadmin',
            pluginName: descriptor.pluginId,
            grantedPermissions: new Set(
              descriptor.grantedPermissions || [],
            ),
          },
          router,
        ),
      )
      return {
        descriptor,
        script,
        style,
        registration,
      }
    } catch (error) {
      script?.remove()
      style?.remove()
      dependencies.clearGlobal(descriptor.globalName)
      throw error
    }
  }

  function unload(pluginId: string): void {
    const plugin = loaded.get(pluginId)
    if (!plugin) return
    plugin.registration.dispose()
    plugin.script.remove()
    plugin.style?.remove()
    dependencies.clearGlobal(plugin.descriptor.globalName)
    loaded.delete(pluginId)
  }

  return {
    state,
    sync: performSync,
    dispose() {
      for (const pluginId of [...loaded.keys()]) {
        unload(pluginId)
      }
      state.loadedPluginIds = []
    },
  }
}

let browserLoader: AqPluginUiLoader | null = null

/** aqadmin 共享的插件 UI 运行状态，供插件管理页展示诊断信息。 */
export const aqPluginUiRuntimeState = shallowReactive<AqPluginUiRuntimeState>({
  loading: false,
  loadedPluginIds: [],
  failures: [],
  lastSynchronizedAt: '',
})

/** 安装 aqadmin 插件宿主全局和浏览器资源加载器。 */
export function installAqAdminPluginUiRuntime(router: Router): void {
  if (browserLoader) return
  installAqPluginHostGlobals()
  browserLoader = createAqPluginUiLoader(
    router,
    browserDependencies(),
  )
  mirrorRuntimeState(browserLoader.state)
}

/** 手动同步用于插件启停和重扫后立即刷新 UI。 */
export async function syncAqAdminPluginUi(
  force = false,
): Promise<void> {
  if (!browserLoader) {
    throw new Error('aqadmin 插件 UI 运行时尚未安装。')
  }
  try {
    await browserLoader.sync(force)
  } finally {
    mirrorRuntimeState(browserLoader.state)
  }
}

function mirrorRuntimeState(source: AqPluginUiRuntimeState): void {
  aqPluginUiRuntimeState.loading = source.loading
  aqPluginUiRuntimeState.loadedPluginIds = [...source.loadedPluginIds]
  aqPluginUiRuntimeState.failures = [...source.failures]
  aqPluginUiRuntimeState.lastSynchronizedAt = source.lastSynchronizedAt
}

function browserDependencies(): AqPluginUiLoaderDependencies {
  const globalScope = globalThis as Record<string, unknown>
  return {
    loadCatalog: loadPluginUiCatalog,
    loadScript: url => appendScript(url),
    loadStyle: url => appendStyle(url),
    readGlobal: name => globalScope[name],
    clearGlobal: name => clearGlobal(globalScope, name),
  }
}

function appendScript(url: string): Promise<AqPluginUiAssetHandle> {
  const script = document.createElement('script')
  script.src = url
  script.async = true
  script.type = 'text/javascript'
  script.dataset.aqPluginAsset = 'script'
  return appendAsset(script)
}

function appendStyle(url: string): Promise<AqPluginUiAssetHandle> {
  const link = document.createElement('link')
  link.rel = 'stylesheet'
  link.href = url
  link.dataset.aqPluginAsset = 'style'
  return appendAsset(link)
}

function appendAsset(
  element: HTMLScriptElement | HTMLLinkElement,
): Promise<AqPluginUiAssetHandle> {
  return new Promise((resolve, reject) => {
    element.onload = () => resolve({
      remove: () => element.remove(),
    })
    element.onerror = () => {
      element.remove()
      reject(new Error(`插件静态资源加载失败：${
        element instanceof HTMLScriptElement
          ? element.src
          : element.href
      }`))
    }
    document.head.append(element)
  })
}

function clearGlobal(
  target: Record<string, unknown>,
  name: string,
): void {
  if (!Object.prototype.hasOwnProperty.call(target, name)) {
    return
  }
  if (!Reflect.deleteProperty(target, name)) {
    try {
      target[name] = undefined
    } catch {
      throw new Error(`无法清理插件全局变量：${name}`)
    }
  }
}

function definitionFromGlobal(value: unknown): AqUiPluginDefinition {
  if (!value || typeof value !== 'object') {
    throw stageError('definition', '插件入口没有创建预期全局对象。')
  }
  const moduleValue = value as Record<string, unknown>
  const candidate = moduleValue.default ?? moduleValue
  if (!candidate || typeof candidate !== 'object') {
    throw stageError('definition', '插件入口没有导出 definePlugin 定义。')
  }
  return candidate as unknown as AqUiPluginDefinition
}

function pluginAssetBase(descriptor: AqPluginUiDescriptor): string {
  return `/api/admin/plugins/${
    encodeURIComponent(descriptor.pluginId)
  }/ui/`
}

function versionQuery(descriptor: AqPluginUiDescriptor): string {
  return `?v=${encodeURIComponent(descriptor.pluginVersion)}`
}

function descriptorFingerprint(
  descriptor: AqPluginUiDescriptor,
): string {
  return JSON.stringify({
    pluginId: descriptor.pluginId,
    pluginVersion: descriptor.pluginVersion,
    globalName: descriptor.globalName,
    entry: descriptor.entry,
    style: descriptor.style,
    externals: [...descriptor.externals],
    grantedPermissions: [...descriptor.grantedPermissions].sort(),
  })
}

async function loadWithStage<T>(
  stage: AqPluginUiRuntimeFailure['stage'],
  action: () => Promise<T>,
): Promise<T> {
  try {
    return await action()
  } catch (error) {
    if (isStageError(error)) throw error
    throw stageError(stage, errorMessage(error))
  }
}

function stageError(
  stage: AqPluginUiRuntimeFailure['stage'],
  message: string,
): Error {
  const error = new Error(message) as Error & {
    aqPluginUiStage?: AqPluginUiRuntimeFailure['stage']
  }
  error.aqPluginUiStage = stage
  return error
}

function isStageError(error: unknown): boolean {
  return error instanceof Error
    && 'aqPluginUiStage' in error
}

function failureStage(
  error: unknown,
): AqPluginUiRuntimeFailure['stage'] {
  if (isStageError(error)) {
    return (
      error as Error & {
        aqPluginUiStage: AqPluginUiRuntimeFailure['stage']
      }
    ).aqPluginUiStage
  }
  return 'definition'
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}
