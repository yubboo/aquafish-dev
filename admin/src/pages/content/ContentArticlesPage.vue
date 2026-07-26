<!-- CMS 文章真实闭环：创建草稿、查看列表、发布到主题前台。 -->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { adminRequest } from '../../api/admin-workspace'
import '../workspace/admin-workspace.css'

interface Article {
  id: number
  title: string
  slug: string
  excerpt: string
  contentText: string
  status: string
  visibility: string
  viewCount: number
  commentCount: number
  publishedAt: string | null
  updatedAt: string | null
}

interface ArticlePage {
  page: number
  pageSize: number
  total: number
  totalPages: number
  items: Article[]
}

const data = ref<ArticlePage | null>(null)
const loading = ref(false)
const saving = ref(false)
const showCreateForm = ref(false)
const query = ref('')
const statusFilter = ref('ALL')
const errorMessage = ref('')
const successMessage = ref('')
const form = reactive({
  title: '',
  slug: '',
  excerpt: '',
  contentText: '',
})

/** 当前页前端即时筛选；后续数据量增长后再把筛选条件交给分页 API。 */
const visibleArticles = computed(() => {
  const keyword = query.value.trim().toLocaleLowerCase()
  return (data.value?.items || []).filter((article) => {
    const statusMatches = statusFilter.value === 'ALL'
      || article.status === statusFilter.value
    const keywordMatches = !keyword
      || article.title.toLocaleLowerCase().includes(keyword)
      || article.slug.toLocaleLowerCase().includes(keyword)
    return statusMatches && keywordMatches
  })
})

async function load(page = 1) {
  loading.value = true
  errorMessage.value = ''
  try {
    data.value = await adminRequest<ArticlePage>(
      `/api/admin/content/articles?page=${page}&pageSize=20`,
    )
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '文章列表读取失败。'
  } finally {
    loading.value = false
  }
}

async function create() {
  saving.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await adminRequest<Article>('/api/admin/content/articles', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(form),
    })
    Object.assign(form, { title: '', slug: '', excerpt: '', contentText: '' })
    showCreateForm.value = false
    successMessage.value = '文章草稿及第一版历史已创建。'
    await load(1)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '文章创建失败。'
  } finally {
    saving.value = false
  }
}

async function publish(article: Article) {
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await adminRequest<Article>(`/api/admin/content/articles/${article.id}/publish`, {
      method: 'POST',
    })
    successMessage.value = `《${article.title}》已发布到前台。`
    await load(data.value?.page || 1)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '文章发布失败。'
  }
}

onMounted(() => void load())
</script>

<template>
  <section class="admin-workspace-page">
    <header class="admin-workspace-hero">
      <div>
        <span class="admin-workspace-eyebrow">Content / Articles</span>
        <h2>文章管理</h2>
        <p>新文章先保存为草稿并写入版本历史，确认后再发布到当前主题前台。</p>
      </div>
      <button class="admin-workspace-action" type="button" @click="showCreateForm = !showCreateForm">
        {{ showCreateForm ? '收起编辑器' : '创建文章' }}
      </button>
    </header>

    <form v-if="showCreateForm" class="admin-workspace-form" @submit.prevent="create">
      <h3>创建文章草稿</h3>
      <div class="admin-workspace-fields">
        <label class="admin-workspace-field">
          标题
          <input v-model.trim="form.title" required placeholder="文章标题">
        </label>
        <label class="admin-workspace-field">
          固定别名
          <input v-model.trim="form.slug" required placeholder="hello-aquafish">
        </label>
        <label class="admin-workspace-field is-wide">
          摘要
          <textarea v-model.trim="form.excerpt" placeholder="用于前台列表展示"></textarea>
        </label>
        <label class="admin-workspace-field is-wide">
          正文
          <textarea v-model.trim="form.contentText" required placeholder="输入文章正文"></textarea>
        </label>
      </div>
      <div class="admin-workspace-form-actions">
        <button class="admin-workspace-action" :disabled="saving" type="submit">
          {{ saving ? '创建中…' : '保存为草稿' }}
        </button>
      </div>
    </form>

    <section class="admin-workspace-toolbar" aria-label="文章筛选">
      <label>
        <span>状态</span>
        <select v-model="statusFilter">
          <option value="ALL">全部状态</option>
          <option value="DRAFT">草稿</option>
          <option value="PUBLISHED">已发布</option>
          <option value="ARCHIVED">已归档</option>
        </select>
      </label>
      <label class="is-search">
        <span>搜索文章</span>
        <input v-model.trim="query" type="search" placeholder="搜索标题或固定别名">
      </label>
      <a class="admin-workspace-action is-secondary" href="/content" target="_blank">打开内容前台</a>
    </section>

    <div v-if="errorMessage" class="admin-workspace-state is-error">{{ errorMessage }}</div>
    <div v-if="successMessage" class="admin-workspace-state">{{ successMessage }}</div>

    <section class="admin-workspace-card">
      <div v-if="loading && !data" class="admin-workspace-empty">正在读取文章…</div>
      <div v-else-if="data && !data.items.length" class="admin-workspace-empty">
        <strong>还没有文章</strong>
        <p>创建第一篇草稿并发布后，公开首页会显示真实内容。</p>
      </div>
      <div v-else-if="data" class="admin-workspace-table-wrap">
        <table class="admin-workspace-table">
          <thead>
            <tr>
              <th>ID</th><th>标题</th><th>别名</th><th>状态</th>
              <th>浏览 / 评论</th><th>更新时间</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="article in visibleArticles" :key="article.id">
              <td>{{ article.id }}</td>
              <td>{{ article.title }}</td>
              <td>{{ article.slug }}</td>
              <td>{{ article.status }}</td>
              <td>{{ article.viewCount }} / {{ article.commentCount }}</td>
              <td>{{ article.updatedAt || '—' }}</td>
              <td>
                <button
                  v-if="article.status !== 'PUBLISHED'"
                  class="admin-workspace-action"
                  type="button"
                  @click="publish(article)"
                >
                  发布
                </button>
                <a
                  v-else
                  class="admin-workspace-action is-secondary"
                  :href="`/content/${article.slug}`"
                  target="_blank"
                >
                  查看前台
                </a>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!visibleArticles.length" class="admin-workspace-empty">
          当前筛选条件下没有文章。
        </div>
      </div>
      <footer v-if="data" class="admin-workspace-pagination">
        <span>共 {{ data.total }} 篇，第 {{ data.page }} / {{ Math.max(data.totalPages, 1) }} 页</span>
        <div>
          <button :disabled="data.page <= 1" type="button" @click="load(data.page - 1)">上一页</button>
          <button :disabled="data.page >= data.totalPages" type="button" @click="load(data.page + 1)">下一页</button>
        </div>
      </footer>
    </section>
  </section>
</template>
