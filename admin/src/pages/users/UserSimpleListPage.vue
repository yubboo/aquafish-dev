<!--
  BEGIN：用户组、角色和后台管理组页面。

  type 属性决定真实 API：
  - groups：前台用户组，可增删改；
  - adminGroups：后台管理组，可增删改；
  - roles：系统角色，只读，避免在 RBAC 尚未形成完整闭环时误改底层角色。

  END：页面职责说明。
-->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  createAdminGroupDefinition,
  deleteAdminGroupDefinition,
  fetchAdminGroups,
  fetchAdminRoles,
  fetchAdminUserGroups,
  type AnyRecord,
  type EditableGroupPayload,
  type EditableGroupType,
  updateAdminGroupDefinition,
} from '../../api/admin-users'
import './user-pages.css'

const props = withDefaults(defineProps<{
  type: 'groups' | 'roles' | 'adminGroups'
  title?: string
  description?: string
}>(), {
  title: '',
  description: '',
})

const loading = ref(false)
const errorMessage = ref('')
const rows = ref<AnyRecord[]>([])
const tableName = ref('')
const noticeMessage = ref('')
const noticeType = ref<'success' | 'error'>('success')
const editorOpen = ref(false)
const editingRow = ref<AnyRecord | null>(null)
const submitting = ref(false)
const form = ref<EditableGroupPayload>(emptyForm())

/** 角色由 RBAC 底层维护，本页面只允许用户组和管理组进入写流程。 */
const editable = computed(() => props.type !== 'roles')

/** 把路由属性收窄为 API 接受的可编辑组类型。 */
const editableType = computed<EditableGroupType>(() =>
  props.type === 'groups' ? 'groups' : 'adminGroups',
)

/** 根据复用类型生成当前列表标题。 */
const pageTitle = computed(() => {
  if (props.title) return props.title
  if (props.type === 'groups') return '用户组'
  if (props.type === 'roles') return '角色列表'
  return '管理组'
})

/** 说明当前列表对应的权限维度，避免把角色、用户组和管理组混为一谈。 */
const pageDescription = computed(() => {
  if (props.description) return props.description
  if (props.type === 'groups') {
    return '前台用户组，用于控制社区身份、积分、发帖、回帖和阅读权限。'
  }
  if (props.type === 'roles') {
    return '系统角色，用于兼容基础登录和后台访问身份。'
  }
  return '后台管理组，用于控制管理员能访问哪些菜单、按钮、接口和模块。'
})

/** 按 type 选择真实后端接口，统一维护加载、错误和列表状态。 */
async function loadData() {
  loading.value = true
  errorMessage.value = ''

  try {
    const data = props.type === 'groups'
      ? await fetchAdminUserGroups()
      : props.type === 'roles'
        ? await fetchAdminRoles()
        : await fetchAdminGroups()

    rows.value = data.items || []
    tableName.value = data.table || ''
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '数据加载失败'
  } finally {
    loading.value = false
  }
}

/* ==========================================================================
 * BEGIN：分组编辑状态
 * ========================================================================== */

/** 返回创建分组时的默认表单，避免复用上一次编辑残留。 */
function emptyForm(): EditableGroupPayload {
  return {
    groupKey: '',
    name: '',
    description: '',
    sortOrder: 100,
    isDefault: false,
    enabled: true,
  }
}

/** 打开创建弹窗并恢复当前类型对应的安全默认值。 */
function openCreate() {
  editingRow.value = null
  form.value = emptyForm()
  editorOpen.value = true
}

/** 打开编辑弹窗，把数据库 snake_case 字段转换为表单字段。 */
function openEdit(row: AnyRecord) {
  editingRow.value = row
  form.value = {
    groupKey: String(row.group_key || ''),
    name: String(row.name || ''),
    description: String(row.description || ''),
    sortOrder: Number(row.sort_order ?? 100),
    isDefault: Boolean(row.is_default),
    enabled: row.enabled === undefined ? true : Boolean(row.enabled),
  }
  editorOpen.value = true
}

/** 关闭弹窗；提交进行中禁止关闭，避免用户误以为请求已取消。 */
function closeEditor() {
  if (!submitting.value) {
    editorOpen.value = false
  }
}

/** 判断当前行是否受系统保护，并为按钮和说明复用。 */
function protectedRow(row: AnyRecord): boolean {
  return props.type === 'groups'
    ? Boolean(row.is_default)
    : Boolean(row.built_in)
}

