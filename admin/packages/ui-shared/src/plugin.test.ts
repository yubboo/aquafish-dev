import { describe, expect, it } from 'vitest'

import {
  AQ_APPLICATION_IDS,
  AQ_EXTENSION_POINT_IDS,
  definePlugin,
} from './plugin'

describe('definePlugin', () => {
  it('保留 Aq 插件声明和 aqadmin/user 应用标识', () => {
    const plugin = definePlugin({
      name: 'example',
      applications: ['aqadmin', 'user'],
      extensionPoints: [],
    })

    expect(plugin.name).toBe('example')
    expect(plugin.applications).toEqual(['aqadmin', 'user'])
  })

  it('公开稳定应用和扩展点列表供宿主运行时校验', () => {
    expect(AQ_APPLICATION_IDS).toEqual(['aqadmin', 'user'])
    expect(AQ_EXTENSION_POINT_IDS).toContain(
      'aqadmin:dashboard:widgets:create',
    )
  })
})
