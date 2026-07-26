<!-- 系统设置子菜单的真实运行状态页；当前不伪造尚未落库的可写表单。 -->
<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { adminRequest } from '../../api/admin-workspace'
import '../workspace/admin-workspace.css'

interface SystemFact {
  label: string
  value: string
}

interface SystemStatus {
  section: string
  editable: boolean
  title: string
  description: string
  facts: SystemFact[]
}

const props = defineProps<{
  section: 'basic' | 'mail' | 'storage' | 'security' | 'logs'
}>()

const status = ref<SystemStatus | null>(null)
const loading = ref(false)
const errorMessage = ref('')

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    status.value = await adminRequest<SystemStatus>(
      `/api/admin/system/status/${encodeURIComponent(props.section)}`,
    )
  } catch (error) {
    status.value = null
    errorMessage.value = error instanceof Error ? error.message : '系统设置状态读取失败。'
  } finally {
    loading.value = false
  }
}

watch(() => props.section, () => void load())
onMounted(() => void load())
</script>

<template>
  <section class="admin-workspace-page">
    <header class="admin-workspace-hero">
      <div>
        <span class="admin-workspace-eyebrow">System / {{ section }}</span>
        <h2>{{ status?.title || '系统设置' }}</h2>
        <p>{{ status?.description || '正在读取当前实例运行配置。' }}</p>
      </div>
      <div class="admin-workspace-hero-actions">
        <button type="button" :disabled="loading" @click="load">
          <Refresh aria-hidden="true" />
          {{ loading ? '读取中…' : '重新读取' }}
        </button>
      </div>
    </header>

    <div v-if="errorMessage" class="admin-workspace-state is-error">{{ errorMessage }}</div>

    <template v-if="status">
      <section class="admin-workspace-card">
        <div class="admin-workspace-fact-grid">
          <article v-for="fact in status.facts" :key="fact.label">
            <span>{{ fact.label }}</span>
            <strong>{{ fact.value }}</strong>
          </article>
        </div>
      </section>

      <section v-if="!status.editable" class="admin-workspace-state is-warning">
        <strong>当前为只读运行状态</strong>
        <p>
          页面已经连接真实配置和实例目录；在设置表、权限节点与操作审计完成前，
          不提供会丢失配置或绕过部署平台环境变量的假保存按钮。
        </p>
      </section>
    </template>
  </section>
</template>
