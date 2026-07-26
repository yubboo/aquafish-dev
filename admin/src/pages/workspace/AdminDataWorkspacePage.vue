<!--
  BEGIN：用户、论坛、内容域的通用真实数据页。

  所有列和记录由后端白名单投影返回，页面不接受表名，也不自行拼 SQL。
  users/ip-bans 和 users/bans 在共享只读表格上增加受控领域动作：
  IP 封禁支持完整 CRUD；用户封禁可实时解除并同步用户状态。

  END：页面职责说明。
-->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  createIpBan,
  deleteIpBan,
  loadWorkspace,
  setIpBanEnabled,
  type IpBanPayload,
  type WorkspacePage,
  type WorkspaceRow,
  updateIpBan,
} from '../../api/admin-workspace'
import { unbanAdminUser } from '../../api/admin-users'
import './admin-workspace.css'

const props = defineProps<{
  domain: string
  resource: string
  title: string
  description: string
}>()

const data = ref<WorkspacePage | null>(null)
const loading = ref(false)
const errorMessage = ref('')
const noticeMessage = ref('')
const noticeType = ref<'success' | 'error'>('success')
const editorOpen = ref(false)
const editingRow = ref<WorkspaceRow | null>(null)
const submitting = ref(false)
const ipBanForm = ref<IpBanPayload>(emptyIpBanForm())

const isIpBanResource = computed(() =>
  props.domain === 'users' && props.resource === 'ip-bans',
)
const isUserBanResource = computed(() =>
  props.domain === 'users' && props.resource === 'bans',
)
const hasRowActions = computed(() =>
  isIpBanResource.value || isUserBanResource.value,
)

/** 重新读取真实数据库；写操作成功后也调用本方法保证列表即时一致。 */
async function load(page = 1) {
  loading.value = true
  errorMessage.value = ''
  try {
    data.value = await loadWorkspace(props.domain, props.resource, page, 20)
  } catch (error) {
    data.value = null
    errorMessage.value = error instanceof Error ? error.message : '数据读取失败。'
  } finally {
    loading.value = false
  }
}

/** 把任意数据库单元格转换为适合表格显示的短文本。 */
function cell(row: WorkspaceRow, column: string): string {
  const value = row[column]
  if (value === null || value === undefined || value === '') {
    return '—'
  }
  const text = typeof value === 'object' ? JSON.stringify(value) : String(value)
  return text.length > 160 ? `${text.slice(0, 160)}…` : text
}

/* ==========================================================================
 * BEGIN：IP 封禁 CRUD
 * ========================================================================== */

function emptyIpBanForm(): IpBanPayload {
  return {
    ipValue: '',
    banType: 'access',
    reason: '',
    expiredAt: null,
    enabled: true,
  }
}

function openIpBanCreate() {
  editingRow.value = null
  ipBanForm.value = emptyIpBanForm()
  editorOpen.value = true
}

function openIpBanEdit(row: WorkspaceRow) {
  editingRow.value = row
  const expiredAt = row.expired_at ? String(row.expired_at).slice(0, 16) : null
  ipBanForm.value = {
    ipValue: String(row.ip_value || ''),
    banType: (String(row.ban_type || 'access') as IpBanPayload['banType']),
    reason: String(row.reason || ''),
    expiredAt,
    enabled: enabled(row),
  }
  editorOpen.value = true
}

function closeEditor() {
  if (!submitting.value) {
    editorOpen.value = false
  }
}

function validateIpBan(): string {
  const value = ipBanForm.value.ipValue.trim()
  if (!value) return 'IP 或 CIDR 不能为空。'
  if (!/^[0-9A-Fa-f:./]+$/.test(value)) {
    return '只允许 IPv4、IPv6 或 CIDR，不允许主机名。'
  }
  if (!ipBanForm.value.reason.trim()) return '请填写封禁原因。'
  return ''
}

async function submitIpBan() {
  const validation = validateIpBan()
  if (validation) {
    showNotice(validation, 'error')
    return
  }
  submitting.value = true
  try {
    const payload: IpBanPayload = {
      ...ipBanForm.value,
      ipValue: ipBanForm.value.ipValue.trim(),
      reason: ipBanForm.value.reason.trim(),
      expiredAt: ipBanForm.value.expiredAt || null,
    }
    const result = editingRow.value
      ? await updateIpBan(String(editingRow.value.id), payload)
      : await createIpBan(payload)
    editorOpen.value = false
    showNotice(result.message, 'success')
    await load(data.value?.page || 1)
  } catch (error) {
    showNotice(error instanceof Error ? error.message : 'IP 封禁保存失败。', 'error')
  } finally {
    submitting.value = false
  }
}

