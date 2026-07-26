import { defineComponent } from 'vue'
import {
  createMemoryHistory,
  createRouter,
} from 'vue-router'
import { describe, expect, it, vi } from 'vitest'

import type {
  AqPluginUiCatalog,
  AqPluginUiDescriptor,
} from '../api/plugins'
import { aqUiExtensions } from './extension-registry'
import {
  createAqPluginUiLoader,
  type AqPluginUiAssetHandle,
} from './loader'

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

function descriptor(pluginId: string): AqPluginUiDescriptor {
  return {
    pluginId,
    pluginVersion: '1.0.0',
    globalName: `AqPlugin_${pluginId.replaceAll('-', '_')}`,
    entry: 'main.js',
    style: 'style.css',
    externals: [],
    grantedPermissions: [],
  }
}

describe('aqadmin 插件 UI 加载器', () => {
  it('隔离单插件定义失败，并在后端停用后卸载资源和扩展', async () => {
    const scope: Record<string, unknown> = {}
    const definitions: Record<string, unknown> = {
      AqPlugin_good: {
        default: {
          name: 'good',
          extensionPoints: [{
            id: 'widget',
            point: 'aqadmin:dashboard:widgets:create',
            create: () => defineComponent({
              template: '<div>good</div>',
            }),
          }],
        },
      },
      AqPlugin_broken: {},
    }
    let catalog: AqPluginUiCatalog = {
      items: [descriptor('good'), descriptor('broken')],
      failures: [],
    }
    const removed: string[] = []
    const handle = (kind: string, url: string): AqPluginUiAssetHandle => ({
      remove: () => removed.push(`${kind}:${url}`),
    })
    const loader = createAqPluginUiLoader(
      router(),
      {
        loadCatalog: vi.fn(async () => catalog),
        loadScript: vi.fn(async (url) => {
          for (const [name, definition] of Object.entries(definitions)) {
            if (url.includes(`/${name === 'AqPlugin_good' ? 'good' : 'broken'}/`)) {
              scope[name] = definition
            }
          }
          return handle('script', url)
        }),
        loadStyle: vi.fn(async url => handle('style', url)),
        readGlobal: name => scope[name],
        clearGlobal: vi.fn(name => Reflect.deleteProperty(scope, name)),
      },
    )

    await loader.sync(true)

    expect(loader.state.loadedPluginIds).toEqual(['good'])
    expect(loader.state.failures).toEqual([
      expect.objectContaining({
        pluginId: 'broken',
        stage: 'definition',
      }),
    ])
    expect(aqUiExtensions().value.map(item => item.pluginName))
      .toEqual(['good'])

    catalog = { items: [], failures: [] }
    await loader.sync(true)

    expect(loader.state.loadedPluginIds).toEqual([])
    expect(aqUiExtensions().value).toEqual([])
    expect(removed.some(item => item.startsWith('script:'))).toBe(true)
    expect(removed.some(item => item.startsWith('style:'))).toBe(true)
  })

  it('目录请求失败时保留上一轮已经加载的插件', async () => {
    const good = descriptor('stable')
    const scope: Record<string, unknown> = {}
    const definitions: Record<string, unknown> = {
      AqPlugin_stable: {
        default: { name: 'stable' },
      },
    }
    let shouldFail = false
    const loader = createAqPluginUiLoader(
      router(),
      {
        loadCatalog: async () => {
          if (shouldFail) throw new Error('网络暂时不可用')
          return { items: [good], failures: [] }
        },
        loadScript: async () => {
          scope.AqPlugin_stable = definitions.AqPlugin_stable
          return { remove: vi.fn() }
        },
        loadStyle: async () => ({ remove: vi.fn() }),
        readGlobal: name => scope[name],
        clearGlobal: name => {
          Reflect.deleteProperty(scope, name)
        },
      },
    )

    await loader.sync(true)
    shouldFail = true
    await expect(loader.sync(true)).rejects.toThrow('网络暂时不可用')

    expect(loader.state.loadedPluginIds).toEqual(['stable'])
    expect(loader.state.failures[0]).toEqual(
      expect.objectContaining({ stage: 'catalog' }),
    )
    loader.dispose()
  })

  it('同版本权限变化时重新注册插件，确保撤销立即生效', async () => {
    const scope: Record<string, unknown> = {}
    let catalog: AqPluginUiCatalog = {
      items: [{
        ...descriptor('permissioned'),
        grantedPermissions: ['dashboard.read'],
      }],
      failures: [],
    }
    let scriptLoads = 0
    const loader = createAqPluginUiLoader(
      router(),
      {
        loadCatalog: async () => catalog,
        loadScript: async () => {
          scriptLoads += 1
          scope.AqPlugin_permissioned = {
            name: 'permissioned',
          }
          return { remove: vi.fn() }
        },
        loadStyle: async () => ({ remove: vi.fn() }),
        readGlobal: name => scope[name],
        clearGlobal: name => {
          Reflect.deleteProperty(scope, name)
        },
      },
    )

    await loader.sync(true)
    catalog = {
      items: [{
        ...descriptor('permissioned'),
        grantedPermissions: [],
      }],
      failures: [],
    }
    await loader.sync(true)

    expect(scriptLoads).toBe(2)
    expect(loader.state.loadedPluginIds).toEqual(['permissioned'])
    loader.dispose()
  })
})
