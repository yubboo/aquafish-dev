import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

import { afterEach, describe, expect, it } from 'vitest'

import {
  getAqPluginGlobalName,
  readAqPluginBuildManifest,
} from './manifest'

const temporaryDirectories: string[] = []

afterEach(() => {
  for (const directory of temporaryDirectories.splice(0)) {
    rmSync(directory, { recursive: true, force: true })
  }
})

function createManifest(content: string): string {
  const directory = mkdtempSync(join(tmpdir(), 'aquafish-plugin-manifest-'))
  temporaryDirectories.push(directory)
  const resources = join(directory, 'src', 'main', 'resources')
  mkdirSync(resources, { recursive: true })
  const manifestPath = join(resources, 'plugin.yaml')
  writeFileSync(manifestPath, content, 'utf8')
  return manifestPath
}

describe('Aquafish 插件构建清单', () => {
  it('兼容根字段和 metadata/spec 字段', () => {
    const manifestPath = createManifest([
      'metadata:',
      '  name: article-tools',
      '  displayName: 文章工具',
      'spec:',
      '  version: 1.2.3',
      '  requires: ">=0.1.0"',
      '',
    ].join('\n'))

    expect(readAqPluginBuildManifest(manifestPath)).toMatchObject({
      id: 'article-tools',
      displayName: '文章工具',
      version: '1.2.3',
      requires: '>=0.1.0',
    })
    expect(getAqPluginGlobalName('article-tools'))
      .toBe('AqPlugin_article_tools')
  })

  it('拒绝会与后端校验不一致的插件 ID', () => {
    const manifestPath = createManifest([
      'id: "../escape"',
      'version: 1.0.0',
      '',
    ].join('\n'))

    expect(() => readAqPluginBuildManifest(manifestPath))
      .toThrow('插件 ID 只能包含')
  })
})