async function toggleIpBan(row: WorkspaceRow) {
  try {
    const result = await setIpBanEnabled(String(row.id), !enabled(row))
    showNotice(result.message, 'success')
    await load(data.value?.page || 1)
  } catch (error) {
    showNotice(error instanceof Error ? error.message : 'IP 封禁状态修改失败。', 'error')
  }
}

async function removeIpBan(row: WorkspaceRow) {
  if (!window.confirm(`确定删除 IP 规则“${row.ip_value || row.id}”吗？`)) return
  try {
    const result = await deleteIpBan(String(row.id))
    showNotice(result.message, 'success')
    await load(data.value?.page || 1)
  } catch (error) {
    showNotice(error instanceof Error ? error.message : 'IP 封禁删除失败。', 'error')
  }
}

/* END：IP 封禁 CRUD。 */

/* ==========================================================================
 * BEGIN：用户封禁列表动作
 * ========================================================================== */

/** 从禁止用户列表解除封禁；后端同时关闭封禁记录并恢复用户状态。 */
async function unbanUserRow(row: WorkspaceRow) {
  if (!row.user_id) {
    showNotice('封禁记录缺少用户 ID，无法解除。', 'error')
    return
  }
  if (!window.confirm(`确定解除用户 ${row.user_id} 的全部有效封禁吗？`)) return
  try {
    const result = await unbanAdminUser(
      String(row.user_id),
      '从禁止用户列表解除封禁',
    )
    showNotice(result.message, 'success')
    await load(data.value?.page || 1)
  } catch (error) {
    showNotice(error instanceof Error ? error.message : '用户解禁失败。', 'error')
  }
}

/* END：用户封禁列表动作。 */

function enabled(row: WorkspaceRow): boolean {
  return row.enabled === true || Number(row.enabled) === 1
}

function showNotice(message: string, type: 'success' | 'error') {
  noticeMessage.value = message
  noticeType.value = type
}

watch(
  () => [props.domain, props.resource],
  () => {
    noticeMessage.value = ''
    editorOpen.value = false
    void load()
  },
)
onMounted(() => void load())
</script>

