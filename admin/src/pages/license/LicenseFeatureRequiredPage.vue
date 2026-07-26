<!--
  模块授权不足说明页。
  路由守卫或全局 fetch 包装器发现 LICENSE_FEATURE_REQUIRED 时进入本页；页面展示缺少的
  模块、当前版本和已授权模块，并提供重新校验与返回授权管理入口。后端 403 才是安全边界。
-->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock } from '@element-plus/icons-vue'
import {
  isLicenseFeature,
  isLicenseFeatureGranted,
  LICENSE_FEATURE_LABELS,
  type LicenseFeature,
} from '../../config/license-features'
import {
  currentLicenseStatus,
  loadLicenseStatus,
} from '../../stores/license-status'
import './license-feature-required-page.css'

const route = useRoute()
const router = useRouter()
const checking = ref(false)
const feedback = ref('')

/** 只接受已登记模块代码，防止手工修改 query 后显示任意文本。 */
const requiredFeature = computed<LicenseFeature>(() => {
  const value = route.query.feature
  return isLicenseFeature(value) ? value : 'content'
})

const featureLabel = computed(() => LICENSE_FEATURE_LABELS[requiredFeature.value])
const status = computed(() => currentLicenseStatus.value)

/** 返回地址只允许后台内部路径，避免 query 被利用为站外开放重定向。 */
const safeRedirect = computed(() => {
  const value = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  return value.startsWith('/admin')
    && value !== '/admin/license/feature-required'
    ? value
    : '/admin'
})

/** 强制重新读取后端状态；升级授权后可直接返回原目标。 */
async function recheckLicense(): Promise<void> {
  checking.value = true
  feedback.value = ''
  try {
    const latest = await loadLicenseStatus(true)
    if (isLicenseFeatureGranted(latest, requiredFeature.value)) {
      await router.replace(safeRedirect.value)
      return
    }
    feedback.value = `当前授权仍未包含“${featureLabel.value}”。`
  } catch (error) {
    feedback.value = error instanceof Error ? error.message : '授权状态复核失败。'
  } finally {
    checking.value = false
  }
}

/** 进入系统授权页并保留升级后的返回目标。 */
function openLicensePage(): void {
  void router.push({
    path: '/admin/license',
    query: { redirect: safeRedirect.value },
  })
}
</script>

<template>
  <section class="license-feature-required-page">
    <div class="license-feature-required-card">
      <div class="license-feature-required-icon" aria-hidden="true"><Lock /></div>
      <span class="license-feature-required-kicker">Module entitlement</span>
      <h1>此功能需要{{ featureLabel }}授权</h1>
      <p class="license-feature-required-summary">
        系统平台授权有效，但当前版本没有包含该模块。菜单已经隐藏，直接访问页面或
        接口也会受到后端保护。
      </p>

      <div class="license-feature-required-facts">
        <article>
          <span>所需模块</span>
          <strong>{{ featureLabel }}</strong>
          <code>{{ requiredFeature }}</code>
        </article>
        <article>
          <span>当前版本</span>
          <strong>{{ status?.edition || '未读取' }}</strong>
          <code>{{ status?.licenseId || '—' }}</code>
        </article>
        <article>
          <span>当前状态</span>
          <strong>{{ status?.valid ? '平台授权有效' : '等待复核' }}</strong>
          <code>{{ status?.features?.length || 0 }} 个功能项</code>
        </article>
      </div>

      <div v-if="status?.features?.length" class="license-feature-required-tags">
        <span v-for="feature in status.features" :key="feature">{{ feature }}</span>
      </div>

      <p v-if="feedback" class="license-feature-required-feedback" role="status">
        {{ feedback }}
      </p>

      <div class="license-feature-required-actions">
        <button type="button" class="is-secondary" @click="router.push('/admin')">
          返回控制台
        </button>
        <button type="button" class="is-secondary" :disabled="checking" @click="recheckLicense">
          {{ checking ? '正在校验…' : '重新校验' }}
        </button>
        <button type="button" class="is-primary" @click="openLicensePage">
          前往授权管理
        </button>
      </div>
    </div>
  </section>
</template>
