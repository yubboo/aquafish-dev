<!--
  后台管理员登录页。

  页面行为：
  左右分栏布局——左侧展示钓鱼主题插画，右侧为登录表单区。
  页面首次加载时先展示完整背景，1.5 秒后右侧登录表单淡入。
  表单通过 adminAuthStore 调用真实登录 API；成功后按 redirect 参数跳转，
  失败只显示后端返回的安全错误。会话使用 HttpOnly Cookie。
-->
<template>
  <main class="admin-login-page">
    <!-- ==================== 左侧插画区 START ==================== -->
    <div class="admin-login-left" aria-hidden="true">
      <img
        class="admin-login-left__img"
        :src="loginL"
        alt=""
        aria-hidden="true"
      />
      <!-- 插画上方叠加一层渐变让左侧边缘与右侧融合 -->
      <div class="admin-login-left__fade"></div>
    </div>
    <!-- ==================== 左侧插画区 END ==================== -->


    <!-- ==================== 右侧登录表单区 START ==================== -->
    <div
      class="admin-login-right"
      :style="{ backgroundImage: `url(${loginBg})` }"
    >
      <!-- 登录卡片：1.5 秒后淡入 -->
      <Transition name="login-form">
        <section
          v-if="showForm"
          class="admin-login-card"
          role="dialog"
          aria-modal="true"
          aria-label="管理员登录"
        >
          <!-- 品牌标识 -->
          <div class="admin-login-brand">
            <img
              class="admin-login-brand__decor"
              :src="loginR"
              alt=""
              aria-hidden="true"
            />
            <h1 class="admin-login-brand__title">Aquafish</h1>
            <p class="admin-login-brand__sub">后台管理登录</p>
          </div>

          <!-- 登录表单 -->
          <form class="admin-login-form" @submit.prevent="handleSubmit">
            <!-- 用户名 -->
            <label class="admin-login-field">
              <span class="admin-login-field__icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                  <circle cx="12" cy="7" r="4"/>
                </svg>
              </span>
              <input
                v-model.trim="form.username"
                class="admin-login-field__input"
                type="text"
                autocomplete="username"
                placeholder="用户名 / 邮箱"
              />
            </label>

            <!-- 密码 -->
            <label class="admin-login-field">
              <span class="admin-login-field__icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                  <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                </svg>
              </span>
              <input
                v-model="form.password"
                class="admin-login-field__input"
                type="password"
                autocomplete="current-password"
                placeholder="请输入管理员密码"
              />
            </label>

            <!-- 记住密码 -->
            <div class="admin-login-row">
              <label class="admin-login-remember">
                <input v-model="form.rememberMe" type="checkbox" />
                <span>记住密码</span>
              </label>
            </div>

            <!-- 错误提示 -->
            <p v-if="errorMessage" class="admin-login-error">
              {{ errorMessage }}
            </p>

            <!-- 登录按钮 -->
            <button
              class="admin-login-submit"
              :disabled="adminAuthStore.state.loading"
              type="submit"
            >
              <span v-if="adminAuthStore.state.loading">登录中...</span>
              <span v-else>登 录</span>
            </button>
          </form>

          <!-- 底部链接区 -->
          <div class="admin-login-links">
            <a class="admin-login-links__item" href="/">← 返回前台首页</a>
            <a class="admin-login-links__item" href="/register">注册账号</a>
          </div>

          <!-- 底部提示 -->
          <p class="admin-login-footer">
            账号由首次安装向导创建
          </p>
        </section>
      </Transition>

      <!-- 表单出现前只展示品牌名 -->
      <div v-if="!showForm" class="admin-login-wait">
        <h1 class="admin-login-wait__title">Aquafish</h1>
        <p class="admin-login-wait__hint">正在加载…</p>
      </div>
    </div>
    <!-- ==================== 右侧登录表单区 END ==================== -->
  </main>
</template>

<script setup lang="ts">
/**
 * 页面名称：后台管理员登录页
 *
 * 功能说明：
 * 左右分栏——左为钓鱼主题插画，右为登录表单。
 * 页面加载后 1.5 秒表单从右侧淡入，此前仅展示品牌名。
 *
 * 数据来源：
 * admin/src/api/admin-auth.ts
 *
 * 关联文件：
 * - admin/src/stores/admin-auth.ts：登录状态管理
 * - admin/src/router/admin-auth-guard.ts：路由鉴权守卫
 * - admin/src/assets/images/login_bg.png：右侧底色纹理
 * - admin/src/assets/images/login_l.png：左侧钓鱼插画
 * - admin/src/assets/images/login_r.png：卡片装饰图
 *
 * 样式关联：
 * admin-login-page.css
 */
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminAuthStore } from '../../stores/admin-auth'
import loginBg from '../../assets/images/login_bg.png'
import loginL from '../../assets/images/login_l.png'
import loginR from '../../assets/images/login_r.png'
import './admin-login-page.css'

const route = useRoute()
const router = useRouter()

/** 登录表单是否可见，1.5 秒后变为 true */
const showForm = ref(false)

const form = reactive({
  username: '',
  password: '',
  rememberMe: true,
})

const errorMessage = ref('')

/**
 * 只接受站内 /admin 路径作为登录后目标，拒绝外部 URL，避免开放重定向。
 */
function getRedirectPath(): string {
  const redirect = route.query.redirect

  if (typeof redirect === 'string' && redirect.startsWith('/admin') && redirect !== '/admin/login') {
    return redirect
  }

  return '/admin'
}

/** 校验表单、调用真实登录接口，后端确认成功后跳转到安全目标页面。 */
async function handleSubmit() {
  errorMessage.value = ''

  try {
    await adminAuthStore.login({
      username: form.username,
      password: form.password,
      rememberMe: form.rememberMe,
    })

    await router.replace(getRedirectPath())
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败，请检查账号密码。'
  }
}

onMounted(async () => {
  // 先检查是否已有有效会话
  const ok = await adminAuthStore.ensureUser()

  if (ok) {
    await router.replace(getRedirectPath())
    return
  }

  // 未登录：1.5 秒后淡入登录表单
  setTimeout(() => {
    showForm.value = true
  }, 1500)
})
</script>
