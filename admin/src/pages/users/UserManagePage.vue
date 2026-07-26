<!--
  后台用户管理主页面。

  BEGIN / END 注释把 Vue 数据层、筛选区、列表区和两个操作抽屉明确分开。
  所有写操作统一调用 admin-users.ts；页面不拼 SQL、不接触密码哈希，也不保存会话令牌。
-->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  adjustAdminUserPoints,
  assignAdminUserGroups,
  banAdminUser,
  changeAdminUserGroup,
  createAdminUser,
  deleteAdminUser,
  disableAdminUser,
  enableAdminUser,
  fetchAdminGroups,
  fetchAdminUserDetail,
  fetchAdminUserGroups,
  fetchAdminUsers,
  removeAdminUserGroups,
  resetAdminUserPassword,
  unbanAdminUser,
  updateAdminUserBasic,
  type AdminUserItem,
  type AnyRecord,
  type PageResult,
} from '../../api/admin-users'
import './user-pages.css'

const props = withDefaults(defineProps<{
  pageTitle?: string
  pageDescription?: string
  adminOnlyDefault?: boolean
}>(), {
  pageTitle: '用户管理',
  pageDescription: '创建、筛选、授权、封禁和安全删除 Aquafish 用户。',
  adminOnlyDefault: false,
})

/* ==========================================================================
 * BEGIN：列表、筛选与引用数据状态
 * ========================================================================== */
const loading = ref(false)
const actionLoading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const userGroups = ref<AnyRecord[]>([])
const adminGroups = ref<AnyRecord[]>([])

const filters = reactive({
  keyword: '',
  status: '',
  adminOnly: props.adminOnlyDefault,
})

const pageData = ref<PageResult<AdminUserItem>>({
  page: 1,
  pageSize: 20,
  total: 0,
  totalPages: 0,
  keyword: '',
  status: '',
  adminOnly: props.adminOnlyDefault,
  message: '',
  users: [],
  items: [],
})

const rows = computed(() => pageData.value.items || [])
/* END：列表、筛选与引用数据状态。 */

/* ==========================================================================
 * BEGIN：新建用户抽屉状态
 * ========================================================================== */
const createOpen = ref(false)
const createForm = reactive({
  username: '',
  email: '',
  password: '',
  displayName: '',
  status: 'ACTIVE',
  groupId: 0,
})

function openCreate() {
  Object.assign(createForm, {
    username: '',
    email: '',
    password: '',
    displayName: '',
    status: 'ACTIVE',
    groupId: Number(defaultUserGroupId()),
  })
  createOpen.value = true
}

async function submitCreate() {
  await runAction(async () => {
    const created = await createAdminUser({ ...createForm })
    createOpen.value = false
    await loadUsers(1)
    await openDetail(created)
    return created.message || '用户创建成功。'
  })
}
/* END：新建用户抽屉状态。 */

/* ==========================================================================
 * BEGIN：用户详情与领域动作状态
 * ========================================================================== */
const detail = ref<AdminUserItem | null>(null)
const detailLoading = ref(false)
const detailError = ref('')
const editForm = reactive({
  username: '',
  email: '',
  displayName: '',
  avatar: '',
  groupId: 0,
  password: '',
  banType: 'login',
  banReason: '',
  expiredAt: '',
  adminGroupId: 0,
  pointsDelta: 0,
  pointsReason: '',
})

async function openDetail(user: AnyRecord) {
  detail.value = null
  detailError.value = ''
  detailLoading.value = true
  try {
    const loaded = await fetchAdminUserDetail(user.id)
    detail.value = loaded
    syncEditForm(loaded)
  } catch (error) {
    detailError.value = messageOf(error, '用户详情加载失败。')
  } finally {
    detailLoading.value = false
  }
}

function closeDetail() {
  if (actionLoading.value) return
  detail.value = null
  detailError.value = ''
}

function syncEditForm(user: AdminUserItem) {
  Object.assign(editForm, {
    username: user.username || '',
    email: user.email || '',
    displayName: user.displayName || '',
    avatar: user.avatar || '',
    groupId: Number(user.groupId || defaultUserGroupId()),
    password: '',
    banType: 'login',
    banReason: '',
    expiredAt: '',
    adminGroupId: Number(adminGroups.value[0]?.id || 0),
    pointsDelta: 0,
    pointsReason: '',
  })
}

function requireDetail(): AdminUserItem {
  if (!detail.value) {
    throw new Error('请先选择用户。')
  }
  return detail.value
}

