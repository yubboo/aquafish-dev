<!--
  已安装系统访问 /setup 时显示的只读提示页。
  本页面不读取安装上下文、不调用任何安装写接口，只读取完成页保存在本机的安全入口偏好，
  提示安装已锁定后自动跳转；权威安装状态仍由后端和 setup-status-guard.ts 决定。
-->
<template>
  <main class="setup-page setup-page--installed">
    <section class="setup-card setup-installed-card" aria-live="polite">
      <header class="setup-installed-brand">
        <div>
          <span class="setup-brand">Aquafish</span>
          <span class="setup-mode-badge">系统安装向导</span>
        </div>
        <span class="setup-installed-badge">
          <Lock aria-hidden="true" />
          安装入口已锁定
        </span>
      </header>

      <div class="setup-installed-hero">
        <CircleCheckFilled class="setup-installed-icon" aria-hidden="true" />
        <p class="setup-eyebrow">安全初始化已完成</p>
        <h1>系统已经安装</h1>
        <p>无需重复配置，Aquafish 将带你返回首次安装完成时选择的页面。</p>
      </div>

      <div class="setup-installed-status" aria-label="系统安装状态">
        <div class="setup-installed-status__item">
          <CircleCheckFilled aria-hidden="true" />
          <span><small>安装状态</small><strong>初始化完成</strong></span>
        </div>
        <div class="setup-installed-status__item">
          <Lock aria-hidden="true" />
          <span><small>安全保护</small><strong>安装接口锁定</strong></span>
        </div>
        <div class="setup-installed-status__item is-destination">
          <span><small>即将前往</small><strong>{{ destinationLabel }}</strong></span>
          <ArrowRight aria-hidden="true" />
        </div>
      </div>

      <footer class="setup-installed-footer">
        <div class="setup-installed-progress" aria-hidden="true"><span></span></div>
        <div class="setup-installed-actions">
          <p>页面将在约 2 秒后自动跳转</p>
          <button type="button" class="setup-primary setup-installed-button" @click="continueNow">
            <span>立即前往</span>
            <ArrowRight aria-hidden="true" />
          </button>
        </div>
      </footer>
    </section>
  </main>
</template>

<script setup lang="ts">
/**
 * 已安装系统的专用提示页。
 *
 * 该页面不读取安装上下文、不提供安装表单，也不调用任何安装写接口；它只提示
 * “系统已经安装”，随后前往用户在首次完成页保存的入口。
 */
import { ArrowRight, CircleCheckFilled, Lock } from '@element-plus/icons-vue'
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  isExternalSetupDestination,
  readSetupDestination,
} from '../../router/setup-completion-destination'
import './setup-page.css'
import './setup-page-v2.css'

const router = useRouter()
let redirectTimer: number | null = null
const REDIRECT_DELAY_MS = 2200

/*
 * 只读取安装完成页主动保存的入口，不接受地址栏 redirect 参数。
 * 这样外部链接无法把“系统已安装”提示页变成开放重定向入口。
 */
const destination = readSetupDestination()

/** 把安全跳转目标转换为用户可读名称，不在页面暴露完整外部 URL。 */
const destinationLabel = computed(() => {
  if (destination === '/admin/themes') return '主题与插件配置'
  if (isExternalSetupDestination(destination)) return '站点首页'
  return '后台登录'
})

/** 清理自动跳转计时器，并按内部路由或外部站点使用对应的 replace 导航。 */
function continueNow(): void {
  if (redirectTimer !== null) {
    window.clearTimeout(redirectTimer)
    redirectTimer = null
  }
  if (isExternalSetupDestination(destination)) {
    window.location.replace(destination)
    return
  }
  void router.replace(destination)
}

onMounted(() => {
  // 给用户保留足够时间看清“已安装”状态，再自动前往已记住的入口。
  redirectTimer = window.setTimeout(continueNow, REDIRECT_DELAY_MS)
})

onBeforeUnmount(() => {
  if (redirectTimer !== null) window.clearTimeout(redirectTimer)
})
</script>

<style scoped>
/*
 * 已安装提示页使用独立样式，避免改动安装向导的表单、步骤条和完成页。
 * 视觉继续沿用 Aquafish 的粉紫蓝柔光，只收紧信息层级和留白比例。
 */
.setup-page--installed {
  padding: 32px 20px;
}

.setup-installed-card {
  width: min(800px, 100%);
  padding: 0;
  overflow: hidden;
}

