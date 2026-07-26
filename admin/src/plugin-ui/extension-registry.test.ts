import { defineComponent } from 'vue'
import {
  createMemoryHistory,
  createRouter,
} from 'vue-router'
import { describe, expect, it } from 'vitest'

import {
  aqUiExtensions,
  registerAqUiPlugin,
} from './extension-registry'

function router() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [{
      path: '/admin',
      name: 'admin.root',
      component: defineComponent({ template: '<div />' }),
    }],
  })
}

describe('aqadmin 插件扩展点注册器', () => {
  it('按顺序注册扩展和受控路由，并能完整卸载', async () => {
    const extensionComponent = defineComponent({
      name: 'DemoDashboardWidget',
      template: '<div>demo</div>',
    })
    const testRouter = router()
    const registration = await registerAqUiPlugin(
      {
        name: 'demo',
        applications: ['aqadmin'],
        permissions: ['dashboard.read'],
        routes: [{
          path: 'plugins/demo/settings',
          name: 'plugin.demo.settings',
          component: extensionComponent,
        }],
        extensionPoints: [{
          id: 'dashboard-widget',
          point: 'aqadmin:dashboard:widgets:create',
          order: 20,
          permissions: ['dashboard.read'],
          create: () => ({
            component: extensionComponent,
            props: { title: '演示组件' },
          }),
        }],
      },
      {
        application: 'aqadmin',
        pluginName: 'demo',
        grantedPermissions: new Set(['dashboard.read']),
      },
      testRouter,
    )

    expect(registration.failures).toEqual([])
    expect(aqUiExtensions().value).toHaveLength(1)
    expect(aqUiExtensions().value[0]?.props)
      .toEqual({ title: '演示组件' })
    expect(testRouter.hasRoute('plugin.demo.settings')).toBe(true)

    registration.dispose()

    expect(aqUiExtensions().value).toEqual([])
    expect(testRouter.hasRoute('plugin.demo.settings')).toBe(false)
  })

  it('拒绝未批准的插件级能力和越界路由', async () => {
    const testRouter = router()

    await expect(registerAqUiPlugin(
      {
        name: 'demo',
        permissions: ['system.write'],
      },
      {
        application: 'aqadmin',
        pluginName: 'demo',
        grantedPermissions: new Set(),
      },
      testRouter,
    )).rejects.toThrow('缺少已批准能力')

    await expect(registerAqUiPlugin(
      {
        name: 'demo',
        routes: [{
          path: 'system/basic',
          name: 'plugin.demo.escape',
          component: defineComponent({ template: '<div />' }),
        }],
      },
      {
        application: 'aqadmin',
        pluginName: 'demo',
        grantedPermissions: new Set(),
      },
      testRouter,
    )).rejects.toThrow('/admin/plugins/demo')
  })

  it('拒绝未知应用标识和重复插件路由', async () => {
    const testRouter = router()
    await expect(registerAqUiPlugin(
      {
        name: 'demo',
        applications: ['unknown' as 'aqadmin'],
      },
      {
        application: 'aqadmin',
        pluginName: 'demo',
        grantedPermissions: new Set(),
      },
      testRouter,
    )).rejects.toThrow('未知的应用标识')

    const routeComponent = defineComponent({ template: '<div />' })
    await expect(registerAqUiPlugin(
      {
        name: 'demo',
        routes: [
          {
            path: 'plugins/demo/settings',
            name: 'plugin.demo.settings',
            component: routeComponent,
          },
          {
            path: 'plugins/demo/settings',
            name: 'plugin.demo.duplicate',
            component: routeComponent,
          },
        ],
      },
      {
        application: 'aqadmin',
        pluginName: 'demo',
        grantedPermissions: new Set(),
      },
      testRouter,
    )).rejects.toThrow('重复注册路由')
  })

  it('隔离单个扩展工厂失败，不影响同插件其他扩展', async () => {
    const validComponent = defineComponent({
      template: '<div>valid</div>',
    })
    const registration = await registerAqUiPlugin(
      {
        name: 'isolated',
        extensionPoints: [
          {
            id: 'broken',
            point: 'aqadmin:dashboard:widgets:create',
            create: () => {
              throw new Error('组件初始化失败')
            },
          },
          {
            id: 'valid',
            point: 'aqadmin:dashboard:widgets:create',
            create: () => validComponent,
          },
        ],
      },
      {
        application: 'aqadmin',
        pluginName: 'isolated',
        grantedPermissions: new Set(),
      },
      router(),
    )

    expect(registration.failures).toEqual([
      '扩展 broken 创建失败：组件初始化失败',
    ])
    expect(aqUiExtensions().value.map(item => item.id))
      .toEqual(['valid'])
    registration.dispose()
  })
})