async function saveBasic() {
  const user = requireDetail()
  await mutateDetail(() => updateAdminUserBasic(user.id, {
    username: editForm.username,
    email: editForm.email,
    displayName: editForm.displayName,
    avatar: editForm.avatar,
  }))
}

async function saveGroup() {
  const user = requireDetail()
  await mutateDetail(() => changeAdminUserGroup(user.id, editForm.groupId))
}

async function toggleEnabled() {
  const user = requireDetail()
  if (String(user.status).toUpperCase() === 'ACTIVE') {
    await mutateDetail(() => disableAdminUser(user.id, editForm.banReason || '后台禁用'))
  } else {
    await mutateDetail(() => enableAdminUser(user.id))
  }
}

async function toggleBan() {
  const user = requireDetail()
  if (String(user.status).toUpperCase() === 'BANNED') {
    await mutateDetail(() => unbanAdminUser(user.id, editForm.banReason || '后台解封'))
    return
  }
  await mutateDetail(() => banAdminUser(user.id, {
    banType: editForm.banType,
    reason: editForm.banReason || '后台手动封禁',
    expiredAt: editForm.expiredAt || null,
  }))
}

async function resetPassword() {
  const user = requireDetail()
  await mutateDetail(() => resetAdminUserPassword(user.id, editForm.password))
  editForm.password = ''
}

async function changeAdminGroup(assign: boolean) {
  const user = requireDetail()
  if (!editForm.adminGroupId) throw new Error('请选择管理组。')
  await mutateDetail(() => assign
    ? assignAdminUserGroups(user.id, [editForm.adminGroupId])
    : removeAdminUserGroups(user.id, [editForm.adminGroupId]))
}

async function adjustPoints() {
  const user = requireDetail()
  await mutateDetail(() => adjustAdminUserPoints(
    user.id,
    Number(editForm.pointsDelta),
    editForm.pointsReason || '后台人工调整',
  ))
  editForm.pointsDelta = 0
  editForm.pointsReason = ''
}

async function removeUser() {
  const user = requireDetail()
  const confirmed = window.confirm(
    `确认删除 UID ${user.uid}（${displayName(user)}）吗？`
    + '\n内部历史内容会保留，但账号凭据将被撤销，UID 会释放给后续新用户。',
  )
  if (!confirmed) return

  await runAction(async () => {
    const result = await deleteAdminUser(user.id)
    closeDetail()
    await loadUsers(pageData.value.page)
    return result.message || '用户已删除。'
  })
}

async function mutateDetail(
  action: () => Promise<AdminUserItem & { message: string }>,
) {
  await runAction(async () => {
    const changed = await action()
    detail.value = changed
    syncEditForm(changed)
    await loadUsers(pageData.value.page)
    return changed.message || '操作成功。'
  })
}
/* END：用户详情与领域动作状态。 */

/* ==========================================================================
 * BEGIN：列表读取、分页与显示辅助
 * ========================================================================== */
async function loadUsers(targetPage = pageData.value.page || 1) {
  loading.value = true
  errorMessage.value = ''
  try {
    pageData.value = await fetchAdminUsers({
      page: targetPage,
      pageSize: pageData.value.pageSize || 20,
      keyword: filters.keyword,
      status: filters.status,
      adminOnly: filters.adminOnly,
    })
  } catch (error) {
    errorMessage.value = messageOf(error, '用户列表加载失败。')
  } finally {
    loading.value = false
  }
}

async function loadReferences() {
  try {
    const [groups, managers] = await Promise.all([
      fetchAdminUserGroups(),
      fetchAdminGroups(),
    ])
    userGroups.value = groups.items || []
    adminGroups.value = managers.items || []
  } catch (error) {
    errorMessage.value = messageOf(error, '用户组或管理组加载失败。')
  }
}

async function runAction(action: () => Promise<string>) {
  actionLoading.value = true
  successMessage.value = ''
  errorMessage.value = ''
  detailError.value = ''
  try {
    successMessage.value = await action()
  } catch (error) {
    detailError.value = messageOf(error, '操作失败。')
  } finally {
    actionLoading.value = false
  }
}

function searchUsers() {
  loadUsers(1)
}

function resetSearch() {
  filters.keyword = ''
  filters.status = ''
  filters.adminOnly = props.adminOnlyDefault
  loadUsers(1)
}

