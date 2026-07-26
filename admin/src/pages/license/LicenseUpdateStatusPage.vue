<!--
  更新服务授权状态页。
  本页只读取已经过后端验签的脱敏授权状态，不伪造在线升级检查或下载能力。
-->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh, UploadFilled } from '@element-plus/icons-vue'
import { isLicenseFeatureGranted } from '../../config/license-features'
import {
  currentLicenseStatus,
  loadLicenseStatus,
} from '../../stores/license-status'
import '../workspace/admin-workspace.css'

const loading = ref(false)
const errorMessage = ref('')

const updateGranted = computed(() => {
  return isLicenseFeatureGranted(currentLicenseStatus.value, 'updates')
})

/** 强制重新读取后端授权状态，避免用前端缓存判断更新服务权益。 */
async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    await loadLicenseStatus(true)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '更新服务授权状态读取失败。'
  } finally {
    loading.value = false
  }
}

onMounted(() => void load())
</script>

<template>
  <section class="admin-workspace-page">
    <header class="admin-workspace-hero">
      <div>
        <span class="admin-workspace-eyebrow">License / Updates</span>
        <h2>更新服务</h2>
        <p>核对当前授权是否包含版本更新权益；升级检查与制品下载仍由后续发布服务负责。</p>
      </div>
      <div class="admin-workspace-hero-actions">
        <button type="button" :disabled="loading" @click="load">
          <Refresh aria-hidden="true" />
          {{ loading ? '校验中…' : '重新校验' }}
        </button>
      </div>
    </header>

    <div v-if="errorMessage" class="admin-workspace-state is-error">{{ errorMessage }}</div>

    <section v-if="currentLicenseStatus" class="admin-workspace-card">
      <div class="admin-workspace-summary">
        <div>
          <span>授权版本</span>
          <strong>{{ currentLicenseStatus.edition || '未授权' }}</strong>
        </div>
        <div>
          <span>授权状态</span>
          <strong>{{ currentLicenseStatus.valid ? '有效' : '不可用' }}</strong>
        </div>
        <div>
          <span>更新权益</span>
          <strong>{{ updateGranted ? '已授权' : '未包含' }}</strong>
        </div>
      </div>
    </section>

    <section class="admin-workspace-state" :class="updateGranted ? 'is-success' : 'is-warning'">
      <strong><UploadFilled aria-hidden="true" /> 真实能力边界</strong>
      <p v-if="updateGranted">
        当前许可证包含 updates 权益。现阶段只完成权益校验，不提供尚未接入发布中心的假检查或假下载按钮。
      </p>
      <p v-else>
        当前许可证未包含 updates 权益，或授权尚未生效；请先回到“授权状态”完成校验。
      </p>
    </section>
  </section>
</template>
