import {
  mkdirSync,
  mkdtempSync,
  rmSync,
  writeFileSync,
} from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

import {
  build,
  type ConfigEnv,
  type UserConfig,
} from 'vite'
import { afterEach, describe, expect, it } from 'vitest'

import {
  AQ_PLUGIN_EXTERNALS,
  AQ_PLUGIN_GLOBALS,
} from './constants'
import { defineAqPluginViteConfig } from './vite'

const temporaryDirectories: string[] = []

interface AqBuildOutputItem {
  fileName: string
  type: 'asset' | 'chunk'
  code?: string
  source?: string | Uint8Array
}

interface AqBuildOutput {
  output: AqBuildOutputItem[]
}

afterEach(() => {
  for (const directory of temporaryDirectories.splice(0)) {
    rmSync(directory, { recursive: true, force: true })
  }
})

function createPluginUiProject(): {
  root: string
  manifestPath: string
} {
  const pluginRoot = mkdtempSync(join(tmpdir(), 'aquafish-plugin-ui-'))
  temporaryDirectories.push(pluginRoot)
  const root = join(pluginRoot, 'ui')
  const resources = join(pluginRoot, 'src', 'main', 'resources')
  mkdirSync(join(root, 'src'), { recursive: true })
  mkdirSync(resources, { recursive: true })
  const manifestPath = join(resources, 'plugin.yaml')
  writeFileSync(manifestPath, [
    'id: demo-plugin',
    'name: 演示插件',
    'version: 1.0.0',
    '',
  ].join('\n'), 'utf8')
  writeFileSync(join(root, 'src', 'index.ts'), [
    "import { definePlugin } from '@aquafish/ui-shared'",
    "import DemoPanel from './DemoPanel.vue'",
    '',
    'export default definePlugin({',
    "  name: 'demo-plugin',",
    '  components: { DemoPanel },',
    '})',
    '',
  ].join('\n'), 'utf8')
  writeFileSync(join(root, 'src', 'DemoPanel.vue'), [
    '<script setup lang="ts">',
    "import { ref } from 'vue'",
    "import { AqButton } from '@aquafish/components'",
    'const count = ref(0)',
    '</script>',
    '<template>',
    '  <AqButton class="demo-panel" @click="count += 1">{{ count }}</AqButton>',
    '</template>',
    '<style>.demo-panel { min-width: 8rem; }</style>',
    '',
  ].join('\n'), 'utf8')
  return { root, manifestPath }
}

async function resolveConfig(
  config: ReturnType<typeof defineAqPluginViteConfig>,
): Promise<UserConfig> {
  if (typeof config !== 'function') {
    return config
  }
  return await config({
    command: 'build',
    mode: 'production',
    isSsrBuild: false,
    isPreview: false,
  } as ConfigEnv)
}

describe('Aquafish 插件 UI Vite 构建器', () => {
  it('锁定共享依赖、全局变量和标准产物', async () => {
    const project = createPluginUiProject()
    const config = await resolveConfig(defineAqPluginViteConfig({
      root: project.root,
      manifestPath: project.manifestPath,
      vite: {
        logLevel: 'silent',
        build: {
          minify: false,
          write: false,
        },
      },
    }))

    expect(config.build?.rollupOptions?.external)
      .toEqual(expect.arrayContaining([...AQ_PLUGIN_EXTERNALS]))
    expect(config.build?.outDir)
      .toBe('../build/resources/main/ui')
    const outputOptions = config.build?.rollupOptions?.output
    expect(Array.isArray(outputOptions)).toBe(false)
    expect(outputOptions).toMatchObject({
      globals: AQ_PLUGIN_GLOBALS,
      extend: true,
      inlineDynamicImports: true,
    })

    const buildResult = await build(config)
    const buildCandidates: unknown[] = Array.isArray(buildResult)
      ? buildResult
      : [buildResult]
    const buildOutputs = buildCandidates
      .filter(item => (
        typeof item === 'object'
        && item !== null
        && 'output' in item
      ))
      .map(item => item as AqBuildOutput)
    expect(buildOutputs).toHaveLength(1)
    const output = buildOutputs[0].output
    const files = output.map(item => item.fileName)
    expect(files).toEqual(expect.arrayContaining([
      'main.js',
      'style.css',
      'ui-manifest.json',
    ]))

    const main = output.find(item => item.fileName === 'main.js')
    expect(main?.type).toBe('chunk')
    if (main?.type === 'chunk') {
      expect(main.code).toContain('AquafishUiShared')
      expect(main.code).toContain('AquafishComponents')
      expect(main.code).toContain('Vue')
      /*
       * 真实执行构建后的 IIFE，锁定宿主读取到“模块对象.default”的约定，
       * 防止构建升级后静态文件仍存在但动态加载器拿不到插件定义。
       */
      const runtimeModule = new Function(
        'Vue',
        'AquafishComponents',
        'AquafishUiShared',
        `${main.code}; return AqPlugin_demo_plugin`,
      )(
        {
          defineComponent: (value: unknown) => value,
          ref: (value: unknown) => ({ value }),
        },
        { AqButton: {} },
        { definePlugin: (value: unknown) => value },
      ) as {
        default?: { name?: string }
        name?: string
      }
      const runtimeDefinition = runtimeModule.default ?? runtimeModule
      expect(runtimeDefinition.name).toBe('demo-plugin')
    }

    const manifestAsset = output.find(
      item => item.fileName === 'ui-manifest.json',
    )
    expect(manifestAsset?.type).toBe('asset')
    if (manifestAsset?.type === 'asset') {
      expect(JSON.parse(String(manifestAsset.source))).toMatchObject({
        schemaVersion: 1,
        pluginId: 'demo-plugin',
        pluginVersion: '1.0.0',
        format: 'iife',
        globalName: 'AqPlugin_demo_plugin',
        entry: 'main.js',
        style: 'style.css',
      })
    }
  })
})