/** 校验最小表单规则，完整唯一性和保护规则仍由后端执行。 */
function validateForm(): string {
  if (!/^[a-z][a-z0-9_]{1,63}$/.test(form.value.groupKey.trim())) {
    return 'Key 必须以小写字母开头，只能包含小写字母、数字和下划线。'
  }
  if (!form.value.name.trim()) {
    return '名称不能为空。'
  }
  if (!Number.isFinite(Number(form.value.sortOrder))
    || Number(form.value.sortOrder) < 0) {
    return '排序值必须是大于等于 0 的数字。'
  }
  return ''
}

/** 创建或修改当前分组；成功后重新读取数据库，避免本地状态与服务端不一致。 */
async function submitEditor() {
  const validation = validateForm()
  if (validation) {
    showNotice(validation, 'error')
    return
  }

  submitting.value = true
  try {
    const payload: EditableGroupPayload = {
      ...form.value,
      groupKey: form.value.groupKey.trim(),
      name: form.value.name.trim(),
      description: form.value.description.trim(),
      sortOrder: Number(form.value.sortOrder),
    }
    const result = editingRow.value
      ? await updateAdminGroupDefinition(
        editableType.value,
        editingRow.value.id,
        payload,
      )
      : await createAdminGroupDefinition(editableType.value, payload)

    editorOpen.value = false
    showNotice(result.message, 'success')
    await loadData()
  } catch (error) {
    showNotice(
      error instanceof Error ? error.message : '分组保存失败。',
      'error',
    )
  } finally {
    submitting.value = false
  }
}

/** 删除自定义空分组；默认组、内置组和存在成员的组由前后端双重保护。 */
async function removeRow(row: AnyRecord) {
  if (protectedRow(row)) {
    showNotice(
      props.type === 'groups'
        ? '默认用户组不能删除。'
        : '系统内置管理组不能删除。',
      'error',
    )
    return
  }
  if (!window.confirm(`确定删除“${row.name || row.group_key}”吗？`)) {
    return
  }

  try {
    const result = await deleteAdminGroupDefinition(editableType.value, row.id)
    showNotice(result.message, 'success')
    await loadData()
  } catch (error) {
    showNotice(
      error instanceof Error ? error.message : '分组删除失败。',
      'error',
    )
  }
}

/** 在页面顶部显示本次写操作结果。 */
function showNotice(message: string, type: 'success' | 'error') {
  noticeMessage.value = message
  noticeType.value = type
}

/* END：分组编辑状态。 */

/** 把任意后端字段转换为表格可读文本，空值统一显示短横线。 */
function formatValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'boolean') return value ? '是' : '否'
  return String(value).replace('T', ' ').slice(0, 19)
}

/** 数字布尔字段单独格式化，避免把排序值 0/1 误显示为是否。 */
function formatFlag(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-'
  return Boolean(Number(value)) ? '是' : '否'
}

/** 根据行状态返回共享 CSS 中对应的状态色类。 */
function statusClass(row: AnyRecord): string {
  if (row.enabled === false || row.enabled === 0 || row.status === 'DISABLED') {
    return 'is-red'
  }
  if (row.enabled === true || row.enabled === 1 || row.status === 'ACTIVE') {
    return 'is-green'
  }
  return 'is-gray'
}

