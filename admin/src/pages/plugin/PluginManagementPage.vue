<!--
  ==========================================================================
  BEGIN：PF4J 插件运行管理页
  数据来自后端真实 PluginManager，不再把目录扫描结果当作“已启用插件”。
  ==========================================================================
-->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Connection,
  Refresh,
  VideoPause,
  VideoPlay,
  Warning,
} from '@element-plus/icons-vue'
import { adminRequest } from '../../api/admin-workspace'
import {
  aqPluginUiRuntimeState,
  syncAqAdminPluginUi,
} from '../../plugin-ui/loader'
import '../workspace/admin-workspace.css'

interface PluginDependency {
  pluginId: string
  versionRequirement: string
  optional: boolean
  present: boolean
  state: string
}

interface PluginRuntimeItem {
  pluginId: string
  name: string
  version: string
  provider: string
  description: string
  packageType: 'jar' | 'directory'
  state: string
  started: boolean
  classLoader: string
  dependencies: PluginDependency[]
  error: string
}

interface PluginRuntimeStatus {
  runtimeDirectoryReady: boolean
  loaderAvailable: boolean
  lifecycleAvailable: boolean
  candidateCount: number
  items: PluginRuntimeItem[]
  enabledPluginIds: string[]
  message: string
}

const status = ref<PluginRuntimeStatus | null>(null)
const router = useRouter()
const loading = ref(false)
const actingPluginId = ref('')
const errorMessage = ref('')
const successMessage = ref('')

const startedCount = computed(
  () => status.value?.items.filter((item) => item.started).length ?? 0,
)
const loadedUiCount = computed(
  () => aqPluginUiRuntimeState.loadedPluginIds.length,
)

function isEnabledRequested(pluginId: string) {
  return status.value?.enabledPluginIds.includes(pluginId) === true
}

function stateLabel(item: PluginRuntimeItem) {
  if (item.error) return '加载失败'
  if (item.started) return '运行中'
  if (isEnabledRequested(item.pluginId)) return '等待恢复'
  const labels: Record<string, string> = {
    CREATED: '已创建',
    DISABLED: '已停用',
    RESOLVED: '可启动',
    STOPPED: '已停止',
    FAILED: '失败',
  }
  return labels[item.state] || item.state
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    status.value = await adminRequest<PluginRuntimeStatus>('/api/admin/plugins/status')
  } catch (error) {
    status.value = null
    errorMessage.value = error instanceof Error ? error.message : '插件状态读取失败。'
  } finally {
    loading.value = false
  }
}

async function rescan() {
  loading.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    status.value = await adminRequest<PluginRuntimeStatus>('/api/admin/plugins/rescan', {
      method: 'POST',
    })
    successMessage.value = '插件目录、依赖图和启用状态已重新同步。'
    await synchronizePluginUi()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '插件重新扫描失败。'
  } finally {
    loading.value = false
  }
}

async function changeLifecycle(item: PluginRuntimeItem, action: 'start' | 'stop') {
  actingPluginId.value = item.pluginId
  errorMessage.value = ''
  successMessage.value = ''
  try {
    status.value = await adminRequest<PluginRuntimeStatus>(
      `/api/admin/plugins/${encodeURIComponent(item.pluginId)}/${action}`,
      { method: 'POST' },
    )
    successMessage.value = action === 'start'
      ? `插件“${item.name}”已启用。`
      : `插件“${item.name}”及其运行中的下游依赖者已停止。`
    await synchronizePluginUi()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '插件生命周期操作失败。'
  } finally {
    actingPluginId.value = ''
  }
}

async function synchronizePluginUi() {
  try {
    await syncAqAdminPluginUi(true)
  } catch (error) {
    errorMessage.value = error instanceof Error
      ? `插件生命周期已更新，但管理端 UI 同步失败：${error.message}`
      : '插件生命周期已更新，但管理端 UI 同步失败。'
  }
  /*
   * 动态路由被卸载时，如果管理员仍停留在该插件页面，立即回到插件管理页，
   * 避免保留已经失效的组件实例。
   */
  if (router.currentRoute.value.path.startsWith('/admin/plugins/')) {
    await router.replace('/admin/plugins')
  }
}

onMounted(() => void load())
</script>

