<!-- 本页面只在 Vite 开发服务器根路径出现；生产根路径由后端主题控制器直接响应。 -->
<script setup lang="ts">
import { onMounted } from 'vue'

onMounted(() => {
  window.location.replace('/site')
})
</script>

<template>
  <main class="public-handoff">
    <h1>正在进入 Aquafish 前台…</h1>
    <p>如果没有自动跳转，请 <a href="/site">点击这里</a>。</p>
  </main>
</template>

<style scoped>
.public-handoff {
  min-height: 100vh;
  display: grid;
  place-content: center;
  gap: 10px;
  padding: 32px;
  color: #172033;
  background: linear-gradient(135deg, #fdf2f8, #eff6ff);
  text-align: center;
}
.public-handoff h1,
.public-handoff p { margin: 0; }
.public-handoff a { color: #6d4aff; font-weight: 800; }
</style>
