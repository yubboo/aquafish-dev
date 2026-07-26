import {
  AQ_APPLICATION_IDS,
  AQ_EXTENSION_POINT_IDS,
  type AqExtensionPointId,
  type AqUiExtensionContext,
  type AqUiExtensionRenderable,
  type AqUiPluginDefinition,
} from '@aquafish/ui-shared'
import {
  shallowRef,
  type Component,
  type ShallowRef,
} from 'vue'
import type { Router, RouteRecordRaw } from 'vue-router'

const PLUGIN_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]{0,119}$/
const REGISTRATION_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]{0,119}$/
const EXTENSION_POINTS = new Set<string>(AQ_EXTENSION_POINT_IDS)
const APPLICATIONS = new Set<string>(AQ_APPLICATION_IDS)

export interface AqResolvedUiExtension {
  key: string
  pluginName: string
  id: string
  point: AqExtensionPointId
  order: number
  component: Component
  props: Readonly<Record<string, unknown>>
}

export interface AqUiPluginRegistration {
  pluginName: string
  failures: string[]
  dispose: () => void
}

const extensions: ShallowRef<AqResolvedUiExtension[]> = shallowRef([])

/** 返回指定扩展点的响应式只读快照。 */
export function aqUiExtensions(): ShallowRef<AqResolvedUiExtension[]> {
  return extensions
}

/**
 * 注册一个已经过后端包清单校验的插件定义。
 *
 * 插件级权限缺失会拒绝整个定义；单个扩展点权限缺失或工厂失败只隔离该扩展，不影响同插件
 * 的其他扩展。插件路由必须位于 /admin/plugins/<pluginId> 命名空间。
 */
export async function registerAqUiPlugin(
  definition: AqUiPluginDefinition,
  context: AqUiExtensionContext,
  router: Router,
): Promise<AqUiPluginRegistration> {
  validatePluginIdentity(definition, context.pluginName)
  if (definition.applications?.some(
    application => !APPLICATIONS.has(application),
  )) {
    throw new Error('插件 UI 声明了未知的应用标识。')
  }
  const granted = context.grantedPermissions
  const missingPluginPermissions = missingPermissions(
    definition.permissions,
    granted,
  )
  if (missingPluginPermissions.length) {
    throw new Error(
      `插件缺少已批准能力：${missingPluginPermissions.join(', ')}`,
    )
  }

  if (definition.applications?.length
    && !definition.applications.includes('aqadmin')) {
    return registration(context.pluginName, [], [], [])
  }

  const routeRemovers = validateAndRegisterRoutes(
    definition.routes,
    context.pluginName,
    router,
  )
  const created: AqResolvedUiExtension[] = []
  const failures: string[] = []
  const seenIds = new Set<string>()

  try {
    for (const item of definition.extensionPoints || []) {
      if (!REGISTRATION_ID_PATTERN.test(item.id)) {
        failures.push(`扩展注册 ID 不合法：${item.id}`)
        continue
      }
      if (seenIds.has(item.id)) {
        failures.push(`扩展注册 ID 重复：${item.id}`)
        continue
      }
      seenIds.add(item.id)
      if (!EXTENSION_POINTS.has(item.point)) {
        failures.push(`未知扩展点：${item.point}`)
        continue
      }
      const missing = missingPermissions(item.permissions, granted)
      if (missing.length) {
        failures.push(
          `扩展 ${item.id} 缺少已批准能力：${missing.join(', ')}`,
        )
        continue
      }
      try {
        const rendered = normalizeRenderable(await item.create(context))
        created.push({
          key: `${context.pluginName}:${item.id}`,
          pluginName: context.pluginName,
          id: item.id,
          point: item.point,
          order: Number.isFinite(item.order) ? Number(item.order) : 0,
          component: rendered.component,
          props: rendered.props,
        })
      } catch (error) {
        failures.push(
          `扩展 ${item.id} 创建失败：${errorMessage(error)}`,
        )
      }
    }
  } catch (error) {
    routeRemovers.forEach(remove => remove())
    throw error
  }

  extensions.value = [...extensions.value, ...created]
    .sort((left, right) => {
      if (left.point !== right.point) {
        return left.point.localeCompare(right.point)
      }
      if (left.order !== right.order) {
        return left.order - right.order
      }
      return left.key.localeCompare(right.key)
    })
  return registration(
    context.pluginName,
    routeRemovers,
    created,
    failures,
  )
}

