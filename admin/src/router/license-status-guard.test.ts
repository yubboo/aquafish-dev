import { describe, expect, it } from 'vitest'
import { resolveLicenseNavigation } from './license-status-guard'

describe('resolveLicenseNavigation', () => {
  it('有效授权允许进入任意后台页面', () => {
    expect(resolveLicenseNavigation({ usable: true }, '/admin/users')).toBe(true)
  })

  it('未授权时只允许进入授权页', () => {
    expect(resolveLicenseNavigation({ usable: false }, '/admin/license')).toBe(true)
  })

  it('未授权时仍允许进入基础后台页面', () => {
    expect(resolveLicenseNavigation({ usable: false }, '/admin/users')).toBe(true)
  })

  it('未授权访问高级模块时保留原始目标并跳到授权页', () => {
    expect(resolveLicenseNavigation({ usable: false }, '/admin/ai/providers')).toEqual({
      path: '/admin/license',
      replace: true,
      query: { redirect: '/admin/ai/providers' },
    })
  })

  it('有效平台授权但缺少模块时进入授权不足页', () => {
    expect(resolveLicenseNavigation({
      usable: true,
      valid: true,
      enforcementEnabled: true,
      features: ['platform', 'forum'],
    }, '/admin/ai/providers')).toEqual({
      path: '/admin/license/feature-required',
      replace: true,
      query: {
        feature: 'ai',
        redirect: '/admin/ai/providers',
      },
    })
  })

  it('cms 兼容包允许进入内容与主题页面', () => {
    const status = {
      usable: true,
      valid: true,
      enforcementEnabled: true,
      features: ['platform', 'cms'],
    }

    expect(resolveLicenseNavigation(status, '/admin/content/articles')).toBe(true)
    expect(resolveLicenseNavigation(status, '/admin/themes/current')).toBe(true)
  })

  it('开发环境关闭授权强制时允许所有模块', () => {
    expect(resolveLicenseNavigation({
      usable: true,
      valid: false,
      enforcementEnabled: false,
      features: [],
    }, '/admin/market')).toBe(true)
  })
})