<template>
  <section class="admin-workspace-page">
    <!-- BEGIN：页面标题和资源操作。 -->
    <header class="admin-workspace-hero">
      <div>
        <span class="admin-workspace-eyebrow">{{ domain }} / {{ resource }}</span>
        <h2>{{ title }}</h2>
        <p>{{ description }}</p>
      </div>
      <div class="admin-workspace-hero-actions">
        <button
          v-if="isIpBanResource"
          type="button"
          @click="openIpBanCreate"
        >
          新增 IP 封禁
        </button>
        <button type="button" :disabled="loading" @click="load(data?.page || 1)">
          {{ loading ? '读取中…' : '重新读取' }}
        </button>
      </div>
    </header>
    <!-- END：页面标题和资源操作。 -->

    <div
      v-if="noticeMessage"
      class="admin-workspace-state"
      :class="noticeType === 'success' ? 'is-success' : 'is-error'"
    >
      <strong>{{ noticeType === 'success' ? '操作成功' : '操作失败' }}</strong>
      <p>{{ noticeMessage }}</p>
    </div>

    <div v-if="errorMessage" class="admin-workspace-state is-error">
      <strong>真实数据读取失败</strong>
      <p>{{ errorMessage }}</p>
    </div>

    <div v-else-if="loading && !data" class="admin-workspace-state">
      正在读取数据库…
    </div>

    <!-- BEGIN：白名单数据表格。 -->
    <section v-else-if="data" class="admin-workspace-card">
      <div class="admin-workspace-summary">
        <div>
          <span>数据资源</span>
          <strong>{{ data.title }}</strong>
        </div>
        <div>
          <span>真实表名</span>
          <strong>{{ data.table }}</strong>
        </div>
        <div>
          <span>记录总数</span>
          <strong>{{ data.total }}</strong>
        </div>
      </div>

      <div class="admin-workspace-table-wrap">
        <table v-if="data.items.length" class="admin-workspace-table">
          <thead>
            <tr>
              <th v-for="column in data.columns" :key="column">{{ column }}</th>
              <th v-if="hasRowActions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, rowIndex) in data.items" :key="String(row.id ?? rowIndex)">
              <td v-for="column in data.columns" :key="column" :title="cell(row, column)">
                {{ cell(row, column) }}
              </td>
              <td v-if="hasRowActions">
                <div v-if="isIpBanResource" class="admin-workspace-row-actions">
                  <button
                    class="admin-workspace-action is-secondary"
                    type="button"
                    @click="openIpBanEdit(row)"
                  >
                    编辑
                  </button>
                  <button
                    class="admin-workspace-action is-secondary"
                    type="button"
                    @click="toggleIpBan(row)"
                  >
                    {{ enabled(row) ? '停用' : '启用' }}
                  </button>
                  <button
                    class="admin-workspace-action is-danger"
                    type="button"
                    @click="removeIpBan(row)"
                  >
                    删除
                  </button>
                </div>
                <button
                  v-else-if="isUserBanResource && enabled(row)"
                  class="admin-workspace-action is-secondary"
                  type="button"
                  @click="unbanUserRow(row)"
                >
                  解除封禁
                </button>
                <span v-else class="admin-workspace-operation-note">已解除</span>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="admin-workspace-empty">
          <strong>当前还没有记录</strong>
          <p>页面已经连接真实数据库；后续产生业务数据后会直接显示在这里。</p>
        </div>
      </div>

      <footer class="admin-workspace-pagination">
        <span>第 {{ data.page }} / {{ Math.max(data.totalPages, 1) }} 页</span>
        <div>
          <button
            type="button"
            :disabled="loading || data.page <= 1"
            @click="load(data.page - 1)"
          >
            上一页
          </button>
          <button
            type="button"
            :disabled="loading || data.page >= data.totalPages"
            @click="load(data.page + 1)"
          >
            下一页
          </button>
        </div>
      </footer>
    </section>
    <!-- END：白名单数据表格。 -->

    <!-- BEGIN：IP 封禁编辑弹窗。 -->
    <div
      v-if="editorOpen"
      class="admin-workspace-modal-mask"
      @click.self="closeEditor"
    >
      <section class="admin-workspace-modal" role="dialog" aria-modal="true">
        <div class="admin-workspace-form-heading">
          <div>
            <h3>{{ editingRow ? '编辑 IP 封禁' : '新增 IP 封禁' }}</h3>
            <p>支持单个 IPv4、IPv6 和 CIDR 网段，保存后登录与注册立即生效。</p>
          </div>
          <button
            class="admin-workspace-action is-secondary"
            type="button"
            @click="closeEditor"
          >
            关闭
          </button>
        </div>

        <div class="admin-workspace-fields">
          <label class="admin-workspace-field">
            <span>IP / CIDR</span>
            <input v-model="ipBanForm.ipValue" placeholder="203.0.113.0/24">
          </label>

          <label class="admin-workspace-field">
            <span>封禁范围</span>
            <select v-model="ipBanForm.banType">
              <option value="access">全部登录与注册</option>
              <option value="login">仅登录</option>
              <option value="register">仅注册</option>
            </select>
          </label>

          <label class="admin-workspace-field">
            <span>到期时间（可空）</span>
            <input v-model="ipBanForm.expiredAt" type="datetime-local">
          </label>

          <label class="admin-workspace-field">
            <span>状态</span>
            <span class="admin-workspace-switch">
              <input v-model="ipBanForm.enabled" type="checkbox">
              立即启用
            </span>
          </label>

          <label class="admin-workspace-field is-wide">
            <span>封禁原因</span>
            <textarea
              v-model="ipBanForm.reason"
              placeholder="记录真实原因，便于后续审计和解除。"
            />
          </label>
        </div>

        <div class="admin-workspace-form-actions">
          <button
            class="admin-workspace-action is-secondary"
            type="button"
            :disabled="submitting"
            @click="closeEditor"
          >
            取消
          </button>
          <button
            class="admin-workspace-action"
            type="button"
            :disabled="submitting"
            @click="submitIpBan"
          >
            {{ submitting ? '正在保存…' : '保存规则' }}
          </button>
        </div>
      </section>
    </div>
    <!-- END：IP 封禁编辑弹窗。 -->
  </section>
</template>