function registration(
  pluginName: string,
  routeRemovers: Array<() => void>,
  created: AqResolvedUiExtension[],
  failures: string[],
): AqUiPluginRegistration {
  let disposed = false
  return {
    pluginName,
    failures: [...failures],
    dispose() {
      if (disposed) return
      disposed = true
      const createdKeys = new Set(created.map(item => item.key))
      extensions.value = extensions.value.filter(
        item => !createdKeys.has(item.key),
      )
      routeRemovers.forEach(remove => remove())
    },
  }
}

function validatePluginIdentity(
  definition: AqUiPluginDefinition,
  expectedPluginName: string,
): void {
  if (!PLUGIN_ID_PATTERN.test(expectedPluginName)) {
    throw new Error('宿主提供的插件 ID 不合法。')
  }
  if (!definition || typeof definition !== 'object') {
    throw new Error('插件 UI 入口没有导出有效定义。')
  }
  if (definition.name !== expectedPluginName) {
    throw new Error('插件 UI 定义名称与已安装插件 ID 不一致。')
  }
}

function validateAndRegisterRoutes(
  routes: RouteRecordRaw[] | undefined,
  pluginName: string,
  router: Router,
): Array<() => void> {
  const values = routes || []
  const names = new Set<string>()
  const paths = new Set<string>()
  values.forEach((route) => {
    validateRoute(route, pluginName)
    const name = String(route.name)
    if (names.has(name) || paths.has(route.path)) {
      throw new Error('同一插件不能重复注册路由名称或路径。')
    }
    names.add(name)
    paths.add(route.path)
  })
  const removers: Array<() => void> = []
  try {
    for (const route of values) {
      removers.push(router.addRoute('admin.root', route))
    }
    return removers
  } catch (error) {
    removers.forEach(remove => remove())
    throw error
  }
}

function validateRoute(
  route: RouteRecordRaw,
  pluginName: string,
): void {
  const pathPrefix = `plugins/${pluginName}`
  const namePrefix = `plugin.${pluginName}.`
  if (!route.path
    || route.path.startsWith('/')
    || route.path.includes('..')
    || (route.path !== pathPrefix
      && !route.path.startsWith(`${pathPrefix}/`))) {
    throw new Error(
      `插件路由必须位于 /admin/${pathPrefix} 命名空间。`,
    )
  }
  if (typeof route.name !== 'string'
    || !route.name.startsWith(namePrefix)) {
    throw new Error(`插件路由名称必须以 ${namePrefix} 开头。`)
  }
  if (route.children?.length || route.redirect || route.alias) {
    throw new Error('插件路由暂不允许 children、redirect 或 alias。')
  }
}

function missingPermissions(
  required: string[] | undefined,
  granted: ReadonlySet<string>,
): string[] {
  return [...new Set(required || [])]
    .filter(permission => !granted.has(permission))
}

function normalizeRenderable(
  value: AqUiExtensionRenderable,
): {
    component: Component
    props: Readonly<Record<string, unknown>>
  } {
  if (typeof value === 'function') {
    return { component: value as Component, props: {} }
  }
  if (!value || typeof value !== 'object') {
    throw new Error('扩展点工厂必须返回 Vue 组件。')
  }
  if ('component' in value) {
    const candidate = value as {
      component?: unknown
      props?: Readonly<Record<string, unknown>>
    }
    if (!isComponent(candidate.component)) {
      throw new Error('扩展点结果中的 component 不是 Vue 组件。')
    }
    return {
      component: candidate.component,
      props: candidate.props || {},
    }
  }
  if (!isComponent(value)) {
    throw new Error('扩展点工厂必须返回 Vue 组件。')
  }
  return { component: value, props: {} }
}

function isComponent(value: unknown): value is Component {
  return typeof value === 'function'
    || Boolean(value && typeof value === 'object')
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}
