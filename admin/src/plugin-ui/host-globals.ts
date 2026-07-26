/**
 * 为插件 IIFE 入口安装与构建器 external 协议一致的宿主全局。
 *
 * 这里只暴露 Aquafish 已明确承诺的共享包，不暴露 aqadmin 内部页面、Store 或私有组件路径。
 */
import * as AquafishApiClient from '@aquafish/api-client'
import * as AquafishComponents from '@aquafish/components'
import * as AquafishUiShared from '@aquafish/ui-shared'
import axios from 'axios'
import * as Pinia from 'pinia'
import * as Vue from 'vue'
import * as VueRouter from 'vue-router'

const HOST_GLOBALS = Object.freeze({
  Vue,
  VueRouter,
  Pinia,
  axios,
  AquafishComponents,
  AquafishApiClient,
  AquafishUiShared,
})

/**
 * 全局属性设为不可写但可配置：运行期间插件不能替换宿主共享包，测试和应用卸载仍可清理。
 */
export function installAqPluginHostGlobals(
  target: Record<string, unknown> = globalThis as Record<string, unknown>,
): void {
  for (const [name, value] of Object.entries(HOST_GLOBALS)) {
    const descriptor = Object.getOwnPropertyDescriptor(target, name)
    if (descriptor && descriptor.value !== value) {
      throw new Error(`插件宿主全局 ${name} 已被其他脚本占用。`)
    }
    if (!descriptor) {
      Object.defineProperty(target, name, {
        configurable: true,
        enumerable: false,
        writable: false,
        value,
      })
    }
  }
}

export const AQ_PLUGIN_HOST_GLOBAL_NAMES = Object.freeze(
  Object.keys(HOST_GLOBALS),
)

