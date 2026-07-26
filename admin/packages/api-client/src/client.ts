import axios, {
  AxiosHeaders,
  type AxiosAdapter,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios'

/** Aquafish 后端统一 JSON 响应。 */
export interface AqApiEnvelope<T> {
  success: boolean
  code: string
  message: string
  data: T | null
}

export type AqRequestConfig<D = unknown> = AxiosRequestConfig<D>

/** HTTP 失败对应的宿主行为；纯函数分类便于完整测试安全边界。 */
export type AqApiFailureAction =
  | { type: 'none' }
  | { type: 'csrf-retry' }
  | { type: 'csrf-reset' }
  | { type: 'unauthorized' }
  | { type: 'license-required' }
  | { type: 'feature-required'; requiredFeature: string }

export interface AqApiFailureInput {
  status: number
  url: string
  method?: string
  code?: string
  requiredFeature?: string
  csrfRetried?: boolean
}

export interface AqApiClientOptions {
  baseURL?: string
  timeout?: number
  /**
   * 测试或特殊运行环境可注入 Axios Adapter；生产应用不应自行替换安全传输层。
   */
  adapter?: AxiosAdapter
  onUnauthorized?: () => void
  onLicenseRequired?: () => void
  onFeatureRequired?: (requiredFeature: string) => void
}

interface AqCsrfResponse {
  token: string
  headerName?: string
}

interface AqCsrfCredentials {
  token: string
  headerName: string
}

interface AqInternalRequestConfig extends InternalAxiosRequestConfig {
  __aqCsrfRetried?: boolean
}

function requestPath(url: string, baseURL = ''): string {
  const base = new URL(baseURL || '/', 'http://aquafish.local')
  return new URL(url || '/', base).pathname
}

function isUnsafeMethod(method?: string): boolean {
  return !['GET', 'HEAD', 'OPTIONS'].includes(String(method || 'GET').toUpperCase())
}

function isAdminApiPath(path: string): boolean {
  return path.startsWith('/api/admin/')
}

function isAuthCheckPath(path: string): boolean {
  return path === '/api/admin/auth/me' || path === '/api/admin/auth/bridge'
}

/** Axios 的 blob 错误响应仍可能包含后端 JSON，统一恢复为可分类对象。 */
async function readFailurePayload(data: unknown): Promise<{
  code?: string
  message?: string
  data?: { requiredFeature?: string } | unknown
}> {
  if (typeof Blob !== 'undefined' && data instanceof Blob) {
    const text = await data.text()
    if (!text) {
      return {}
    }
    try {
      return JSON.parse(text) as {
        code?: string
        message?: string
        data?: { requiredFeature?: string } | unknown
      }
    } catch {
      return { message: text }
    }
  }
  if (typeof data === 'string') {
    try {
      return JSON.parse(data) as {
        code?: string
        message?: string
        data?: { requiredFeature?: string } | unknown
      }
    } catch {
      return { message: data }
    }
  }
  return data && typeof data === 'object'
    ? data as {
        code?: string
        message?: string
        data?: { requiredFeature?: string } | unknown
      }
    : {}
}

/**
 * 对后台失败进行无副作用分类。
 *
 * 只有后端明确返回 ADMIN_CSRF_INVALID 才自动换令牌并重试一次，普通权限 403
 * 不会被错误地重复提交。
 */
export function classifyAqApiFailure(input: AqApiFailureInput): AqApiFailureAction {
  const path = requestPath(input.url)
  const unsafe = isUnsafeMethod(input.method)

  if (input.status === 403 && input.code === 'LICENSE_FEATURE_REQUIRED') {
    return {
      type: 'feature-required',
      requiredFeature: input.requiredFeature || '',
    }
  }

  if (input.status === 403 && input.code === 'ADMIN_CSRF_INVALID' && unsafe) {
    return input.csrfRetried ? { type: 'csrf-reset' } : { type: 'csrf-retry' }
  }

  if (input.status === 403 && unsafe) {
    return { type: 'csrf-reset' }
  }

  if (input.status === 401
    && path !== '/api/admin/auth/login'
    && !isAuthCheckPath(path)) {
    return { type: 'unauthorized' }
  }

  if (input.status === 423 && !path.startsWith('/api/admin/license/')) {
    return { type: 'license-required' }
  }

  return { type: 'none' }
}

/** 包含后端业务代码和 HTTP 状态的统一异常。 */
export class AqApiError extends Error {
  readonly code: string
  readonly status: number | null
  readonly data: unknown

  constructor(
    message: string,
    options: {
      code?: string
      status?: number | null
      data?: unknown
      cause?: unknown
    } = {},
  ) {
    super(message)
    this.name = 'AqApiError'
    this.code = options.code || 'REQUEST_FAILED'
    this.status = options.status ?? null
    this.data = options.data
    if (options.cause !== undefined) {
      this.cause = options.cause
    }
  }
}

/**
 * 创建一个应用级 Axios 实例。
 *
 * Cookie、CSRF、401、许可证状态和功能授权都在这里统一处理；页面和插件不得再创建
 * 各自的后台 Axios 实例。
 */
export function createAqApiClient(options: AqApiClientOptions = {}): AxiosInstance {
  const sharedConfig: AxiosRequestConfig = {
    baseURL: options.baseURL || '',
    timeout: options.timeout ?? 15_000,
    withCredentials: true,
    adapter: options.adapter,
    headers: {
      Accept: 'application/json',
      'X-Requested-With': 'XMLHttpRequest',
    },
  }
  const client = axios.create(sharedConfig)
  const csrfClient = axios.create(sharedConfig)
  let csrfCredentials: AqCsrfCredentials | null = null
  let csrfRequest: Promise<AqCsrfCredentials> | null = null

  async function ensureCsrfCredentials(): Promise<AqCsrfCredentials> {
    if (csrfCredentials) {
      return csrfCredentials
    }
    if (!csrfRequest) {
      csrfRequest = csrfClient
        .get<AqApiEnvelope<AqCsrfResponse>>('/api/admin/auth/csrf')
        .then((response) => {
          const result = response.data
          if (!result?.success || !result.data?.token) {
            throw new AqApiError(result?.message || '无法获取请求安全令牌。', {
              code: result?.code || 'CSRF_TOKEN_UNAVAILABLE',
              status: response.status,
              data: result?.data,
            })
          }
          csrfCredentials = {
            token: String(result.data.token),
            headerName: String(result.data.headerName || 'X-XSRF-TOKEN'),
          }
          return csrfCredentials
        })
        .finally(() => {
          csrfRequest = null
        })
    }
    return csrfRequest
  }

  client.interceptors.request.use(async (config) => {
    const path = requestPath(config.url || '', config.baseURL)
    if (isAdminApiPath(path)
      && path !== '/api/admin/auth/csrf'
      && isUnsafeMethod(config.method)) {
      const csrf = await ensureCsrfCredentials()
      const headers = AxiosHeaders.from(config.headers)
      headers.set(csrf.headerName, csrf.token)
      config.headers = headers
    }
    return config
  })

  client.interceptors.response.use(
    response => response,
    async (error: unknown) => {
      if (!axios.isAxiosError(error) || !error.config) {
        return Promise.reject(error)
      }

      const config = error.config as AqInternalRequestConfig
      const body = await readFailurePayload(error.response?.data)
      const failureData = body.data && typeof body.data === 'object'
        ? body.data as { requiredFeature?: string }
        : undefined
      const action = classifyAqApiFailure({
        status: error.response?.status || 0,
        url: requestPath(config.url || '', config.baseURL),
        method: config.method,
        code: body?.code,
        requiredFeature: failureData?.requiredFeature,
        csrfRetried: config.__aqCsrfRetried,
      })

      if (action.type === 'csrf-retry') {
        csrfCredentials = null
        config.__aqCsrfRetried = true
        return client.request(config)
      }
      if (action.type === 'csrf-reset') {
        csrfCredentials = null
      } else if (action.type === 'unauthorized') {
        options.onUnauthorized?.()
      } else if (action.type === 'license-required') {
        options.onLicenseRequired?.()
      } else if (action.type === 'feature-required') {
        options.onFeatureRequired?.(action.requiredFeature)
      }

      return Promise.reject(error)
    },
  )

  return client
}

/** 发起请求并保留后端成功消息，供写操作提示使用。 */
export async function requestAqEnvelope<T, D = unknown>(
  client: AxiosInstance,
  config: AqRequestConfig<D>,
): Promise<AqApiEnvelope<T>> {
  try {
    const response = await client.request<AqApiEnvelope<T>>(config)
    const result = response.data
    if (!result || result.success !== true) {
      throw new AqApiError(result?.message || '接口请求失败。', {
        code: result?.code,
        status: response.status,
        data: result?.data,
      })
    }
    return result
  } catch (error) {
    if (error instanceof AqApiError) {
      throw error
    }
    if (axios.isAxiosError(error)) {
      const result = error.response?.data as Partial<AqApiEnvelope<unknown>> | undefined
      throw new AqApiError(
        result?.message || error.message || '网络请求失败。',
        {
          code: result?.code || 'NETWORK_ERROR',
          status: error.response?.status ?? null,
          data: result?.data,
          cause: error,
        },
      )
    }
    throw new AqApiError(
      error instanceof Error ? error.message : '网络请求失败。',
      { cause: error },
    )
  }
}

/** 发起请求并返回非空业务数据。 */
export async function requestAqData<T, D = unknown>(
  client: AxiosInstance,
  config: AqRequestConfig<D>,
): Promise<T> {
  const result = await requestAqEnvelope<T, D>(client, config)
  if (result.data === null) {
    throw new AqApiError(result.message || '接口没有返回数据。', {
      code: result.code || 'EMPTY_RESPONSE',
    })
  }
  return result.data
}

/** 下载非 JSON 二进制资源，同时保留统一认证、授权和错误解析行为。 */
export async function requestAqBlob<D = unknown>(
  client: AxiosInstance,
  config: AqRequestConfig<D>,
): Promise<Blob> {
  try {
    const response = await client.request<Blob>({
      ...config,
      responseType: 'blob',
    })
    if (!(response.data instanceof Blob)) {
      throw new AqApiError('接口没有返回二进制资源。', {
        code: 'INVALID_BINARY_RESPONSE',
        status: response.status,
      })
    }
    return response.data
  } catch (error) {
    if (error instanceof AqApiError) {
      throw error
    }
    if (axios.isAxiosError(error)) {
      const result = await readFailurePayload(error.response?.data)
      throw new AqApiError(
        result.message || error.message || '文件下载失败。',
        {
          code: result.code || 'DOWNLOAD_FAILED',
          status: error.response?.status ?? null,
          data: result.data,
          cause: error,
        },
      )
    }
    throw new AqApiError(
      error instanceof Error ? error.message : '文件下载失败。',
      { code: 'DOWNLOAD_FAILED', cause: error },
    )
  }
}
