<!--
  系统平台授权管理页。
  通过 license.ts 获取设备码、验签状态并提交激活/取消激活；页面只展示脱敏授权信息，
  真正的 Ed25519 验签、授权文件保存和业务 API 拦截都在后端 license 模块完成。
-->
<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  activateLicense,
  activateOnlineLicense,
  deactivateLicense,
  fetchLicenseStatus,
  refreshOnlineLicenseStatus,
  type LicenseStatus,
  type LicenseStatusCode,
} from '../../api/license'
import {
  clearUsableLicenseConfirmation,
  confirmUsableLicense,
} from '../../router/license-status-guard'
import './license-management-page.css'

const route = useRoute()
const router = useRouter()

const status = ref<LicenseStatus | null>(null)
const licenseCode = ref('')
const onlineActivationCode = ref('')
const loading = ref(false)
const activating = ref(false)
const onlineActivating = ref(false)
const deactivating = ref(false)
const copied = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

/** 后端授权状态码到中文标签和视觉色调的唯一映射。 */
const statusMeta: Record<LicenseStatusCode, { label: string; tone: string }> = {
  NOT_ACTIVATED: { label: '等待激活', tone: 'warning' },
  VALID: { label: '授权有效', tone: 'success' },
  EXPIRED: { label: '授权已过期', tone: 'danger' },
  NOT_YET_VALID: { label: '尚未生效', tone: 'warning' },
  INSTANCE_MISMATCH: { label: '设备不匹配', tone: 'danger' },
  PRODUCT_MISMATCH: { label: '产品不匹配', tone: 'danger' },
  SUSPENDED: { label: '授权已暂停', tone: 'warning' },
  REVOKED: { label: '授权已吊销', tone: 'danger' },
  DEVICE_UNBOUND: { label: '设备已解绑', tone: 'danger' },
  ONLINE_CHECK_REQUIRED: { label: '需要联网校验', tone: 'warning' },
  INVALID: { label: '授权无效', tone: 'danger' },
  CONFIGURATION_ERROR: { label: '配置异常', tone: 'danger' },
}

/** 当前授权状态的展示元数据；接口尚未返回时使用中性色。 */
const currentMeta = computed(() => {
  return status.value
    ? statusMeta[status.value.status]
    : { label: '读取中', tone: 'neutral' }
})

/**
 * 把在线中心的机器状态转换为面向管理员的中文摘要。
 * 日期来自后端成功缓存，不展示在线中心地址、令牌或完整授权码。
 */
const onlineMeta = computed(() => {
  const online = status.value?.online
  if (!online?.enabled) {
    return { label: '未启用', detail: '当前使用本地 Ed25519 离线验签' }
  }
  const labels: Record<string, string> = {
    PENDING: '等待首次校验',
    ACTIVE: '在线状态有效',
    SUSPENDED: '已被临时暂停',
    REVOKED: '已远程吊销',
    UNBOUND: '设备已解绑',
    UNKNOWN: '在线记录不存在',
    INSTANCE_MISMATCH: '设备不匹配',
    EXPIRED: '在线记录已过期',
    ONLINE_CHECK_REQUIRED: '需要恢复联网',
    CONFIGURATION_ERROR: '在线配置异常',
  }
  const details = [online.message]
  if (online.lastCheckedAt) {
    details.push(`最近成功校验 ${formatDate(online.lastCheckedAt)}`)
  }
  if (online.graceExpiresAt && !['SUSPENDED', 'REVOKED', 'UNBOUND'].includes(online.state)) {
    details.push(`离线宽限至 ${formatDate(online.graceExpiresAt)}`)
  }
  return {
    label: labels[online.state] || online.state,
    detail: details.filter(Boolean).join(' · '),
  }
})

/** 激活成功后的安全返回地址，只允许后台内部路径且不能再次指向授权页。 */
const targetAfterActivation = computed(() => {
  const target = typeof route.query.redirect === 'string' ? route.query.redirect : '/admin'
  return target.startsWith('/admin') && target !== '/admin/license' ? target : '/admin'
})

/** 格式化授权时间；expiresAt 为空按产品规则表示永久有效。 */
function formatDate(value: string | null): string {
  if (!value) {
    return '永久有效'
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    }).format(date)
}

/** 重新请求后端实时验签状态，并同步路由守卫缓存；吊销状态也必须覆盖旧的可用快照。 */
async function loadStatus(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    status.value = await fetchLicenseStatus()
    confirmUsableLicense(status.value)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '授权状态读取失败'
  } finally {
    loading.value = false
  }
}

/** 点击按钮时等待一次在线中心查询；在线功能关闭时等同于普通本地复核。 */
async function recheckStatus(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    status.value = await refreshOnlineLicenseStatus()
    confirmUsableLicense(status.value)
    successMessage.value = '本地签名与在线授权状态已经重新校验。'
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '在线授权状态复核失败'
  } finally {
    loading.value = false
  }
}

