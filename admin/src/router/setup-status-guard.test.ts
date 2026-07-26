import { describe, expect, it } from 'vitest'
import {
  SYSTEM_UNAVAILABLE_PATH,
  resolveSetupNavigation,
} from './setup-status-guard'

const installableStatus = {
  installed: false,
  canInstall: true,
  stateAvailable: true,
}

const installedStatus = {
  installed: true,
  canInstall: false,
  stateAvailable: true,
}

const databaseUnavailableStatus = {
  installed: false,
  canInstall: false,
  stateAvailable: false,
}

/**
 * 首次安装导航矩阵测试。
 *
 * 这些测试专门覆盖过去缺失的跨页面行为，避免只测试后端状态接口，
 * 却遗漏“空白系统首次打开仍然进入登录页”的回归问题。
 */
describe('resolveSetupNavigation', () => {
  it('未安装时把后台首页送到安装向导', () => {
    expect(resolveSetupNavigation(installableStatus, '/admin')).toEqual({
      path: '/setup',
      replace: true,
    })
  })

  it('未安装时也不允许直接进入后台登录页', () => {
    expect(resolveSetupNavigation(installableStatus, '/admin/login')).toEqual({
      path: '/setup',
      replace: true,
    })
  })

  it('未安装时允许安装向导自身正常渲染', () => {
    expect(resolveSetupNavigation(installableStatus, '/setup')).toBe(true)
  })

  it('已安装后禁止重新打开安装向导', () => {
    expect(resolveSetupNavigation(installedStatus, '/setup')).toEqual({
      path: '/setup/installed',
      replace: true,
    })
  })

  it('已安装时允许专用提示页渲染，但不允许安装向导渲染', () => {
    expect(resolveSetupNavigation(installedStatus, '/setup/installed')).toBe(true)
  })

  it('未安装时不能伪造地址进入已安装提示页', () => {
    expect(resolveSetupNavigation(installableStatus, '/setup/installed')).toEqual({
      path: '/setup',
      replace: true,
    })
  })

  it('已安装后允许后台路由继续执行登录守卫', () => {
    expect(resolveSetupNavigation(installedStatus, '/admin/users')).toBe(true)
  })

  it('数据库不可用时不能把已配置环境伪装成首次安装', () => {
    expect(resolveSetupNavigation(databaseUnavailableStatus, '/admin/users')).toEqual({
      path: SYSTEM_UNAVAILABLE_PATH,
      query: {
        redirect: '/admin/users',
      },
      replace: true,
    })
  })

  it('数据库不可用时访问安装地址也进入独立故障页', () => {
    expect(resolveSetupNavigation(databaseUnavailableStatus, '/setup')).toEqual({
      path: SYSTEM_UNAVAILABLE_PATH,
      query: {
        redirect: '/admin',
      },
      replace: true,
    })
  })

  it('数据库恢复为已安装后离开故障页并进入后台首页', () => {
    expect(resolveSetupNavigation(installedStatus, SYSTEM_UNAVAILABLE_PATH)).toEqual({
      path: '/admin',
      replace: true,
    })
  })

  it('纯净环境从故障页重新检测成功后进入安装器', () => {
    expect(resolveSetupNavigation(installableStatus, SYSTEM_UNAVAILABLE_PATH)).toEqual({
      path: '/setup',
      replace: true,
    })
  })
})
