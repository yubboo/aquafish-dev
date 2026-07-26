<!--
  后台固定链接设置页。
  读取 short、halo、discuz 和 custom 规则，修改时请求后端生成真实预览，保存后由 core
  permalink 服务持久化；页面不自行实现最终 URL 拼接，避免前后端规则漂移。
-->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  fetchPermalinkSettings,
  previewPermalinkSettings,
  savePermalinkSettings,
  type PermalinkMode,
  type PermalinkPreview,
  type PermalinkSettings,
} from '../../../api/permalink'
import './permalink-settings-page.css'

interface PermalinkPreset {
  mode: PermalinkMode
  title: string
  tag: string
  description: string
  bestFor: string
  examples: string[]
}

const presets: PermalinkPreset[] = [
  {
    mode: 'short',
    title: '短链接模式',
    tag: '推荐默认',
    description: 'Aquafish 默认推荐模式，短、稳、好迁移，适合 CMS + BBS 混合系统。',
    bestFor: '适合新站、综合社区、内容 + 论坛一体站。',
    examples: ['/p/1', '/t/1', '/f/general', '/c/dev']
  },
  {
    mode: 'halo',
    title: 'Halo CMS 风格',
    tag: '博客友好',
    description: '吸收 Halo 的 slug / permalink 思路，适合文章、分类、标签和单页。',
    bestFor: '适合偏博客、官网、知识库、文档站。',
    examples: ['/archives/demo', '/categories/dev', '/tags/ai', '/page/about']
  },
  {
    mode: 'discuz',
    title: 'Discuz 兼容风格',
    tag: '迁移友好',
    description: '兼容传统论坛伪静态规则，方便从 Discuz 迁移并保留旧链接。',
    bestFor: '适合老论坛迁移、SEO 旧链接保留、传统社区。',
    examples: ['thread-1.html', 'forum-1.html', 'article-1.html', 'thread-1-1-1.html']
  },
  {
    mode: 'custom',
    title: '自定义规则',
    tag: '高级配置',
    description: '允许站长自定义文章、帖子、板块、分类和标签的链接规则。',
    bestFor: '适合有明确 SEO 规则、历史链接规则或特殊运营需求的网站。',
    examples: ['/article/{id}', '/thread/{tid}', '/forum/{key}', '/category/{slug}']
  }
]

const form = ref<PermalinkSettings>({
  mode: 'short',
  articlePattern: '/p/{id}',
  pagePattern: '/page/{slug}',
  categoryPattern: '/c/{key}',
  tagPattern: '/tag/{key}',
  forumPattern: '/f/{key}',
  threadPattern: '/t/{tid}',
  userPattern: '/u/{name}',
  enableDiscuzCompat: true,
  enableHaloCompat: true,
  enableOldLinkRedirect: true
})

const preview = ref<PermalinkPreview | null>(null)
const storagePath = ref('')

const loading = ref(false)
const saving = ref(false)
const previewing = ref(false)
const hasLoaded = ref(false)

const errorMessage = ref('')
const successMessage = ref('')

let previewTimer: number | undefined

/** 当前模式对应的说明卡片；找不到时回退到第一个安全预设。 */
const selectedPreset = computed(() => {
  return presets.find((preset) => preset.mode === form.value.mode) || presets[0]
})

/** 首次读取后端持久化设置、真实预览和配置存储位置。 */
async function loadSettings(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  successMessage.value = ''

  try {
    const data = await fetchPermalinkSettings()

    form.value = data.settings
    preview.value = data.preview
    storagePath.value = data.storagePath
    hasLoaded.value = true
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '固定链接设置读取失败'
  } finally {
    loading.value = false
  }
}

/** 把当前表单交给后端 PermalinkBuilder 生成预览，前端不自行拼 URL。 */
async function refreshPreview(): Promise<void> {
  if (!hasLoaded.value) {
    return
  }

  previewing.value = true
  errorMessage.value = ''

  try {
    preview.value = await previewPermalinkSettings(form.value)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '固定链接预览生成失败'
  } finally {
    previewing.value = false
  }
}

/** 以 260ms 防抖刷新预览，避免连续输入时向后端发送过多请求。 */
function schedulePreview(): void {
  if (!hasLoaded.value) {
    return
  }

  if (previewTimer !== undefined) {
    window.clearTimeout(previewTimer)
  }

  previewTimer = window.setTimeout(() => {
    void refreshPreview()
  }, 260)
}