/** 复制稳定设备码；剪贴板权限不可用时保留手动复制提示。 */
async function copyInstanceId(): Promise<void> {
  const value = status.value?.instanceId
  if (!value) {
    return
  }
  try {
    await navigator.clipboard.writeText(value)
    copied.value = true
    window.setTimeout(() => { copied.value = false }, 1800)
  } catch {
    errorMessage.value = '浏览器无法自动复制，请手动选择设备码。'
  }
}

/**
 * 打开后端白名单配置的授权中心。
 *
 * 设备码不自动拼入 URL，防止进入浏览器历史、反向代理访问日志或第三方统计；
 * 用户先点击“复制设备码”，再到已登录的个人中心主动绑定。
 */
function openLicensePortal(): void {
  const value = status.value?.portalUrl
  if (!value) {
    errorMessage.value = '发行方尚未配置客户授权中心地址。'
    return
  }
  try {
    const target = new URL(value)
    const loopbackHttp = target.protocol === 'http:'
      && ['127.0.0.1', 'localhost'].includes(target.hostname)
    if (target.protocol !== 'https:' && !loopbackHttp) {
      throw new Error('untrusted portal protocol')
    }
    window.open(target.toString(), '_blank', 'noopener,noreferrer')
  } catch {
    errorMessage.value = '授权中心地址无效，请联系 Aquafish 发行方。'
  }
}

/** 提交完整 AQF1 授权码，后端验签成功后清空输入框并允许进入业务页面。 */
async function submitActivation(): Promise<void> {
  const code = licenseCode.value.trim()
  if (!code) {
    errorMessage.value = '请粘贴完整授权码。'
    return
  }

  activating.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    status.value = await activateLicense(code)
    licenseCode.value = ''
    successMessage.value = '授权码校验通过，Aquafish 系统平台已经激活。'
    confirmUsableLicense(status.value)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '授权码激活失败'
  } finally {
    activating.value = false
  }
}

/**
 * 把 AQO1 短码交给后端在线激活；后端会附加真实设备码并对中心返回的 AQF1 再次验签。
 */
async function submitOnlineActivation(): Promise<void> {
  const code = onlineActivationCode.value.trim()
  if (!code) {
    errorMessage.value = '请输入授权中心提供的 AQO1 在线激活码。'
    return
  }
  onlineActivating.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    status.value = await activateOnlineLicense(code)
    onlineActivationCode.value = ''
    successMessage.value = '授权中心已确认授权，签名凭证已经安全保存。'
    confirmUsableLicense(status.value)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '在线激活失败'
  } finally {
    onlineActivating.value = false
  }
}

/** 经用户二次确认后取消本机激活，并立即清除前端授权放行缓存。 */
async function submitDeactivation(): Promise<void> {
  if (!window.confirm('确定取消本机激活吗？取消后业务接口会立即停止使用。')) {
    return
  }

  deactivating.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    status.value = await deactivateLicense()
    clearUsableLicenseConfirmation(status.value)
    successMessage.value = '本机授权已经取消激活。'
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '取消激活失败'
  } finally {
    deactivating.value = false
  }
}

/** 使用 replace 进入激活前目标，避免返回键重新落到一次性激活状态。 */
function enterSystem(): void {
  void router.replace(targetAfterActivation.value)
}

/**
 * 授权子菜单复用同一个真实管理页，并把视口定位到对应区域。
 *
 * 设备绑定和在线校验不是两套重复业务逻辑，独立 URL 只用于稳定导航、刷新和权限审计。
 */
async function focusLicenseSection(): Promise<void> {
  const targetId = route.path.endsWith('/bind')
    ? 'license-device'
    : route.path.endsWith('/online')
      ? 'license-online'
      : ''
  if (!targetId) {
    return
  }
  await nextTick()
  document.getElementById(targetId)?.scrollIntoView({
    behavior: 'smooth',
    block: 'start',
  })
}

onMounted(() => {
  void loadStatus()
  void focusLicenseSection()
})

watch(() => route.path, () => {
  void focusLicenseSection()
})
</script>

