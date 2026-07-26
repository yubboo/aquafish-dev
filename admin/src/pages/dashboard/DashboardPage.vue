<!-- 后台控制台：展示用户、论坛、内容、主题四个核心域的真实数据库数量。 -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ChatDotRound,
  Collection,
  Coin,
  Document,
  Refresh,
  User,
} from '@element-plus/icons-vue'
import { adminRequest } from '../../api/admin-workspace'
import './dashboard-page.css'

interface DashboardData {
  counts: Record<string, number>
  databaseType: string
  tablePrefix: string
  publicEntry: string
  forumEntry: string
}

const data = ref<DashboardData | null>(null)
const loading = ref(false)
const errorMessage = ref('')

const cards = computed(() => [
  { icon: User, label: '用户', value: data.value?.counts.users ?? 0, note: '账号与后台管理员', path: '/admin/users' },
  { icon: Collection, label: '论坛板块', value: data.value?.counts.sections ?? 0, note: '公开与受限板块', path: '/admin/forum/sections' },
  { icon: ChatDotRound, label: '论坛主题', value: data.value?.counts.threads ?? 0, note: '首帖与回复聚合', path: '/admin/forum/posts' },
  { icon: Document, label: 'CMS 文章', value: data.value?.counts.articles ?? 0, note: '草稿与已发布文章', path: '/admin/content/articles' },
])

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    data.value = await adminRequest<DashboardData>('/api/admin/dashboard')
  } catch (error) {
    data.value = null
    errorMessage.value = error instanceof Error ? error.message : '控制台数据读取失败。'
  } finally {
    loading.value = false
  }
}

onMounted(() => void load())
</script>

<template>
  <section class="dashboard-page">
    <div class="dashboard-hero">
      <div>
        <div class="dashboard-hero__badge">Aquafish Overview</div>
        <h2>站点运行概览</h2>
        <p>
          用户、论坛、文章和主题数据均从当前实例数据库与 workdir 实时读取。
        </p>
      </div>
      <button class="dashboard-refresh" type="button" :disabled="loading" @click="load">
        <Refresh aria-hidden="true" />
        {{ loading ? '读取中…' : '刷新数据' }}
      </button>
    </div>

    <div v-if="errorMessage" class="dashboard-error">{{ errorMessage }}</div>

    <div class="dashboard-grid">
      <RouterLink v-for="card in cards" :key="card.label" :to="card.path" class="dashboard-stat">
        <span class="dashboard-stat__icon" aria-hidden="true">
          <component :is="card.icon" />
        </span>
        <div>
          <span class="dashboard-stat__label">{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
          <p>{{ card.note }}</p>
        </div>
      </RouterLink>
    </div>

    <div class="dashboard-panels">
      <section class="dashboard-panel">
        <div class="dashboard-panel__header">
          <span class="dashboard-panel__icon"><Coin aria-hidden="true" /></span>
          <h3>运行数据源</h3>
        </div>
        <div class="dashboard-health">
          <p><span>数据库类型：</span><strong>{{ data?.databaseType || '—' }}</strong></p>
          <p><span>数据表前缀：</span><strong>{{ data?.tablePrefix || '—' }}</strong></p>
          <p><span>主题记录数：</span><strong>{{ data?.counts.themes ?? 0 }}</strong></p>
        </div>
      </section>

      <section class="dashboard-panel">
        <div class="dashboard-panel__header">
          <span class="dashboard-panel__icon"><Document aria-hidden="true" /></span>
          <h3>前台入口</h3>
        </div>
        <ul class="dashboard-todos">
          <li>
            <span>站</span>
            <div><strong><a href="/" target="_blank">打开站点首页</a></strong><p>当前主题 + 最新已发布文章</p></div>
          </li>
          <li>
            <span>坛</span>
            <div><strong><a href="/forum" target="_blank">打开公开论坛</a></strong><p>公开板块、主题和楼层浏览</p></div>
          </li>
          <li>
            <span>文</span>
            <div><strong><a href="/content" target="_blank">打开内容中心</a></strong><p>CMS 文章列表与详情</p></div>
          </li>
        </ul>
      </section>
    </div>
  </section>
</template>