function nextPage() {
  if (pageData.value.page < pageData.value.totalPages) {
    loadUsers(pageData.value.page + 1)
  }
}

function prevPage() {
  if (pageData.value.page > 1) {
    loadUsers(pageData.value.page - 1)
  }
}

function defaultUserGroupId(): number | string {
  return userGroups.value.find((group) => Boolean(group.is_default))?.id
    || userGroups.value[0]?.id
    || 0
}

function displayName(user: AnyRecord): string {
  return String(user.display_name || user.displayName || user.username || '-')
}

function firstLetter(user: AnyRecord): string {
  return displayName(user).slice(0, 1).toUpperCase() || 'U'
}

function formatTime(value: unknown): string {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-'
}

function statusLabel(status: unknown): string {
  const value = String(status || '').toUpperCase()
  return {
    ACTIVE: '正常',
    DISABLED: '禁用',
    BANNED: '封禁',
    PENDING: '待验证',
    DELETED: '已删除',
  }[value] || value || '-'
}

function statusClass(status: unknown): string {
  const value = String(status || '').toUpperCase()
  if (value === 'ACTIVE') return 'is-green'
  if (value === 'DISABLED' || value === 'BANNED') return 'is-red'
  return 'is-gray'
}

function names(list: unknown, key = 'name'): string {
  if (!Array.isArray(list) || list.length === 0) return '-'
  return list
    .map((item) => String(item?.[key] || item?.role_key || item?.group_key || '-'))
    .join('、')
}