<template>
  <section class="license-page">
    <div class="license-hero">
      <div>
        <span class="license-kicker">Aquafish License Center</span>
        <h1>系统平台授权</h1>
        <p>绑定当前实例、验证授权有效期，并统一控制 Aquafish 平台功能访问。</p>
      </div>
      <div class="license-hero-actions">
        <span class="license-status-pill" :class="`is-${currentMeta.tone}`">
          <i></i>{{ currentMeta.label }}
        </span>
        <button type="button" class="license-ghost-button" :disabled="loading" @click="recheckStatus">
          {{ loading ? '校验中…' : '重新校验' }}
        </button>
      </div>
    </div>

    <div v-if="status && !status.enforcementEnabled" class="license-dev-banner">
      <strong>开发环境旁路已开启</strong>
      <span>当前仍会真实验签和显示状态，但不会拦截业务 API；正式环境默认强制授权。</span>
    </div>

    <div v-if="errorMessage || successMessage" class="license-message" :class="{ 'is-error': errorMessage }">
      <strong>{{ errorMessage ? '操作未完成' : '操作成功' }}</strong>
      <span>{{ errorMessage || successMessage }}</span>
    </div>

    <div class="license-overview-grid">
      <article class="license-overview-card license-primary-card">
        <span>当前授权状态</span>
        <strong>{{ currentMeta.label }}</strong>
        <p>{{ status?.message || '正在连接授权校验服务…' }}</p>
      </article>
      <article class="license-overview-card">
        <span>授权版本</span>
        <strong>{{ status?.edition || '未授权' }}</strong>
        <p>{{ status?.customer || '尚未绑定授权主体' }}</p>
      </article>
      <article class="license-overview-card">
        <span>有效期</span>
        <strong>{{ status?.valid ? formatDate(status.expiresAt) : '—' }}</strong>
        <p>{{ status?.licenseId ? `授权编号 ${status.licenseId}` : '激活后显示授权编号' }}</p>
      </article>
      <article class="license-overview-card">
        <span>在线校验</span>
        <strong>{{ onlineMeta.label }}</strong>
        <p>{{ onlineMeta.detail }}</p>
      </article>
    </div>

    <div class="license-content-grid">
      <section id="license-device" class="license-panel">
        <div class="license-panel-heading">
          <div class="license-heading-icon">01</div>
          <div>
            <h2>获取设备码</h2>
            <p>将设备码提交给 Aquafish 授权方，用于签发仅适配当前实例的授权码。</p>
          </div>
        </div>

        <div class="license-device-box">
          <div>
            <span>INSTANCE ID</span>
            <code>{{ status?.instanceId || '正在生成设备码…' }}</code>
          </div>
          <div class="license-device-actions">
            <button type="button" :disabled="!status?.instanceId" @click="copyInstanceId">
              {{ copied ? '已复制' : '复制设备码' }}
            </button>
            <button
              v-if="status?.portalUrl"
              type="button"
              class="license-portal-button"
              @click="openLicensePortal"
            >
              前往授权中心 ↗
            </button>
          </div>
        </div>

        <div class="license-explain-list">
          <div><b>1</b><span>设备码首次生成后保存在 workdir/instance.id，重启不会改变。</span></div>
          <div><b>2</b><span>购买后可在授权中心个人页面粘贴设备码，获取在线短码或下载离线授权。</span></div>
          <div><b>3</b><span>授权内容经过 Ed25519 数字签名，修改任意字段都会立即失效。</span></div>
        </div>
      </section>

      <section id="license-online" class="license-panel">
        <div class="license-panel-heading">
          <div class="license-heading-icon">02</div>
          <div>
            <h2>激活 Aquafish</h2>
            <p>优先使用授权中心短码在线激活；无法联网时再导入设备绑定的 AQF1。</p>
          </div>
        </div>

        <div class="license-online-box">
          <span class="license-method-label">推荐 · 在线授权中心</span>
          <label class="license-short-code-field">
            <span>AQO1 在线激活码</span>
            <input
              v-model="onlineActivationCode"
              autocomplete="off"
              spellcheck="false"
              placeholder="AQO1.xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
            />
          </label>
          <button
            type="button"
            class="license-activate-button"
            :disabled="onlineActivating || !onlineActivationCode.trim()"
            @click="submitOnlineActivation"
          >
            {{ onlineActivating ? '正在连接授权中心…' : '在线验证并激活' }}
          </button>
        </div>

        <div class="license-method-divider"><span>或使用离线设备码授权</span></div>

        <label class="license-code-field">
          <span>完整 AQF1 离线授权码</span>
          <textarea
            v-model="licenseCode"
            rows="7"
            autocomplete="off"
            spellcheck="false"
            placeholder="AQF1.eyJzY2hlbWFWZXJzaW9uIjoxLC4uLg.signature"
          ></textarea>
        </label>

        <button
          type="button"
          class="license-activate-button"
          :disabled="activating || !licenseCode.trim()"
          @click="submitActivation"
        >
          {{ activating ? '正在验签并激活…' : '验证并激活授权' }}
        </button>
      </section>
    </div>

    <section v-if="status?.valid" class="license-active-panel">
      <div class="license-active-mark">✓</div>
      <div class="license-active-copy">
        <span>LICENSE VERIFIED</span>
        <h2>当前实例已获得有效授权</h2>
        <p>签发于 {{ formatDate(status.issuedAt) }}，授权主体为 {{ status.customer || '未命名客户' }}。</p>
        <div class="license-feature-list">
          <span v-for="feature in status.features" :key="feature">{{ feature }}</span>
          <span v-if="!status.features.length">platform</span>
        </div>
      </div>
      <div class="license-active-actions">
        <button type="button" class="license-enter-button" @click="enterSystem">进入系统</button>
        <button type="button" class="license-danger-button" :disabled="deactivating" @click="submitDeactivation">
          {{ deactivating ? '处理中…' : '取消本机激活' }}
        </button>
      </div>
    </section>
  </section>
</template>
