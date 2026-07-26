<!-- 论坛板块真实维护页：新增、修改、启停全部调用论坛领域 API。 -->
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { adminRequest } from '../../api/admin-workspace'
import '../workspace/admin-workspace.css'

interface ForumSection {
  id: number
  parentId: number | null
  sectionKey: string
  name: string
  description: string
  icon: string
  sortOrder: number
  visibility: string
  postingPolicy: string
  moderationPolicy: string
  threadCount: number
  postCount: number
  enabled: boolean
}

const sections = ref<ForumSection[]>([])
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const editingId = ref<number | null>(null)
const form = reactive({
  parentId: 0,
  sectionKey: '',
  name: '',
  description: '',
  icon: '💬',
  sortOrder: 0,
  visibility: 'PUBLIC',
  postingPolicy: 'MEMBERS',
  moderationPolicy: 'NONE',
  enabled: true,
})

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    sections.value = await adminRequest<ForumSection[]>('/api/admin/forum/sections')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '论坛板块读取失败。'
  } finally {
    loading.value = false
  }
}

function edit(section: ForumSection) {
  editingId.value = section.id
  Object.assign(form, {
    parentId: section.parentId || 0,
    sectionKey: section.sectionKey,
    name: section.name,
    description: section.description,
    icon: section.icon,
    sortOrder: section.sortOrder,
    visibility: section.visibility,
    postingPolicy: section.postingPolicy,
    moderationPolicy: section.moderationPolicy,
    enabled: section.enabled,
  })
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function reset() {
  editingId.value = null
  Object.assign(form, {
    parentId: 0,
    sectionKey: '',
    name: '',
    description: '',
    icon: '💬',
    sortOrder: 0,
    visibility: 'PUBLIC',
    postingPolicy: 'MEMBERS',
    moderationPolicy: 'NONE',
    enabled: true,
  })
}

async function save() {
  saving.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const url = editingId.value
      ? `/api/admin/forum/sections/${editingId.value}`
      : '/api/admin/forum/sections'
    await adminRequest<ForumSection>(url, {
      method: editingId.value ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        ...form,
        parentId: form.parentId > 0 ? form.parentId : null,
      }),
    })
    successMessage.value = editingId.value ? '板块已更新。' : '板块已创建。'
    reset()
    await load()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '板块保存失败。'
  } finally {
    saving.value = false
  }
}

async function toggle(section: ForumSection) {
  errorMessage.value = ''
  try {
    await adminRequest<ForumSection>(
      `/api/admin/forum/sections/${section.id}/enabled/${!section.enabled}`,
      { method: 'POST' },
    )
    await load()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '板块状态修改失败。'
  }
}

onMounted(() => void load())
</script>

<template>
  <section class="admin-workspace-page">
    <header class="admin-workspace-hero">
      <div>
        <span class="admin-workspace-eyebrow">Forum / Sections</span>
        <h2>论坛板块管理</h2>
        <p>维护两级板块、公开范围、发帖策略和审核策略；停用不会删除历史帖子。</p>
      </div>
      <a class="admin-workspace-action" href="/forum" target="_blank">打开前台论坛</a>
    </header>

    <form class="admin-workspace-form" @submit.prevent="save">
      <h3>{{ editingId ? `编辑板块 #${editingId}` : '新建论坛板块' }}</h3>
      <div class="admin-workspace-fields">
        <label class="admin-workspace-field">
          板块标识
          <input v-model.trim="form.sectionKey" required placeholder="general-talk">
        </label>
        <label class="admin-workspace-field">
          板块名称
          <input v-model.trim="form.name" required placeholder="综合交流">
        </label>
        <label class="admin-workspace-field">
          父板块
          <select v-model.number="form.parentId">
            <option :value="0">顶级板块</option>
            <option
              v-for="section in sections.filter((item) => !item.parentId && item.id !== editingId)"
              :key="section.id"
              :value="section.id"
            >
              {{ section.name }}
            </option>
          </select>
        </label>
        <label class="admin-workspace-field">
          图标 / Emoji
          <input v-model.trim="form.icon" placeholder="💬">
        </label>
        <label class="admin-workspace-field">
          可见范围
          <select v-model="form.visibility">
            <option value="PUBLIC">公开</option>
            <option value="MEMBERS">仅会员</option>
            <option value="PRIVATE">私有</option>
          </select>
        </label>
        <label class="admin-workspace-field">
          发帖策略
          <select v-model="form.postingPolicy">
            <option value="MEMBERS">会员可发帖</option>
            <option value="SELECTED_GROUPS">指定用户组</option>
            <option value="CLOSED">关闭发帖</option>
          </select>
        </label>
        <label class="admin-workspace-field">
          审核策略
          <select v-model="form.moderationPolicy">
            <option value="NONE">无需审核</option>
            <option value="FIRST_POST">首次发帖审核</option>
            <option value="ALL_POSTS">全部审核</option>
          </select>
        </label>
        <label class="admin-workspace-field">
          排序值
          <input v-model.number="form.sortOrder" min="0" type="number">
        </label>
        <label class="admin-workspace-field is-wide">
          板块说明
          <textarea v-model.trim="form.description" placeholder="说明这个板块讨论什么内容"></textarea>
        </label>
      </div>
      <div class="admin-workspace-form-actions">
        <button class="admin-workspace-action" :disabled="saving" type="submit">
          {{ saving ? '保存中…' : editingId ? '保存修改' : '创建板块' }}
        </button>
        <button
          v-if="editingId"
          class="admin-workspace-action is-secondary"
          type="button"
          @click="reset"
        >
          取消编辑
        </button>
      </div>
    </form>

    <div v-if="errorMessage" class="admin-workspace-state is-error">{{ errorMessage }}</div>
    <div v-if="successMessage" class="admin-workspace-state">{{ successMessage }}</div>

    <section class="admin-workspace-card">
      <div v-if="loading" class="admin-workspace-empty">正在读取板块…</div>
      <div v-else-if="!sections.length" class="admin-workspace-empty">
        <strong>还没有论坛板块</strong>
        <p>使用上方表单创建第一个公开板块，前台论坛会立即显示。</p>
      </div>
      <div v-else class="admin-workspace-table-wrap">
        <table class="admin-workspace-table">
          <thead>
            <tr>
              <th>板块</th><th>标识</th><th>层级</th><th>策略</th>
              <th>主题 / 帖子</th><th>状态</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="section in sections" :key="section.id">
              <td>{{ section.icon }} {{ section.name }}</td>
              <td>{{ section.sectionKey }}</td>
              <td>{{ section.parentId ? `子板块 #${section.parentId}` : '顶级板块' }}</td>
              <td>{{ section.visibility }} / {{ section.moderationPolicy }}</td>
              <td>{{ section.threadCount }} / {{ section.postCount }}</td>
              <td>{{ section.enabled ? '已启用' : '已停用' }}</td>
              <td>
                <button class="admin-workspace-action is-secondary" type="button" @click="edit(section)">
                  编辑
                </button>
                <button class="admin-workspace-action is-secondary" type="button" @click="toggle(section)">
                  {{ section.enabled ? '停用' : '启用' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </section>
</template>
