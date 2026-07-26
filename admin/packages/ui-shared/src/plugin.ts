import type { Component } from 'vue'
import type { RouteRecordRaw } from 'vue-router'

/** Aquafish 官方前端应用标识；不使用 Halo 的 console/uc 命名。 */
export const AQ_APPLICATION_IDS = [
  'aqadmin',
  'user',
] as const

export type AqApplicationId = typeof AQ_APPLICATION_IDS[number]

/**
 * 第一阶段稳定扩展点。
 *
 * 新扩展点必须先定义输入输出契约和权限边界，禁止插件使用任意字符串侵入页面内部。
 */
export const AQ_EXTENSION_POINT_IDS = [
  'aqadmin:dashboard:widgets:create',
  'content:article:list-item:operation:create',
  'forum:thread:list-item:operation:create',
  'forum:moderation:panel:create',
  'theme:list:tabs:create',
  'plugin:self:tabs:create',
  'settings:section:create',
  'ai:editor:action:create',
] as const

export type AqExtensionPointId = typeof AQ_EXTENSION_POINT_IDS[number]

/** 扩展点工厂收到的只读宿主信息。 */
export interface AqUiExtensionContext {
  application: AqApplicationId
  pluginName: string
  grantedPermissions: ReadonlySet<string>
}

/** 扩展点可直接返回组件，也可附带只读组件参数。 */
export interface AqUiExtensionRenderResult {
  component: Component
  props?: Readonly<Record<string, unknown>>
}

export type AqUiExtensionRenderable =
  | Component
  | AqUiExtensionRenderResult

/** 插件向一个受控扩展点注册内容的标准结构。 */
export interface AqUiExtensionRegistration<
  T = AqUiExtensionRenderable,
> {
  id: string
  point: AqExtensionPointId
  order?: number
  permissions?: string[]
  create: (context: AqUiExtensionContext) => T | Promise<T>
}

/** Aquafish 插件 UI 入口契约。 */
export interface AqUiPluginDefinition {
  name: string
  displayName?: string
  applications?: AqApplicationId[]
  permissions?: string[]
  components?: Record<string, Component>
  routes?: RouteRecordRaw[]
  extensionPoints?: AqUiExtensionRegistration[]
}

/**
 * 为插件入口保留完整字面量类型，构建器后续可据此完成权限和兼容版本校验。
 */
export function definePlugin<const T extends AqUiPluginDefinition>(definition: T): T {
  return definition
}
