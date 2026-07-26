import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { resolve } from 'node:path'

import { describe, expect, it } from 'vitest'

function sourceFiles(directory: string): string[] {
  return readdirSync(directory)
    .flatMap((name) => {
      const file = resolve(directory, name)
      return statSync(file).isDirectory() ? sourceFiles(file) : [file]
    })
    .filter(file => /\.(ts|vue)$/.test(file) && !file.endsWith('.test.ts'))
}

describe('aqadmin 请求传输边界', () => {
  it('生产源码不再直接调用 Fetch 或安装全局 Fetch Monkey Patch', () => {
    const sourceRoot = resolve(process.cwd(), 'src')
    const violations = sourceFiles(sourceRoot)
      .filter((file) => {
        const content = readFileSync(file, 'utf8')
        return /\bfetch\s*\(/.test(content) || /window\.fetch/.test(content)
      })
      .map(file => file.replace(`${process.cwd()}\\`, ''))

    expect(violations).toEqual([])
    expect(existsSync(resolve(sourceRoot, 'api', 'admin-fetch-guard.ts')))
      .toBe(false)
  })
})