function field(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-'
  if (Array.isArray(value)) return value.length ? names(value) : '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function messageOf(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}

onMounted(async () => {
  await loadReferences()
  await loadUsers(1)
})
/* END：列表读取、分页与显示辅助。 */
</script>

<template>
  <section class="admin-user-page admin-page-scrollable">
    <!-- BEGIN：页面标题与全局操作 -->
    <div class="admin-user-page__header">
      <div>
        <h1 class="admin-user-page__title">{{ pageTitle }}</h1>
        <p class="admin-user-page__desc">{{ pageDescription }}</p>
      </div>
      <div class="admin-user-page__actions">
        <button class="admin-user-btn is-ghost" type="button" :disabled="loading" @click="loadUsers()">
          刷新
        </button>
        <button class="admin-user-btn" type="button" @click="openCreate">
          新建用户
        </button>
      </div>
    </div>
    <!-- END：页面标题与全局操作 -->

    <!-- BEGIN：搜索和筛选 -->
    <div class="admin-user-search">
      <input
        v-model="filters.keyword"
        class="admin-user-input"
        type="text"
        placeholder="搜索 UID / 用户名 / 邮箱 / 昵称"
        @keyup.enter="searchUsers"
      >
      <select v-model="filters.status" class="admin-user-select">
        <option value="">全部状态</option>
        <option value="ACTIVE">正常</option>
        <option value="DISABLED">禁用</option>
        <option value="BANNED">封禁</option>
        <option value="PENDING">待验证</option>
      </select>
      <label class="admin-user-checkbox">
        <input v-model="filters.adminOnly" type="checkbox">
        只看管理员
      </label>
      <button class="admin-user-btn" type="button" :disabled="loading" @click="searchUsers">查询</button>
      <button class="admin-user-btn is-ghost" type="button" :disabled="loading" @click="resetSearch">重置</button>
    </div>
    <!-- END：搜索和筛选 -->

    <p v-if="successMessage" class="admin-user-notice is-success">{{ successMessage }}</p>
    <p v-if="errorMessage" class="admin-user-notice is-error">{{ errorMessage }}</p>

    <!-- BEGIN：用户数据表 -->
    <div class="admin-user-card">
      <div class="admin-user-table-wrap">
        <table class="admin-user-table">
          <thead>
            <tr>
              <th>UID</th>
              <th>用户</th>
              <th>状态</th>
              <th>用户组</th>
              <th>角色</th>
              <th>管理组</th>
              <th>最后登录</th>
              <th>注册时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="9"><div class="admin-user-empty">正在加载用户数据...</div></td>
            </tr>
            <tr v-else-if="rows.length === 0">
              <td colspan="9"><div class="admin-user-empty">暂无符合条件的用户</div></td>
            </tr>
            <tr v-for="user in rows" v-else :key="user.id">
              <td><strong>#{{ user.uid }}</strong></td>
              <td>
                <div class="admin-user-main">
                  <div class="admin-user-avatar">{{ firstLetter(user) }}</div>
                  <div class="admin-user-name">
                    <strong>{{ displayName(user) }}</strong>
                    <span>{{ user.username }} · {{ user.email || '未填写邮箱' }}</span>
                  </div>
                </div>
              </td>
              <td><span class="admin-user-badge" :class="statusClass(user.status)">{{ statusLabel(user.status) }}</span></td>
              <td>{{ user.userGroup?.name || '-' }}</td>
              <td>{{ names(user.roles) }}</td>
              <td>{{ names(user.adminGroups) }}</td>
              <td>{{ formatTime(user.last_login_at) }}</td>
              <td>{{ formatTime(user.created_at) }}</td>
              <td><button class="admin-user-btn is-ghost" type="button" @click="openDetail(user)">管理</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="admin-user-footer">
        <span>共 {{ pageData.total }} 条，第 {{ pageData.page }} / {{ pageData.totalPages || 1 }} 页</span>
        <div class="admin-user-page__actions">
          <button class="admin-user-btn is-ghost" type="button" :disabled="loading || pageData.page <= 1" @click="prevPage">上一页</button>
          <button class="admin-user-btn is-ghost" type="button" :disabled="loading || pageData.page >= pageData.totalPages" @click="nextPage">下一页</button>
        </div>
      </div>
    </div>
    <!-- END：用户数据表 -->

    <!-- BEGIN：新建用户抽屉 -->
    <div v-if="createOpen" class="admin-user-modal-mask" @click.self="createOpen = false">
      <div class="admin-user-modal is-compact">
        <div class="admin-user-modal__header">
          <div><h3>新建用户</h3><p>UID 由系统自动分配最小空缺值。</p></div>
          <button class="admin-user-btn is-ghost" type="button" @click="createOpen = false">关闭</button>
        </div>
        <div class="admin-user-form-grid">
          <label>用户名<input v-model="createForm.username" class="admin-user-input" autocomplete="off"></label>
          <label>邮箱<input v-model="createForm.email" class="admin-user-input" type="email"></label>
          <label>显示名称<input v-model="createForm.displayName" class="admin-user-input"></label>
          <label>初始密码<input v-model="createForm.password" class="admin-user-input" type="password" autocomplete="new-password"></label>
          <label>用户组
            <select v-model.number="createForm.groupId" class="admin-user-select">
              <option v-for="group in userGroups" :key="group.id" :value="Number(group.id)">{{ group.name }}</option>
            </select>
          </label>
          <label>状态
            <select v-model="createForm.status" class="admin-user-select">
              <option value="ACTIVE">正常</option><option value="DISABLED">禁用</option><option value="PENDING">待验证</option>
            </select>
          </label>
        </div>
        <div class="admin-user-modal__footer">
          <button class="admin-user-btn" type="button" :disabled="actionLoading" @click="submitCreate">创建用户</button>
        </div>
      </div>
    </div>
    <!-- END：新建用户抽屉 -->

    <!-- BEGIN：用户详情与管理抽屉 -->
    <div v-if="detail || detailLoading || detailError" class="admin-user-modal-mask" @click.self="closeDetail">
      <div class="admin-user-modal is-wide">
        <div class="admin-user-modal__header">
          <div>
            <h3>用户管理 <span v-if="detail">#{{ detail.uid }}</span></h3>
            <p v-if="detail">内部主键 {{ detail.id }} · 公开编号 {{ detail.public_id || '-' }}</p>
          </div>
          <button class="admin-user-btn is-ghost" type="button" :disabled="actionLoading" @click="closeDetail">关闭</button>
        </div>
        <div v-if="detailLoading" class="admin-user-empty">正在加载详情...</div>
        <p v-if="detailError" class="admin-user-notice is-error">{{ detailError }}</p>

        <div v-if="detail" class="admin-user-manage-sections">
          <section class="admin-user-manage-section">
            <h4>基础资料</h4>
            <div class="admin-user-form-grid">
              <label>用户名<input v-model="editForm.username" class="admin-user-input"></label>
              <label>邮箱<input v-model="editForm.email" class="admin-user-input" type="email"></label>
              <label>显示名称<input v-model="editForm.displayName" class="admin-user-input"></label>
              <label>头像地址<input v-model="editForm.avatar" class="admin-user-input"></label>
            </div>
            <button class="admin-user-btn" type="button" :disabled="actionLoading" @click="saveBasic">保存资料</button>
          </section>

          <section class="admin-user-manage-section">
            <h4>用户组与账号状态</h4>
            <div class="admin-user-inline-form">
              <select v-model.number="editForm.groupId" class="admin-user-select">
                <option v-for="group in userGroups" :key="group.id" :value="Number(group.id)">{{ group.name }}</option>
              </select>
              <button class="admin-user-btn" type="button" :disabled="actionLoading" @click="saveGroup">保存用户组</button>
              <button class="admin-user-btn is-ghost" type="button" :disabled="actionLoading" @click="toggleEnabled">
                {{ String(detail.status).toUpperCase() === 'ACTIVE' ? '禁用账号' : '启用账号' }}
              </button>
            </div>
          </section>

          <section class="admin-user-manage-section">
            <h4>封禁管理</h4>
            <div class="admin-user-form-grid">
              <label>封禁类型
                <select v-model="editForm.banType" class="admin-user-select">
                  <option value="login">禁止登录</option><option value="post">禁止发帖</option><option value="all">全部封禁</option>
                </select>
              </label>
              <label>到期时间<input v-model="editForm.expiredAt" class="admin-user-input" type="datetime-local"></label>
              <label class="is-full">原因<input v-model="editForm.banReason" class="admin-user-input"></label>
            </div>
            <button class="admin-user-btn" type="button" :disabled="actionLoading" @click="toggleBan">
              {{ String(detail.status).toUpperCase() === 'BANNED' ? '解除封禁' : '封禁用户' }}
            </button>
          </section>

          <section class="admin-user-manage-section">
            <h4>管理权限</h4>
            <div class="admin-user-inline-form">
              <select v-model.number="editForm.adminGroupId" class="admin-user-select">
                <option :value="0">选择管理组</option>
                <option v-for="group in adminGroups" :key="group.id" :value="Number(group.id)">{{ group.name }}</option>
              </select>
              <button class="admin-user-btn" type="button" :disabled="actionLoading" @click="changeAdminGroup(true)">授予管理组</button>
              <button class="admin-user-btn is-ghost" type="button" :disabled="actionLoading" @click="changeAdminGroup(false)">移除管理组</button>
            </div>
            <p>当前：{{ names(detail.adminGroups) }}</p>
          </section>

          <section class="admin-user-manage-section">
            <h4>密码与积分</h4>
            <div class="admin-user-form-grid">
              <label>新密码<input v-model="editForm.password" class="admin-user-input" type="password" autocomplete="new-password"></label>
              <div class="admin-user-form-action"><button class="admin-user-btn is-ghost" type="button" :disabled="actionLoading" @click="resetPassword">重置密码</button></div>
              <label>积分变动<input v-model.number="editForm.pointsDelta" class="admin-user-input" type="number"></label>
              <label>积分原因<input v-model="editForm.pointsReason" class="admin-user-input"></label>
            </div>
            <button class="admin-user-btn is-ghost" type="button" :disabled="actionLoading" @click="adjustPoints">提交积分调整</button>
          </section>

          <section class="admin-user-manage-section is-danger">
            <h4>安全删除</h4>
            <p>保留历史文章、帖子和审计关系；撤销账号凭据，并把 UID {{ detail.uid }} 释放给后续新用户。</p>
            <button class="admin-user-btn is-danger" type="button" :disabled="actionLoading" @click="removeUser">删除用户</button>
          </section>

          <details class="admin-user-manage-section">
            <summary>只读数据摘要</summary>
            <dl class="admin-user-detail-grid">
              <dt>状态</dt><dd>{{ statusLabel(detail.status) }}</dd>
              <dt>注册时间</dt><dd>{{ formatTime(detail.created_at) }}</dd>
              <dt>最后登录</dt><dd>{{ formatTime(detail.last_login_at) }}</dd>
              <dt>角色</dt><dd>{{ names(detail.roles) }}</dd>
              <dt>标签</dt><dd>{{ names(detail.tags) }}</dd>
              <dt>封禁记录</dt><dd>{{ Array.isArray(detail.bans) ? detail.bans.length + ' 条' : '0 条' }}</dd>
              <dt>积分记录</dt><dd>{{ Array.isArray(detail.recentPointsLogs) ? detail.recentPointsLogs.length + ' 条' : '0 条' }}</dd>
              <dt>资料</dt><dd>{{ field(detail.profile) }}</dd>
            </dl>
          </details>
        </div>
      </div>
    </div>
    <!-- END：用户详情与管理抽屉 -->
  </section>
</template>
