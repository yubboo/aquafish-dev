/**
 * 后台初版工作台 API。
 *
 * 页面只消费统一 ApiResult，不直接处理 Cookie、CSRF 或授权跳转；这些安全行为由
 * admin-fetch-guard.ts 全局完成。
 */
export interface ApiResult<T> {
  success: boolean
  code: string
  message: string
  data: T
}

export type WorkspaceRow = Record<string, unknown>

export interface WorkspacePage {
  domain: string
  resource: string
  title: string
  table: string
  page: number
  pageSize: number
  total: number
  totalPages: number
  columns: string[]
  items: WorkspaceRow[]
}

export interface AdminCommandResult<T> {
  data: T
  message: string
}

/** 请求一个后台 JSON 资源，并把业务失败统一转换为可展示异常。 */
export async function adminRequest<T>(
  url: string,
  init?: RequestInit,
): Promise<T> {
  const response = await fetch(url, init)
  const body = await response.json().catch(() => null) as ApiResult<T> | null
  if (!response.ok || !body || body.success !== true) {
    throw new Error(body?.message || `请求失败：HTTP ${response.status}`)
  }
  return body.data
}

/** 执行后台写操作并同时保留服务端成功提示。 */
export async function adminCommand<T>(
  url: string,
  init: RequestInit,
): Promise<AdminCommandResult<T>> {
  const response = await fetch(url, init)
  const body = await response.json().catch(() => null) as ApiResult<T> | null
  if (!response.ok || !body || body.success !== true) {
    throw new Error(body?.message || `请求失败：HTTP ${response.status}`)
  }
  return {
    data: body.data,
    message: body.message || '操作成功。',
  }
}

export function loadWorkspace(
  domain: string,
  resource: string,
  page = 1,
  pageSize = 20,
): Promise<WorkspacePage> {
  const query = new URLSearchParams({
    page: String(page),
    pageSize: String(pageSize),
  })
  return adminRequest<WorkspacePage>(
    `/api/admin/workspace/${encodeURIComponent(domain)}/${encodeURIComponent(resource)}?${query}`,
  )
}

/* ==========================================================================
 * BEGIN：IP 封禁写操作
 * ========================================================================== */

export interface IpBanPayload {
  ipValue: string
  banType: 'access' | 'login' | 'register'
  reason: string
  expiredAt: string | null
  enabled: boolean
}

export function createIpBan(payload: IpBanPayload) {
  return adminCommand<{ id: number }>('/api/admin/users/ip-bans', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function updateIpBan(id: number | string, payload: IpBanPayload) {
  return adminCommand<{ id: number }>(
    `/api/admin/users/ip-bans/${encodeURIComponent(String(id))}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
  )
}

export function setIpBanEnabled(id: number | string, enabled: boolean) {
  return adminCommand<{ id: number }>(
    `/api/admin/users/ip-bans/${encodeURIComponent(String(id))}/${enabled ? 'enable' : 'disable'}`,
    { method: 'POST' },
  )
}

export function deleteIpBan(id: number | string) {
  return adminCommand<{ id: number }>(
    `/api/admin/users/ip-bans/${encodeURIComponent(String(id))}`,
    { method: 'DELETE' },
  )
}

/* END：IP 封禁写操作。 */
