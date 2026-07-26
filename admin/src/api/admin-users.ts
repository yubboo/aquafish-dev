/**
 * 后台用户、角色和用户组 API 适配层。
 *
 * 关联 UserManagePage.vue 与 UserSimpleListPage.vue；本文件统一解析 ApiResult、兼容后端
 * 分页字段并规范化用户对象，页面不直接依赖数据库字段或拼接后台接口地址。
 */
export interface ApiResponse<T> {
  success: boolean
  code: string
  message: string
  data: T
}

export type AnyRecord = Record<string, any>

export type AdminUserStatus =
  | 'ACTIVE'
  | 'DISABLED'
  | 'BANNED'
  | 'PENDING'
  | string

export interface AdminUserItem extends AnyRecord {
  id: number | string
  uid: number | string
  public_id: string
  username: string
  email: string
  display_name: string
  displayName: string
  avatar: string | null
  status: AdminUserStatus
  group_id: number | string | null
  groupId: number | string | null
  created_at: string | null
  createdAt: string | null
  updated_at: string | null
  updatedAt: string | null
  last_login_at: string | null
  lastLoginAt: string | null
  roles: AnyRecord[]
  adminGroups: AnyRecord[]
  userGroup: AnyRecord | null
  statistics: AnyRecord | null
}


export interface UserListParams {
  page?: number
  pageSize?: number
  keyword?: string
  status?: string
  adminOnly?: boolean
}

export interface PageResult<T> {
  page: number
  pageSize: number
  total: number
  totalPages: number
  keyword: string
  status: string
  adminOnly: boolean
  items: T[]

  /**
   * 兼容旧页面 src/pages/user/AdminUsers.vue
   */
  message: string
  users: T[]
}

/** 请求只返回 data 的后台接口，并把 HTTP/业务失败转换为 Error。 */
async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init)
  const body = await response.json().catch(() => null) as ApiResponse<T> | null

  if (!response.ok || !body || body.success !== true) {
    throw new Error(body?.message || '请求失败：' + response.status)
  }

  return body.data
}

/** 与 requestJson 相同，但保留后端 message，供列表页显示真实操作结果。 */
async function requestJsonWithMessage<T extends AnyRecord>(
  url: string,
  init?: RequestInit,
): Promise<T & { message: string }> {
  const response = await fetch(url, init)
  const body = await response.json().catch(() => null) as ApiResponse<T> | null

  if (!response.ok || !body || body.success !== true) {
    throw new Error(body?.message || '请求失败：' + response.status)
  }

  return {
    ...((body.data || {}) as T),
    message: body.message || '请求成功。',
  }
}

/** 把未知字段安全转换为字符串，空值使用指定回退文本。 */
function text(value: unknown, fallback = ''): string {
  if (value === null || value === undefined) {
    return fallback
  }

  const result = String(value)
  return result || fallback
}

/** 把可选时间/文本字段规范为字符串或 null，避免页面出现 undefined。 */
function nullableText(value: unknown): string | null {
  if (value === null || value === undefined || value === '') {
    return null
  }

  return String(value)
}

/**
 * 同时兼容 snake_case 与 camelCase 后端字段，输出页面唯一使用的 AdminUserItem 结构。
 */
function normalizeUser(raw: AnyRecord): AdminUserItem {
  const username = text(raw.username, '-')
  const email = text(raw.email, '')
  const displayName = text(raw.displayName ?? raw.display_name, username)
  const status = text(raw.status, 'ACTIVE')

  return {
    ...raw,
    id: raw.id ?? 0,
    uid: raw.uid ?? 0,
    public_id: text(raw.public_id ?? raw.publicId, ''),
    username,
    email,
    display_name: displayName,
    displayName,
    avatar: raw.avatar ?? null,
    status,
    group_id: raw.group_id ?? raw.groupId ?? null,
    groupId: raw.groupId ?? raw.group_id ?? null,
    created_at: nullableText(raw.created_at ?? raw.createdAt),
    createdAt: nullableText(raw.createdAt ?? raw.created_at),
    updated_at: nullableText(raw.updated_at ?? raw.updatedAt),
    updatedAt: nullableText(raw.updatedAt ?? raw.updated_at),
    last_login_at: nullableText(raw.last_login_at ?? raw.lastLoginAt),
    lastLoginAt: nullableText(raw.lastLoginAt ?? raw.last_login_at),
    roles: Array.isArray(raw.roles) ? raw.roles : [],
    adminGroups: Array.isArray(raw.adminGroups) ? raw.adminGroups : [],
    userGroup: raw.userGroup ?? null,
    statistics: raw.statistics ?? null,
  }
}

