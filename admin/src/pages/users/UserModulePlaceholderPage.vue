<!--
  用户模块尚未完成子功能的轻量占位页。
  标题来自 route.meta，由 router/index.ts 的用户路由复用；真实页面完成后应替换路由组件，
  不要继续在本占位组件中堆业务逻辑。
-->
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import './user-pages.css'

const route = useRoute()

const title = computed(() => String(route.meta.title || '用户模块'))
</script>

<template>
  <section class="admin-user-page admin-page-scrollable">
    <div class="admin-user-placeholder">
      <div class="admin-user-placeholder__inner">
        <h2>{{ title }}</h2>
        <p>
          这个页面的菜单和底层表已经规划完成。后续会按模块继续接入真实接口，
          包括用户栏目、资料统计、用户标签、禁止用户、禁止 IP、积分奖惩、推荐关注、推荐好友、资料审核和认证设置。
        </p>
      </div>
    </div>
  </section>
</template>