/** 从 enabled/status 兼容字段中提取并规范化状态文案。 */
function statusText(row: AnyRecord): string {
  if (row.enabled === false || row.enabled === 0) return '禁用'
  if (row.enabled === true || row.enabled === 1) return '启用'
  if (row.status) return String(row.status)
  return '-'
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <section class="admin-user-page admin-page-scrollable">
    <!-- BEGIN：页面标题和主操作。 -->
    <div class="admin-user-page__header">
      <div>
        <h1 class="admin-user-page__title">{{ pageTitle }}</h1>
        <p class="admin-user-page__desc">{{ pageDescription }}</p>
      </div>

      <div class="admin-user-page__actions">
        <button
          class="admin-user-btn is-ghost"
          type="button"
          :disabled="loading"
          @click="loadData"
        >
          刷新
        </button>
        <button
          v-if="editable"
          class="admin-user-btn"
          type="button"
          @click="openCreate"
        >
          新建{{ pageTitle }}
        </button>
      </div>
    </div>
    <!-- END：页面标题和主操作。 -->

    <div
      v-if="noticeMessage"
      class="admin-user-notice"
      :class="noticeType === 'success' ? 'is-success' : 'is-error'"
    >
      {{ noticeMessage }}
    </div>

    <div v-if="errorMessage" class="admin-user-empty">
      {{ errorMessage }}
    </div>

    <!-- BEGIN：真实数据库分组列表。 -->
    <div class="admin-user-card">
      <div class="admin-user-table-wrap">
        <table class="admin-user-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Key</th>
              <th>名称</th>
              <th>说明</th>
              <th>状态</th>
              <th>排序</th>
              <th>内置 / 默认</th>
              <th>创建时间</th>
              <th v-if="editable">操作</th>
            </tr>
          </thead>

          <tbody>
            <tr v-if="loading">
              <td :colspan="editable ? 9 : 8">
                <div class="admin-user-empty">正在加载数据...</div>
              </td>
            </tr>

            <tr v-else-if="rows.length === 0">
              <td :colspan="editable ? 9 : 8">
                <div class="admin-user-empty">暂无数据</div>
              </td>
            </tr>

            <tr v-for="row in rows" v-else :key="row.id">
              <td>{{ row.id }}</td>
              <td>{{ row.group_key || row.role_key || '-' }}</td>
              <td><strong>{{ row.name || '-' }}</strong></td>
              <td>{{ row.description || '-' }}</td>
              <td>
                <span class="admin-user-badge" :class="statusClass(row)">
                  {{ statusText(row) }}
                </span>
              </td>
              <td>{{ formatValue(row.sort_order) }}</td>
              <td>{{ formatFlag(row.built_in ?? row.is_default) }}</td>
              <td>{{ formatValue(row.created_at) }}</td>
              <td v-if="editable">
                <div class="admin-user-row-actions">
                  <button
                    class="admin-user-link"
                    type="button"
                    @click="openEdit(row)"
                  >
                    编辑
                  </button>
                  <button
                    class="admin-user-link is-danger"
                    type="button"
                    :disabled="protectedRow(row)"
                    @click="removeRow(row)"
                  >
                    删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="admin-user-footer">
        <span>数据表：{{ tableName || '-' }}</span>
        <span>共 {{ rows.length }} 条</span>
      </div>
    </div>
    <!-- END：真实数据库分组列表。 -->

    <!-- BEGIN：用户组 / 管理组编辑弹窗。 -->
    <div
      v-if="editorOpen"
      class="admin-user-modal-mask"
      @click.self="closeEditor"
    >
      <section class="admin-user-modal is-compact" role="dialog" aria-modal="true">
        <header class="admin-user-modal__header">
          <div>
            <h3>{{ editingRow ? `编辑${pageTitle}` : `新建${pageTitle}` }}</h3>
            <p>
              {{
                props.type === 'groups'
                  ? '用户组负责前台身份；默认组用于新注册用户。'
                  : '管理组负责后台权限；系统内置组的 Key 和启用状态受保护。'
              }}
            </p>
          </div>
          <button type="button" class="admin-user-link" @click="closeEditor">
            关闭
          </button>
        </header>

        <div class="admin-user-modal__body">
          <div class="admin-user-form-grid">
            <label>
              <span>稳定 Key</span>
              <input
                v-model="form.groupKey"
                class="admin-user-input"
                :disabled="Boolean(editingRow?.built_in)"
                placeholder="member_plus"
              >
            </label>

            <label>
              <span>名称</span>
              <input
                v-model="form.name"
                class="admin-user-input"
                placeholder="高级会员"
              >
            </label>

            <label>
              <span>排序</span>
              <input
                v-model.number="form.sortOrder"
                class="admin-user-input"
                type="number"
                min="0"
              >
            </label>

            <label v-if="props.type === 'groups'" class="admin-user-checkbox">
              <input v-model="form.isDefault" type="checkbox">
              <span>设为默认用户组</span>
            </label>

            <label v-else class="admin-user-checkbox">
              <input
                v-model="form.enabled"
                type="checkbox"
                :disabled="Boolean(editingRow?.built_in)"
              >
              <span>启用管理组</span>
            </label>

            <label class="is-full">
              <span>说明</span>
              <textarea
                v-model="form.description"
                class="admin-user-input admin-user-group-editor__description"
                rows="4"
              />
            </label>
          </div>
        </div>

        <footer class="admin-user-modal__footer">
          <button
            class="admin-user-btn is-ghost"
            type="button"
            :disabled="submitting"
            @click="closeEditor"
          >
            取消
          </button>
          <button
            class="admin-user-btn"
            type="button"
            :disabled="submitting"
            @click="submitEditor"
          >
            {{ submitting ? '正在保存...' : '保存' }}
          </button>
        </footer>
      </section>
    </div>
    <!-- END：用户组 / 管理组编辑弹窗。 -->
  </section>
</template>