.setup-installed-brand {
  min-height: 72px;
  padding: 0 30px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  border-bottom: 1px solid rgba(142, 132, 205, 0.14);
  background: rgba(255, 255, 255, 0.5);
}

.setup-installed-brand > div {
  display: flex;
  align-items: center;
  gap: 11px;
}

.setup-installed-badge {
  min-height: 34px;
  padding: 0 13px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: 1px solid rgba(46, 169, 124, 0.22);
  border-radius: 999px;
  color: #237c60;
  background: rgba(227, 248, 239, 0.82);
  font-size: 12px;
  font-weight: 800;
}

.setup-installed-badge svg {
  width: 15px;
  height: 15px;
}

.setup-installed-hero {
  padding: 46px 32px 30px;
  display: grid;
  justify-items: center;
  text-align: center;
}

.setup-installed-icon {
  width: 74px;
  height: 74px;
  margin-bottom: 18px;
  color: var(--setup-success);
  filter: drop-shadow(0 14px 22px rgba(46, 169, 124, 0.2));
}

.setup-installed-hero .setup-eyebrow {
  color: #6f65d2;
  letter-spacing: 0.09em;
}

.setup-installed-hero h1 {
  margin: 10px 0 12px;
  color: #29233f;
  font-size: clamp(34px, 5vw, 44px);
  line-height: 1.16;
  letter-spacing: -0.045em;
}

.setup-installed-hero > p:last-child {
  max-width: 570px;
  margin: 0;
  color: var(--setup-muted);
  font-size: 15px;
  line-height: 1.8;
}

.setup-installed-status {
  margin: 0 34px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  overflow: hidden;
  border: 1px solid rgba(142, 132, 205, 0.17);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.66);
}

.setup-installed-status__item {
  min-width: 0;
  min-height: 82px;
  padding: 16px 18px;
  display: flex;
  align-items: center;
  gap: 11px;
  box-sizing: border-box;
}

.setup-installed-status__item + .setup-installed-status__item {
  border-left: 1px solid rgba(142, 132, 205, 0.15);
}

.setup-installed-status__item > svg {
  flex: 0 0 auto;
  width: 22px;
  height: 22px;
  color: var(--setup-success);
}

.setup-installed-status__item span {
  min-width: 0;
}

.setup-installed-status__item small,
.setup-installed-status__item strong {
  display: block;
}

.setup-installed-status__item small {
  margin-bottom: 5px;
  color: #8b85a2;
  font-size: 11px;
}

.setup-installed-status__item strong {
  overflow: hidden;
  color: #3a3451;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.setup-installed-status__item.is-destination {
  justify-content: space-between;
  background: linear-gradient(135deg, rgba(238, 234, 255, 0.82), rgba(255, 235, 248, 0.74));
}

.setup-installed-status__item.is-destination > svg {
  color: #7867e7;
}

.setup-installed-status__item.is-destination strong {
  color: #5c4fd0;
  font-size: 15px;
}

.setup-installed-footer {
  padding: 26px 34px 32px;
}

.setup-installed-progress {
  height: 4px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(113, 101, 238, 0.1);
}

.setup-installed-progress span {
  width: 0;
  height: 100%;
  display: block;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--setup-primary), var(--setup-pink));
  animation: setup-installed-progress 2200ms linear forwards;
}

.setup-installed-actions {
  margin-top: 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.setup-installed-actions p {
  margin: 0;
  color: #8b85a2;
  font-size: 12px;
}

.setup-installed-button {
  min-width: 176px;
}

@keyframes setup-installed-progress {
  to { width: 100%; }
}

@media (max-width: 700px) {
  .setup-page--installed {
    padding: 18px 12px;
    align-items: flex-start;
  }

  .setup-installed-brand {
    min-height: 0;
    padding: 18px 20px;
    align-items: flex-start;
    flex-direction: column;
  }

  .setup-installed-hero {
    padding: 34px 20px 24px;
  }

  .setup-installed-icon {
    width: 64px;
    height: 64px;
  }

  .setup-installed-status {
    margin: 0 20px;
    grid-template-columns: 1fr;
  }

  .setup-installed-status__item {
    min-height: 68px;
  }

  .setup-installed-status__item + .setup-installed-status__item {
    border-top: 1px solid rgba(142, 132, 205, 0.15);
    border-left: 0;
  }

  .setup-installed-footer {
    padding: 24px 20px;
  }

  .setup-installed-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .setup-installed-button {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .setup-installed-progress span {
    width: 100%;
    animation: none;
  }
}
</style>
