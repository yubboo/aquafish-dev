import { describe, expect, it } from 'vitest'
import type { LicenseStatus } from '../api/license'
import { adminMenus, filterAdminMenusByLicense } from './admin-menu'
import {
  isLicenseFeatureGranted,
  requiredLicenseFeatureForAdminPath,
} from './license-features'

/** 创建测试用脱敏状态，避免每个用例重复与本测试无关的授权展示字段。 */
function status(features: string[], enforcementEnabled = true): LicenseStatus {
  return {
    status: enforcementEnabled ? 'VALID' : 'NOT_ACTIVATED',
    valid: enforcementEnabled,
    usable: true,
    enforcementEnabled,
    instanceId: 'instance-test',
    licenseId: enforcementEnabled ? 'license-test' : null,
    edition: enforcementEnabled ? 'professional' : null,
    customer: 'Aquafish Test',
    issuedAt: null,
    expiresAt: null,
    features,
    entitlements: [],
    portalUrl: null,
    online: null,
    message: 'test',
  }
}

describe('license feature mapping', () => {
  it('按完整后台路径段识别模块', () => {
    expect(requiredLicenseFeatureForAdminPath('/admin/forum/posts')).toBe('forum')
    expect(requiredLicenseFeatureForAdminPath('/admin/ai/providers')).toBe('ai')
    expect(requiredLicenseFeatureForAdminPath('/admin/aired')).toBeNull()
  })

  it('cms 兼容总包只授予内容和主题', () => {
    const licensed = status(['platform', 'cms'])
    expect(isLicenseFeatureGranted(licensed, 'content')).toBe(true)
    expect(isLicenseFeatureGranted(licensed, 'theme')).toBe(true)
    expect(isLicenseFeatureGranted(licensed, 'plugin')).toBe(false)
  })

  it('开发环境关闭强制授权时放行全部模块', () => {
    expect(isLicenseFeatureGranted(status([], false), 'ai')).toBe(true)
  })
})

describe('licensed admin menu', () => {
  it('授权状态读取期间保留完整菜单，避免刷新后误隐藏模块', () => {
    const visibleKeys = filterAdminMenusByLicense(adminMenus, null)
      .map((menu) => menu.key)

    expect(visibleKeys).toContain('forum')
    expect(visibleKeys).toContain('content')
    expect(visibleKeys).toContain('theme')
    expect(visibleKeys).toContain('plugin')
  })

  it('只保留基础菜单与授权包含的模块', () => {
    const visibleKeys = filterAdminMenusByLicense(
      adminMenus,
      status(['platform', 'forum']),
    ).map((menu) => menu.key)

    expect(visibleKeys).toContain('dashboard')
    expect(visibleKeys).toContain('users')
    expect(visibleKeys).toContain('license')
    expect(visibleKeys).toContain('forum')
    expect(visibleKeys).not.toContain('ai')
    expect(visibleKeys).not.toContain('market')
    expect(visibleKeys).not.toContain('plugin')
  })

  it('没有 updates 时隐藏授权管理内的更新服务子菜单', () => {
    const licenseMenu = filterAdminMenusByLicense(
      adminMenus,
      status(['platform']),
    ).find((menu) => menu.key === 'license')

    expect(licenseMenu?.children?.map((item) => item.key)).toEqual([
      'license.platform',
      'license.bind',
      'license.online',
    ])
  })
})
