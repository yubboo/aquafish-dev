<!--
  Aquafish 运行状态故障页。

  该页面专门承接“数据库安装状态无法确认”的安全失败场景。它与首次安装页面完全分离，
  防止已经安装的开发或生产环境因为数据库服务短暂中断而被误导为需要重新安装。
-->
<template>
  <main class="system-unavailable-page">
    <section class="system-unavailable-card" aria-live="polite">
      <span class="system-unavailable-icon" aria-hidden="true"><Warning /></span>

      <p class="system-unavailable-kicker">AQUAFISH RUNTIME CHECK</p>
      <h1>系统暂时无法进入</h1>
      <p class="system-unavailable-summary">
        当前无法读取数据库中的安装状态。Aquafish 不会把这个故障当成“尚未安装”，
        也不会开放首次安装器。
      </p>

      <div class="system-unavailable-detail">
        <strong>请先检查运行环境</strong>
        <ul>
          <li>确认 MySQL、MariaDB 或 PostgreSQL 服务已经启动。</li>
          <li>确认当前电脑仍在使用原来的数据库地址、端口和账号。</li>
          <li>数据库恢复后点击“重新检测”，系统会继续进入原后台地址。</li>
        </ul>
      </div>

      <p v-if="message" class="system-unavailable-message">{{ message }}</p>

      <button
        type="button"
        class="system-unavailable-retry"
        :disabled="checking"
        @click="retry"
      >
        {{ checking ? '正在检测...' : '重新检测' }}
      </button>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Warning } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchSetupStatus } from '../../api/setup-status'
import './system-unavailable-page.css'

const route = useRoute()
const router = useRouter()
const checking = ref(false)
const message = ref('数据库安装状态暂时不可用，请确认数据库服务已经启动。')

/**
 * 只接受站内后台地址作为恢复目标，避免查询参数形成开放重定向。
 */
function recoveryTarget(): string {
  const redirect = route.query.redirect

  if (
    typeof redirect === 'string'
    && (
      redirect === '/admin'
      || redirect.startsWith('/admin/')
    )
  ) {
    return redirect
  }

  return '/admin'
}

/**
 * 重新读取后端权威状态：
 * 1. 数据库确认已安装时返回原后台地址；
 * 2. 纯净环境明确允许安装时才进入 /setup；
 * 3. 状态仍不可用时留在本页，不执行任何安装写操作。
 */
async function retry(): Promise<void> {
  checking.value = true
  message.value = ''

  try {
    const status = await fetchSetupStatus()

    if (status.installed) {
      await router.replace(recoveryTarget())
      return
    }

    if (status.stateAvailable && status.canInstall) {
      await router.replace('/setup')
      return
    }

    message.value = status.note || '数据库安装状态仍然不可用，请检查数据库服务和连接配置。'
  } catch (error) {
    message.value = error instanceof Error && error.message
      ? error.message
      : '后端状态接口暂时不可访问，请确认 Aquafish 后端已经启动。'
  } finally {
    checking.value = false
  }
}

/*
 * 页面打开后立即尝试一次恢复。数据库已经重新启动时，用户无需再手动点击按钮。
 */
onMounted(retry)
</script>
