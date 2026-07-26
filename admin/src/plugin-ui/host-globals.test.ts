import { afterEach, describe, expect, it } from 'vitest'

import {
  AQ_PLUGIN_HOST_GLOBAL_NAMES,
  installAqPluginHostGlobals,
} from './host-globals'

const target: Record<string, unknown> = {}

afterEach(() => {
  for (const name of AQ_PLUGIN_HOST_GLOBAL_NAMES) {
    Reflect.deleteProperty(target, name)
  }
})

describe('插件共享宿主全局', () => {
  it('安装构建器约定的七个不可写共享包', () => {
    installAqPluginHostGlobals(target)

    expect(Object.keys(target)).toEqual([])
    expect(AQ_PLUGIN_HOST_GLOBAL_NAMES).toEqual([
      'Vue',
      'VueRouter',
      'Pinia',
      'axios',
      'AquafishComponents',
      'AquafishApiClient',
      'AquafishUiShared',
    ])
    for (const name of AQ_PLUGIN_HOST_GLOBAL_NAMES) {
      const property = Object.getOwnPropertyDescriptor(target, name)
      expect(property?.writable).toBe(false)
      expect(property?.configurable).toBe(true)
    }
  })

  it('拒绝覆盖同名第三方全局', () => {
    target.Vue = { occupied: true }

    expect(() => installAqPluginHostGlobals(target))
      .toThrow('Vue 已被其他脚本占用')
  })
})