/** 将选中预设完整写入表单；custom 仅切换模式并保留用户当前规则。 */
function applyPreset(mode: PermalinkMode): void {
  successMessage.value = ''
  errorMessage.value = ''

  if (mode === 'short') {
    form.value = {
      mode: 'short',
      articlePattern: '/p/{id}',
      pagePattern: '/page/{slug}',
      categoryPattern: '/c/{key}',
      tagPattern: '/tag/{key}',
      forumPattern: '/f/{key}',
      threadPattern: '/t/{tid}',
      userPattern: '/u/{name}',
      enableDiscuzCompat: true,
      enableHaloCompat: true,
      enableOldLinkRedirect: true
    }
    return
  }

  if (mode === 'halo') {
    form.value = {
      mode: 'halo',
      articlePattern: '/archives/{slug}',
      pagePattern: '/page/{slug}',
      categoryPattern: '/categories/{slug}',
      tagPattern: '/tags/{slug}',
      forumPattern: '/f/{key}',
      threadPattern: '/t/{tid}',
      userPattern: '/u/{name}',
      enableDiscuzCompat: false,
      enableHaloCompat: true,
      enableOldLinkRedirect: true
    }
    return
  }

  if (mode === 'discuz') {
    form.value = {
      mode: 'discuz',
      articlePattern: 'article-{id}.html',
      pagePattern: '/page/{slug}',
      categoryPattern: 'category-{id}.html',
      tagPattern: 'tag-{id}.html',
      forumPattern: 'forum-{fid}.html',
      threadPattern: 'thread-{tid}.html',
      userPattern: 'space-{uid}.html',
      enableDiscuzCompat: true,
      enableHaloCompat: false,
      enableOldLinkRedirect: true
    }
    return
  }

  form.value = {
    ...form.value,
    mode: 'custom'
  }
}

/** 保存经后端校验的设置，并使用后端返回值覆盖本地状态以保持一致。 */
async function saveSettings(): Promise<void> {
  saving.value = true
  errorMessage.value = ''
  successMessage.value = ''

  try {
    const data = await savePermalinkSettings(form.value)

    form.value = data.settings
    preview.value = data.preview
    storagePath.value = data.storagePath
    successMessage.value = '固定链接设置保存成功'
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '固定链接设置保存失败'
  } finally {
    saving.value = false
  }
}

watch(
  form,
  () => {
    successMessage.value = ''
    schedulePreview()
  },
  {
    deep: true
  }
)

onMounted(() => {
  void loadSettings()
})
</script>

