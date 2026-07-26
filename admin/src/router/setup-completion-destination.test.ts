import { describe, expect, it } from 'vitest'
import {
  DEFAULT_SETUP_DESTINATION,
  SETUP_DESTINATION_STORAGE_KEY,
  normalizeSetupDestination,
  readSetupDestination,
  rememberSetupDestination,
} from './setup-completion-destination'

class MemoryStorage {
  private readonly values = new Map<string, string>()

  getItem(key: string): string | null {
    return this.values.get(key) ?? null
  }

  setItem(key: string, value: string): void {
    this.values.set(key, value)
  }
}

describe('安装完成入口偏好', () => {
  it('可以保存并读取后台主题与插件入口', () => {
    const storage = new MemoryStorage()

    rememberSetupDestination('/admin/themes', storage)

    expect(readSetupDestination(storage)).toBe('/admin/themes')
    expect(storage.getItem(SETUP_DESTINATION_STORAGE_KEY)).toBe('/admin/themes')
  })

  it('可以保存安装时确认过的 HTTP(S) 站点地址', () => {
    const storage = new MemoryStorage()

    rememberSetupDestination('https://example.com/site', storage)

    expect(readSetupDestination(storage)).toBe('https://example.com/site')
  })

  it('拒绝脚本协议、协议相对地址和任意后台路径', () => {
    expect(normalizeSetupDestination('javascript:alert(1)')).toBe(DEFAULT_SETUP_DESTINATION)
    expect(normalizeSetupDestination('//evil.example')).toBe(DEFAULT_SETUP_DESTINATION)
    expect(normalizeSetupDestination('/admin/users')).toBe(DEFAULT_SETUP_DESTINATION)
  })

  it('没有选择记录时默认进入会员登录', () => {
    expect(readSetupDestination(new MemoryStorage())).toBe(DEFAULT_SETUP_DESTINATION)
  })
})