<template>
  <section class="admin-workspace-page">
    <!-- BEGIN：页面标题与重扫操作 -->
    <header class="admin-workspace-hero">
      <div>
        <span class="admin-workspace-eyebrow">Extensions / PF4J</span>
        <h2>插件管理</h2>
        <p>
          插件代码从 workdir/plugins 加载；每个插件拥有独立 ClassLoader、依赖图和
          Spring 子上下文，运行数据保存在各自私有目录。
        </p>
      </div>
      <div class="admin-workspace-hero-actions">
        <button type="button" :disabled="loading || Boolean(actingPluginId)" @click="rescan">
          <Refresh aria-hidden="true" />
          {{ loading ? '扫描中…' : '重新扫描' }}
        </button>
      </div>
    </header>
    <!-- END：页面标题与重扫操作 -->

    <div v-if="errorMessage" class="admin-workspace-state is-error">
      <Warning aria-hidden="true" />
      {{ errorMessage }}
    </div>
    <div v-if="successMessage" class="admin-workspace-state is-success">
      {{ successMessage }}
    </div>

    <template v-if="status">
      <!-- BEGIN：PF4J 能力摘要 -->
      <section class="admin-workspace-card">
        <div class="admin-workspace-summary">
          <div>
            <span>PF4J 加载器</span>
            <strong>{{ status.loaderAvailable ? '可用' : '不可用' }}</strong>
          </div>
          <div>
            <span>已发现插件</span>
            <strong>{{ status.candidateCount }}</strong>
          </div>
          <div>
            <span>运行中的插件</span>
            <strong>{{ startedCount }}</strong>
          </div>
          <div>
            <span>已加载插件 UI</span>
            <strong>{{ loadedUiCount }}</strong>
          </div>
        </div>
      </section>
      <!-- END：PF4J 能力摘要 -->

      <section class="admin-workspace-state is-warning">
        <strong><Connection aria-hidden="true" /> 运行边界</strong>
        <p>{{ status.message }}</p>
        <p>
          插件必须提供 plugin.yaml。停用基础插件时，会先停止所有依赖它的下游插件，
          避免残留失效 Bean 或 ClassLoader 引用。
        </p>
      </section>

      <section
        v-if="aqPluginUiRuntimeState.failures.length"
        class="admin-workspace-state is-error"
      >
        <strong>插件 UI 隔离报告</strong>
        <p
          v-for="failure in aqPluginUiRuntimeState.failures"
          :key="`${failure.pluginId}:${failure.stage}:${failure.message}`"
        >
          {{ failure.pluginId }} / {{ failure.stage }}：{{ failure.message }}
        </p>
      </section>

      <!-- BEGIN：真实插件列表与启停按钮 -->
      <section class="admin-workspace-card">
        <div v-if="!status.items.length" class="admin-workspace-empty">
          <strong>当前没有已安装插件</strong>
          <p>把符合 Aquafish 清单规范的 JAR 放入 workdir/plugins，再点击“重新扫描”。</p>
        </div>

        <div v-else class="admin-plugin-grid">
          <article
            v-for="item in status.items"
            :key="item.pluginId"
            class="admin-plugin-card"
            :class="{ 'has-error': Boolean(item.error) }"
          >
            <div class="admin-plugin-card__heading">
              <div>
                <span class="admin-plugin-card__id">{{ item.pluginId }}</span>
                <h3>{{ item.name }}</h3>
              </div>
              <span class="admin-plugin-state" :class="{ 'is-started': item.started }">
                {{ stateLabel(item) }}
              </span>
            </div>

            <p class="admin-plugin-card__description">
              {{ item.description || '该插件没有提供说明。' }}
            </p>

            <dl class="admin-plugin-meta">
              <div><dt>版本</dt><dd>{{ item.version }}</dd></div>
              <div><dt>提供方</dt><dd>{{ item.provider || '未声明' }}</dd></div>
              <div><dt>载体</dt><dd>{{ item.packageType === 'jar' ? 'JAR' : '开发目录' }}</dd></div>
              <div><dt>类加载器</dt><dd>{{ item.classLoader }}</dd></div>
            </dl>

            <div v-if="item.dependencies.length" class="admin-plugin-dependencies">
              <strong>依赖</strong>
              <span
                v-for="dependency in item.dependencies"
                :key="dependency.pluginId"
                :class="{ 'is-missing': !dependency.present && !dependency.optional }"
              >
                {{ dependency.pluginId }}
                {{ dependency.versionRequirement || '*' }}
                {{ dependency.optional ? '（可选）' : '' }}
              </span>
            </div>

            <p v-if="item.error" class="admin-plugin-card__error">{{ item.error }}</p>

            <div class="admin-plugin-card__actions">
              <button
                v-if="!item.started"
                type="button"
                :disabled="Boolean(actingPluginId)"
                @click="changeLifecycle(item, 'start')"
              >
                <VideoPlay aria-hidden="true" />
                {{ actingPluginId === item.pluginId ? '启动中…' : '启用' }}
              </button>
              <button
                v-else
                type="button"
                class="admin-workspace-action is-secondary"
                :disabled="Boolean(actingPluginId)"
                @click="changeLifecycle(item, 'stop')"
              >
                <VideoPause aria-hidden="true" />
                {{ actingPluginId === item.pluginId ? '停止中…' : '停用' }}
              </button>
            </div>
          </article>
        </div>
      </section>
      <!-- END：真实插件列表与启停按钮 -->
    </template>
  </section>
</template>
<!-- END：PF4J 插件运行管理页 -->
