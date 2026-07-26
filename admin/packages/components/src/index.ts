/**
 * Aquafish 管理端与插件 UI 的公共组件入口。
 *
 * 对外组件统一使用 Aq 前缀，禁止页面和插件依赖组件库内部目录。
 */
export { default as AqButton } from './components/button/AqButton.vue'
export { default as AqPagination } from './components/pagination/AqPagination.vue'
export { getPageCount, normalizePage } from './components/pagination/pagination'
export { default as AqStatus } from './components/status/AqStatus.vue'

export type {
  AqButtonSize,
  AqButtonVariant,
} from './components/button/button'
export type { AqStatusTone } from './components/status/status'