/**
 * 查询分页用户列表并规范化分页、筛选和用户字段；URL 参数统一通过 URLSearchParams 编码。
 */
export async function fetchAdminUsers(
  params: UserListParams = {},
): Promise<PageResult<AdminUserItem>> {
  const query = new URLSearchParams()

  query.set('page', String(params.page || 1))
  query.set('pageSize', String(params.pageSize || 20))

  if (params.keyword && params.keyword.trim()) {
    query.set('keyword', params.keyword.trim())
  }

  if (params.status && params.status.trim()) {
    query.set('status', params.status.trim())
  }

  if (params.adminOnly === true) {
    query.set('adminOnly', 'true')
  }

  const data = await requestJsonWithMessage<AnyRecord>(
    '/api/admin/users?' + query.toString(),
  )

  const items = Array.isArray(data.items)
    ? data.items.map((item) => normalizeUser(item))
    : []

  return {
    page: Number(data.page || 1),
    pageSize: Number(data.pageSize || 20),
    total: Number(data.total || 0),
    totalPages: Number(data.totalPages || 0),
    keyword: text(data.keyword, ''),
    status: text(data.status, ''),
    adminOnly: Boolean(data.adminOnly),
    items,
    users: items,
    message: data.message || '用户列表获取成功。',
  }
}

/** 查询单个用户完整详情并复用列表的字段规范化规则。 */
export async function fetchAdminUserDetail(id: number | string): Promise<AdminUserItem> {
  const data = await requestJson<AnyRecord>(
    '/api/admin/users/' + encodeURIComponent(String(id)),
  )

  return normalizeUser(data)
}

/* ==========================================================================
 * BEGIN：后台用户写操作
 *
 * 所有请求都经过 main.ts 安装的后台 fetch 安全包装器，自动携带 HttpOnly Cookie
 * 与 CSRF。页面只传业务字段，不读取或保存管理员令牌。
 * ========================================================================== */

