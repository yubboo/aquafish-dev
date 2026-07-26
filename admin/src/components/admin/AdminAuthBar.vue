<!--
  当前管理员信息与退出组件。
  从 adminAuthStore 读取后端确认过的用户，展示头像缩写、角色并调用统一 logout；样式仅由
  admin-auth-bar.css 提供，当前组件被 AdminLayout.vue 的侧栏底部使用。
-->
<template>
  <div v-if="adminAuthStore.state.user" class="admin-auth-bar">
    <div class="admin-auth-bar__user">
      <span class="admin-auth-bar__avatar">
        {{ avatarText }}
      </span>

      <div class="admin-auth-bar__meta">
        <strong>{{ adminAuthStore.state.user.displayName || adminAuthStore.state.user.username }}</strong>
        <span>{{ adminAuthStore.state.user.roles.join(' / ') }}</span>
      </div>
    </div>

    <button class="admin-auth-bar__logout" type="button" @click="handleLogout">
      退出
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { adminAuthStore } from '../../stores/admin-auth'
import './admin-auth-bar.css'

const router = useRouter()

/** 优先取显示名或用户名首字，作为没有头像图片时的稳定占位。 */
const avatarText = computed(() => {
  const user = adminAuthStore.state.user

  if (!user) {
    return 'A'
  }

  return (user.displayName || user.username || 'A').slice(0, 1).toUpperCase()
})

/**
 * 调用统一会话仓库退出，随后跳转到前台首页。
 *
 * 使用 window.location.href 而非 router.replace，确保：
 * 1. 完整刷新页面，清除所有前端缓存的登录状态；
 * 2. /site 由后端主题控制器直接响应，不走 Vue 路由。
 */
async function handleLogout() {
  await adminAuthStore.logout()
  window.location.href = '/site'
}

onMounted(() => {
  adminAuthStore.ensureUser()
})
</script>
