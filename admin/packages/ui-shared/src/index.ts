/**
 * Aquafish 管理端、用户中心和插件 UI 共用的稳定契约入口。
 */
export { definePlugin } from './plugin'

export type {
  AqApplicationId,
  AqExtensionPointId,
  AqUiExtensionContext,
  AqUiExtensionRegistration,
  AqUiPluginDefinition,
} from './plugin'
