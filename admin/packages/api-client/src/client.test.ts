import { describe, expect, it } from 'vitest'
import {
  AxiosError,
  type AxiosAdapter,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'

import {
  classifyAqApiFailure,
  createAqApiClient,
  requestAqBlob,
  requestAqData,
} from './client'

function response<T>(
  config: InternalAxiosRequestConfig,
  status: number,
  data: T,
): AxiosResponse<T> {
  return {
    config,
    data,
    headers: {},
    status,
    statusText: String(status),
  }
}

describe('Aquafish API 失败分类', () => {
  it('只对明确的 CSRF 失效写请求执行一次重试', () => {
    expect(classifyAqApiFailure({
      status: 403,
      url: '/api/admin/themes/default/activate',
      method: 'POST',
      code: 'ADMIN_CSRF_INVALID',
    })).toEqual({ type: 'csrf-retry' })

    expect(classifyAqApiFailure({
      status: 403,
      url: '/api/admin/themes/default/activate',
      method: 'POST',
      code: 'ADMIN_CSRF_INVALID',
      csrfRetried: true,
    })).toEqual({ type: 'csrf-reset' })
  })

  it('功能授权不足优先于普通 403 处理', () => {
    expect(classifyAqApiFailure({
      status: 403,
      url: '/api/admin/ai/models',
      method: 'POST',
      code: 'LICENSE_FEATURE_REQUIRED',
      requiredFeature: 'ai',
    })).toEqual({
      type: 'feature-required',
      requiredFeature: 'ai',
    })
  })

  it('认证探测和授权接口不会触发循环跳转', () => {
    expect(classifyAqApiFailure({
      status: 401,
      url: '/api/admin/auth/me',
      method: 'GET',
    })).toEqual({ type: 'none' })

    expect(classifyAqApiFailure({
      status: 423,
      url: '/api/admin/license/status',
      method: 'GET',
    })).toEqual({ type: 'none' })
  })

  it('普通后台请求保留统一登录和授权导航', () => {
    expect(classifyAqApiFailure({
      status: 401,
      url: '/api/admin/themes',
      method: 'GET',
    })).toEqual({ type: 'unauthorized' })

    expect(classifyAqApiFailure({
      status: 423,
      url: '/api/admin/themes',
      method: 'GET',
    })).toEqual({ type: 'license-required' })
  })

  it('后台写请求使用服务端声明的 CSRF 请求头', async () => {
    const headers: string[] = []
    const adapter: AxiosAdapter = async (config) => {
      if (config.url === '/api/admin/auth/csrf') {
        return response(config, 200, {
          success: true,
          code: 'OK',
          message: 'ok',
          data: {
            token: 'csrf-token',
            headerName: 'X-AQ-CSRF',
          },
        })
      }
      headers.push(String(config.headers.get('X-AQ-CSRF') || ''))
      return response(config, 200, {
        success: true,
        code: 'OK',
        message: 'ok',
        data: { activated: true },
      })
    }
    const client = createAqApiClient({ adapter })

    const result = await requestAqData<{ activated: boolean }>(client, {
      url: '/api/admin/themes/default/activate',
      method: 'POST',
    })

    expect(result.activated).toBe(true)
    expect(headers).toEqual(['csrf-token'])
  })

  it('CSRF 明确失效时换取令牌并只重试一次', async () => {
    let csrfCallCount = 0
    let writeCallCount = 0
    const writeTokens: string[] = []
    const adapter: AxiosAdapter = async (config) => {
      if (config.url === '/api/admin/auth/csrf') {
        csrfCallCount += 1
        return response(config, 200, {
          success: true,
          code: 'OK',
          message: 'ok',
          data: {
            token: `csrf-${csrfCallCount}`,
            headerName: 'X-XSRF-TOKEN',
          },
        })
      }

      writeCallCount += 1
      writeTokens.push(String(config.headers.get('X-XSRF-TOKEN') || ''))
      if (writeCallCount === 1) {
        const failedResponse = response(config, 403, {
          success: false,
          code: 'ADMIN_CSRF_INVALID',
          message: 'csrf invalid',
          data: null,
        })
        throw new AxiosError(
          'csrf invalid',
          'ERR_BAD_REQUEST',
          config,
          undefined,
          failedResponse,
        )
      }
      return response(config, 200, {
        success: true,
        code: 'OK',
        message: 'ok',
        data: { activated: true },
      })
    }
    const client = createAqApiClient({ adapter })

    await expect(requestAqData(client, {
      url: '/api/admin/themes/default/activate',
      method: 'POST',
    })).resolves.toEqual({ activated: true })

    expect(csrfCallCount).toBe(2)
    expect(writeCallCount).toBe(2)
    expect(writeTokens).toEqual(['csrf-1', 'csrf-2'])
  })

  it('普通后台 401 触发一次宿主登录导航', async () => {
    let unauthorizedCount = 0
    const adapter: AxiosAdapter = async (config) => {
      const failedResponse = response(config, 401, {
        success: false,
        code: 'UNAUTHORIZED',
        message: 'unauthorized',
        data: null,
      })
      throw new AxiosError(
        'unauthorized',
        'ERR_BAD_REQUEST',
        config,
        undefined,
        failedResponse,
      )
    }
    const client = createAqApiClient({
      adapter,
      onUnauthorized: () => {
        unauthorizedCount += 1
      },
    })

    await expect(requestAqData(client, {
      url: '/api/admin/themes',
      method: 'GET',
    })).rejects.toMatchObject({
      code: 'UNAUTHORIZED',
      status: 401,
    })
    expect(unauthorizedCount).toBe(1)
  })

  it('二进制下载保留 Blob，并能解析 Blob 形式的授权错误', async () => {
    const archive = new Blob(['archive'], { type: 'application/zip' })
    const successClient = createAqApiClient({
      adapter: async config => response(config, 200, archive),
    })
    await expect(requestAqBlob(successClient, {
      url: '/api/admin/themes/default/export',
      method: 'GET',
    })).resolves.toBe(archive)

    let requiredFeature = ''
    const failureClient = createAqApiClient({
      adapter: async (config) => {
        const failedResponse = response(config, 403, new Blob([
          JSON.stringify({
            success: false,
            code: 'LICENSE_FEATURE_REQUIRED',
            message: 'feature required',
            data: { requiredFeature: 'theme-export' },
          }),
        ], { type: 'application/json' }))
        throw new AxiosError(
          'feature required',
          'ERR_BAD_REQUEST',
          config,
          undefined,
          failedResponse,
        )
      },
      onFeatureRequired: (feature) => {
        requiredFeature = feature
      },
    })

    await expect(requestAqBlob(failureClient, {
      url: '/api/admin/themes/default/export',
      method: 'GET',
    })).rejects.toMatchObject({
      code: 'LICENSE_FEATURE_REQUIRED',
      status: 403,
    })
    expect(requiredFeature).toBe('theme-export')
  })
})
