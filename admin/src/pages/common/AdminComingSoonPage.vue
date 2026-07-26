<!--
  尚未实现后台功能的统一占位页。
  router/index.ts 通过 route.meta 传入标题、模块名与说明，使菜单和 URL 可以先稳定下来，
  后续真实页面完成后只需替换对应路由组件。
-->
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import './admin-coming-soon-page.css'

const route = useRoute()

const title = computed(() => {
  return String(route.meta.title || '功能开发中')
})

const moduleName = computed(() => {
  return String(route.meta.moduleName || 'Aquafish Admin')
})

const description = computed(() => {
  return String(route.meta.description || '这个功能页面已经预留，后续会接入真实接口和业务逻辑。')
})

const path = computed(() => route.path)
</script>

<template>
  <section class="admin-coming-soon-page">
    <div class="admin-coming-hero">
      <div class="admin-coming-kicker">{{ moduleName }}</div>

      <h1>{{ title }}</h1>

      <p>{{ description }}</p>

      <div class="admin-coming-path">
        <span>当前路由</span>
        <strong>{{ path }}</strong>
      </div>
    </div>

    <div class="admin-coming-grid">
      <article class="admin-coming-card">
        <span class="admin-coming-card-icon">🧩</span>
        <h2>页面结构已预留</h2>
        <p>当前页面已经接入后台路由，后续只需要替换为正式业务页面。</p>
      </article>

      <article class="admin-coming-card">
        <span class="admin-coming-card-icon">🔌</span>
        <h2>接口后续接入</h2>
        <p>等对应后端模块完成后，再接入 API、表格、表单和权限控制。</p>
      </article>

      <article class="admin-coming-card">
        <span class="admin-coming-card-icon">✨</span>
        <h2>保持模块归档</h2>
        <p>页面会按 pages 下的模块目录归档，不会全部塞进一个文件。</p>
      </article>
    </div>
  </section>
</template>