/** 创建用户并返回后端重新查询后的完整用户详情。 */
export function createAdminUser(payload: AnyRecord): Promise<AdminUserItem & {
  message: string
}> {
  return requestJsonWithMessage<AdminUserItem>('/api/admin/users/create', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

/** 修改用户名、邮箱、显示名称和头像。 */
export function updateAdminUserBasic(
  id: number | string,
  payload: AnyRecord,
): Promise<AdminUserItem & { message: string }> {
  return postUserAction(id, 'update-basic', payload)
}

/** 修改前台用户组。 */
export function changeAdminUserGroup(
  id: number | string,
  groupId: number | string,
): Promise<AdminUserItem & { message: string }> {
  return postUserAction(id, 'change-user-group', { groupId })
}

/** 启用账号。 */
export function enableAdminUser(
  id: number | string,
): Promise<AdminUserItem & { message: string }> {
  return postUserAction(id, 'enable')
}

/** 禁用账号并记录原因。 */
export function disableAdminUser(
  id: number | string,
  reason: string,
): Promise<AdminUserItem & { message: string }> {
  return postUserAction(id, 'disable', { reason })
}

/** 封禁账号；到期时间为空时表示长期有效。 */
export function banAdminUser(
  id: number | string,
  payload: AnyRecord,
): Promise<AdminUserItem & { message: string }> {
  return postUserAction(id, 'ban', payload)
}

/** 解除账号全部有效封禁。 */
export function unbanAdminUser(
  id: number | string,
  reason: string,
): Promise<AdminUserItem & { message: string }> {
  return postUserAction(id, 'unban', { reason })
}

/** 使用 BCrypt 重置密码，成功后后端撤销目标账号的旧会话。 */
export function resetAdminUserPassword(
  id: number | string,
  password: string,
): Promise<AdminUserItem & { message: string }> {
  return postUserAction(id, 'reset-password', { password })
}

/** 分配一个或多个后台管理组。 */
export function assignAdminUserGroups(
  id: number | string,
  groupIds: Array<number | string>,
): Promise<AdminUserItem & { message: string }> {
  return postUserAction(id, 'assign-admin-groups', { groupIds })
}

/** 移除一个或多个后台管理组。 */
export function removeAdminUserGroups(
  id: number | string,
  groupIds: Array<number | string>,
): Promise<AdminUserItem & { message: string }> {
  return postUserAction(id, 'remove-admin-groups', { groupIds })
}

/** 调整积分，正数奖励、负数扣除。 */
export function adjustAdminUserPoints(
  id: number | string,
  pointsDelta: number,
  reason: string,
): Promise<AdminUserItem & { message: string }> {
  return postUserAction(id, 'adjust-points', { pointsDelta, reason })
}

/**
 * 安全删除账号：后端保留内部 id 与历史内容关系，释放可复用 uid。
 */
export function deleteAdminUser(
  id: number | string,
): Promise<AnyRecord & { message: string }> {
  return requestJsonWithMessage<AnyRecord>(
    '/api/admin/users/' + encodeURIComponent(String(id)),
    { method: 'DELETE' },
  )
}

/** 统一发送用户领域动作，避免页面各自拼接 URL 与请求头。 */
function postUserAction(
  id: number | string,
  action: string,
  payload?: AnyRecord,
): Promise<AdminUserItem & { message: string }> {
  const init: RequestInit = {
    method: 'POST',
  }
  if (payload !== undefined) {
    init.headers = { 'Content-Type': 'application/json' }
    init.body = JSON.stringify(payload)
  }
  return requestJsonWithMessage<AdminUserItem>(
    '/api/admin/users/' + encodeURIComponent(String(id))
      + '/' + encodeURIComponent(action),
    init,
  )
}

/* END：后台用户写操作。 */

/** 查询普通用户组列表，供用户模块复用只读列表页。 */
export function fetchAdminUserGroups(): Promise<{
  table: string
  total: number
  items: AnyRecord[]
}> {
  return requestJson<{
    table: string
    total: number
    items: AnyRecord[]
  }>('/api/admin/users/groups')
}

/** 查询角色列表，供权限展示和后续角色配置页面使用。 */
export function fetchAdminRoles(): Promise<{
  table: string
  total: number
  items: AnyRecord[]
}> {
  return requestJson<{
    table: string
    total: number
    items: AnyRecord[]
  }>('/api/admin/users/roles')
}

/** 查询后台管理组列表；它与普通用户组、角色是不同的权限维度。 */
export function fetchAdminGroups(): Promise<{
  table: string
  total: number
  items: AnyRecord[]
}> {
  return requestJson<{
    table: string
    total: number
    items: AnyRecord[]
  }>('/api/admin/users/admin-groups')
}

/* ==========================================================================
 * BEGIN：用户组与管理组写操作
 *
 * 用户组与管理组共享字段，但它们属于不同权限维度。type 只在调用端决定 URL，
 * 后端仍分别校验默认用户组和系统内置管理组的保护规则。
 * ========================================================================== */

export type EditableGroupType = 'groups' | 'adminGroups'

export interface EditableGroupPayload {
  groupKey: string
  name: string
  description: string
  sortOrder: number
  isDefault?: boolean
  enabled?: boolean
}

/** 根据页面类型取得稳定 API 路径，禁止页面自行拼接表名。 */
function groupApiPath(type: EditableGroupType): string {
  return type === 'groups'
    ? '/api/admin/users/groups'
    : '/api/admin/users/admin-groups'
}

/** 创建用户组或管理组，并返回后端提交后的最新列表。 */
export function createAdminGroupDefinition(
  type: EditableGroupType,
  payload: EditableGroupPayload,
): Promise<AnyRecord & { message: string }> {
  return requestJsonWithMessage<AnyRecord>(groupApiPath(type), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

/** 修改用户组或管理组；内置保护规则由后端作为最终边界。 */
export function updateAdminGroupDefinition(
  type: EditableGroupType,
  id: number | string,
  payload: EditableGroupPayload,
): Promise<AnyRecord & { message: string }> {
  return requestJsonWithMessage<AnyRecord>(
    groupApiPath(type) + '/' + encodeURIComponent(String(id)),
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
  )
}

/** 删除空的自定义组；默认用户组、内置管理组和仍有关联成员的组会被后端拒绝。 */
export function deleteAdminGroupDefinition(
  type: EditableGroupType,
  id: number | string,
): Promise<AnyRecord & { message: string }> {
  return requestJsonWithMessage<AnyRecord>(
    groupApiPath(type) + '/' + encodeURIComponent(String(id)),
    { method: 'DELETE' },
  )
}

/* END：用户组与管理组写操作。 */