<template>
  <section class="permalink-page">
    <div class="permalink-hero">
      <div>
        <span class="permalink-kicker">System Settings</span>
        <h1>固定链接设置</h1>
        <p>
          融合 Halo 的 CMS slug / permalink 思路，以及 Discuz 的论坛 tid / fid 稳定链接和伪静态兼容能力。
        </p>
      </div>

      <div class="permalink-hero-actions">
        <button
          type="button"
          class="permalink-ghost-button"
          :disabled="loading || saving"
          @click="loadSettings"
        >
          重新读取
        </button>

        <button
          type="button"
          class="permalink-save-button"
          :disabled="loading || saving"
          @click="saveSettings"
        >
          {{ saving ? '保存中...' : '保存设置' }}
        </button>
      </div>
    </div>

    <div
      v-if="loading || errorMessage || successMessage || storagePath"
      class="permalink-status-card"
      :class="{
        'is-error': errorMessage,
        'is-success': successMessage
      }"
    >
      <div>
        <strong v-if="loading">正在读取固定链接设置...</strong>
        <strong v-else-if="errorMessage">接口异常</strong>
        <strong v-else-if="successMessage">{{ successMessage }}</strong>
        <strong v-else>后端配置已连接</strong>

        <p v-if="errorMessage">{{ errorMessage }}</p>
        <p v-else-if="storagePath">保存位置：{{ storagePath }}</p>
        <p v-else>页面正在连接 /api/admin/settings/permalink</p>
      </div>

      <span v-if="previewing" class="permalink-previewing">预览刷新中</span>
    </div>

    <div class="permalink-mode-grid">
      <button
        v-for="preset in presets"
        :key="preset.mode"
        type="button"
        class="permalink-mode-card"
        :class="{ 'is-active': form.mode === preset.mode }"
        @click="applyPreset(preset.mode)"
      >
        <span class="permalink-mode-tag">{{ preset.tag }}</span>
        <strong>{{ preset.title }}</strong>
        <p>{{ preset.description }}</p>
        <small>{{ preset.bestFor }}</small>
      </button>
    </div>

    <div class="permalink-content-grid">
      <section class="permalink-panel">
        <div class="permalink-panel-title">
          <span>🔗</span>
          <div>
            <h2>链接规则</h2>
            <p>这里决定 CMS、BBS、用户主页最终生成什么访问地址。</p>
          </div>
        </div>

        <div class="permalink-form-grid">
          <label class="permalink-field">
            <span>文章链接</span>
            <input v-model="form.articlePattern" type="text">
          </label>

          <label class="permalink-field">
            <span>单页链接</span>
            <input v-model="form.pagePattern" type="text">
          </label>

          <label class="permalink-field">
            <span>分类链接</span>
            <input v-model="form.categoryPattern" type="text">
          </label>

          <label class="permalink-field">
            <span>标签链接</span>
            <input v-model="form.tagPattern" type="text">
          </label>

          <label class="permalink-field">
            <span>板块链接</span>
            <input v-model="form.forumPattern" type="text">
          </label>

          <label class="permalink-field">
            <span>帖子链接</span>
            <input v-model="form.threadPattern" type="text">
          </label>

          <label class="permalink-field">
            <span>用户主页</span>
            <input v-model="form.userPattern" type="text">
          </label>
        </div>
      </section>

      <aside class="permalink-panel permalink-preview-panel">
        <div class="permalink-panel-title">
          <span>✨</span>
          <div>
            <h2>当前预览</h2>
            <p>{{ selectedPreset.title }} · {{ selectedPreset.tag }}</p>
          </div>
        </div>

        <div class="permalink-preview-list">
          <div>
            <span>文章</span>
            <strong>{{ preview?.article || '-' }}</strong>
          </div>

          <div>
            <span>单页</span>
            <strong>{{ preview?.page || '-' }}</strong>
          </div>

          <div>
            <span>分类</span>
            <strong>{{ preview?.category || '-' }}</strong>
          </div>

          <div>
            <span>标签</span>
            <strong>{{ preview?.tag || '-' }}</strong>
          </div>

          <div>
            <span>板块</span>
            <strong>{{ preview?.forum || '-' }}</strong>
          </div>

          <div>
            <span>帖子</span>
            <strong>{{ preview?.thread || '-' }}</strong>
          </div>

          <div>
            <span>用户</span>
            <strong>{{ preview?.user || '-' }}</strong>
          </div>
        </div>

        <div class="permalink-example-box">
          <h3>后端返回示例</h3>

          <template v-if="preview?.examples?.length">
            <p
              v-for="example in preview.examples"
              :key="example"
            >
              {{ example }}
            </p>
          </template>

          <template v-else>
            <p
              v-for="example in selectedPreset.examples"
              :key="example"
            >
              {{ example }}
            </p>
          </template>
        </div>
      </aside>
    </div>

    <section class="permalink-panel">
      <div class="permalink-panel-title">
        <span>🛡️</span>
        <div>
          <h2>兼容与跳转</h2>
          <p>为 Halo / Discuz 迁移、老链接保留和 SEO 防死链预留。</p>
        </div>
      </div>

      <div class="permalink-switch-grid">
        <label class="permalink-switch">
          <input v-model="form.enableDiscuzCompat" type="checkbox">
          <span></span>
          <div>
            <strong>启用 Discuz 兼容链接</strong>
            <p>允许 thread-1.html、forum-1.html 等旧论坛链接访问。</p>
          </div>
        </label>

        <label class="permalink-switch">
          <input v-model="form.enableHaloCompat" type="checkbox">
          <span></span>
          <div>
            <strong>启用 Halo 风格兼容链接</strong>
            <p>允许 /archives/demo、/categories/dev 等 CMS 风格访问。</p>
          </div>
        </label>

        <label class="permalink-switch">
          <input v-model="form.enableOldLinkRedirect" type="checkbox">
          <span></span>
          <div>
            <strong>启用旧链接 301 跳转</strong>
            <p>旧链接命中后跳转到当前主固定链接，避免 SEO 死链。</p>
          </div>
        </label>
      </div>
    </section>
  </section>
</template>
