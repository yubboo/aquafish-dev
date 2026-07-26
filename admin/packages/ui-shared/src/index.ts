/**
 * Aquafish 管理端、用户中心和插件 UI 共用的稳定契约入口。
 */
export {
  AQ_APPLICATION_IDS,
  AQ_EXTENSION_POINT_IDS,
  definePlugin,
} from './plugin'

export type {
  AqApplicationId,
  AqExtensionPointId,
  AqUiExtensionContext,
  AqUiExtensionRenderable,
  AqUiExtensionRegistration,
  AqUiExtensionRenderResult,
  AqUiPluginDefinition,
} from './plugin'
