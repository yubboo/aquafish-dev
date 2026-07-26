import { describe, expect, it } from 'vitest'

import { definePlugin } from './plugin'

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
})
