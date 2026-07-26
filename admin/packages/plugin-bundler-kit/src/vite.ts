import { existsSync, statSync } from 'node:fs'
import { isAbsolute, relative, resolve } from 'node:path'

import Vue from '@vitejs/plugin-vue'
import {
  defineConfig,
  mergeConfig,
  type ConfigEnv,
  type UserConfig,
  type UserConfigExport,
} from 'vite'

import {
  AQ_PLUGIN_DEFAULT_DEVELOPMENT_OUT_DIR,
  AQ_PLUGIN_DEFAULT_ENTRY,
  AQ_PLUGIN_DEFAULT_PRODUCTION_OUT_DIR,
  AQ_PLUGIN_ENTRY_FILE,
  AQ_PLUGIN_EXTERNALS,
  AQ_PLUGIN_GLOBALS,
  AQ_PLUGIN_STYLE_FILE,
} from './constants'
import {
  getAqPluginGlobalName,
  readAqPluginBuildManifest,
} from './manifest'
import { createAqPluginUiManifestPlugin } from './ui-manifest'

type AqViteUserConfig =
  | UserConfig
  | ((environment: ConfigEnv) => UserConfig | Promise<UserConfig>)

export interface AqPluginViteConfigOptions {
  /** 插件 UI 工程根目录，默认使用执行构建命令时的当前目录。 */
  root?: string
  /** plugin.yaml 路径，默认是 ../src/main/resources/plugin.yaml。 */
  manifestPath?: string
  /** 插件 UI 入口，默认是 src/index.ts，且必须位于 root 内。 */
  entry?: string
  /** 显式产物目录；未设置时开发和生产使用各自标准目录。 */
  outDir?: string
  /** 插件静态资源公开基础路径。 */
  base?: string
  /** 允许添加别名和额外 Vite 插件，但不能覆盖核心 external 与产物协议。 */
  vite?: AqViteUserConfig
}

function entryPath(root: string, entry = AQ_PLUGIN_DEFAULT_ENTRY): string {
  const resolved = isAbsolute(entry) ? entry : resolve(root, entry)
  const relativePath = relative(root, resolved)
  if (relativePath.startsWith('..') || isAbsolute(relativePath)) {
    throw new Error(`插件 UI 入口必须位于工程根目录内：${resolved}`)
  }
  if (!existsSync(resolved) || !statSync(resolved).isFile()) {
    throw new Error(`找不到插件 UI 入口：${resolved}`)
  }
  return resolved
}

function additionalExternals(value: unknown): unknown[] {
  if (Array.isArray(value)) {
    return value
  }
  return typeof value === 'string' || value instanceof RegExp
    ? [value]
    : []
}

/**
 * 创建 Aquafish 插件 UI 的 Vite 配置。
 *
 * 核心运行时强制 external，产物固定为 IIFE main.js、可选 style.css 和
 * ui-manifest.json，方便后续宿主按清单加载而不是执行任意远程脚本地址。
 */
export function defineAqPluginViteConfig(
  options: AqPluginViteConfigOptions = {},
): UserConfigExport {
  return defineConfig(async (environment) => {
    const root = resolve(options.root || process.cwd())
    const manifest = readAqPluginBuildManifest(options.manifestPath, root)
    const globalName = getAqPluginGlobalName(manifest.id)
    const userConfig = typeof options.vite === 'function'
      ? await options.vite(environment)
      : options.vite || {}
    const defaults: UserConfig = {
      root,
      base: `/plugins/${encodeURIComponent(manifest.id)}/ui/`,
      plugins: [
        Vue(),
        createAqPluginUiManifestPlugin(manifest, globalName),
      ],
      build: {
        outDir: environment.mode === 'production'
          ? AQ_PLUGIN_DEFAULT_PRODUCTION_OUT_DIR
          : AQ_PLUGIN_DEFAULT_DEVELOPMENT_OUT_DIR,
        emptyOutDir: true,
        target: 'es2022',
        cssCodeSplit: false,
        lib: {
          entry: entryPath(root, options.entry),
          name: globalName,
          formats: ['iife'],
          fileName: () => AQ_PLUGIN_ENTRY_FILE,
          cssFileName: AQ_PLUGIN_STYLE_FILE.replace(/\.css$/, ''),
        },
        rollupOptions: {
          external: [...AQ_PLUGIN_EXTERNALS],
          output: {
            globals: AQ_PLUGIN_GLOBALS,
            extend: true,
            inlineDynamicImports: true,
          },
        },
      },
    }
    const merged = mergeConfig(defaults, userConfig)
    const userRollupOptions = merged.build?.rollupOptions || {}
    const userOutput = !Array.isArray(userRollupOptions.output)
      ? userRollupOptions.output || {}
      : {}
    const userGlobals = 'globals' in userOutput && userOutput.globals
      ? userOutput.globals
      : {}

    return {
      ...merged,
      root,
      base: options.base || merged.base,
      build: {
        ...merged.build,
        outDir: options.outDir || merged.build?.outDir,
        emptyOutDir: true,
        target: merged.build?.target || 'es2022',
        cssCodeSplit: false,
        lib: defaults.build?.lib,
        rollupOptions: {
          ...userRollupOptions,
          external: [
            ...AQ_PLUGIN_EXTERNALS,
            ...additionalExternals(userRollupOptions.external),
          ],
          output: {
            ...userOutput,
            globals: {
              ...userGlobals,
              ...AQ_PLUGIN_GLOBALS,
            },
            extend: true,
            inlineDynamicImports: true,
          },
        },
      },
    }
  })
}
