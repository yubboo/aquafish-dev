/**
 * Aquafish 后台前端启动入口。
 *
 * 统一 Axios 客户端在领域 API 导入时完成安全配置；应用继续按
 * “安装状态 → 管理员登录 → 授权状态”顺序注册路由守卫，最后挂载 Vue。
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'

import '@aquafish/components/style.css'
import App from './App.vue'
import router from './router'
import { installAdminAuthGuard } from './router/admin-auth-guard'
import { installLicenseStatusGuard } from './router/license-status-guard'
import { installSetupStatusGuard } from './router/setup-status-guard'
import './styles/index.css'
import './styles.css'

const app = createApp(App)

app.use(createPinia())
/*
 * 首次安装守卫必须先注册，保证空白系统先进入 /setup，
 * 确认已安装后才继续执行后台登录态检查。
 */
installSetupStatusGuard(router)
installAdminAuthGuard(router)
/*
 * 授权守卫必须最后注册：先确认系统已安装，再确认管理员身份，最后判断平台授权。
 */
installLicenseStatusGuard(router)
/*
 * 插件宿主只在进入已鉴权的 /admin 路由后异步加载，避免安装页和公开入口承担
 * Vue Router、Pinia、公共组件等完整插件共享命名空间的首屏体积。
 */
router.afterEach((to) => {
  if (to.path === '/admin' || to.path.startsWith('/admin/')) {
    void import('./plugin-ui/loader')
      .then(async (runtime) => {
        runtime.installAqAdminPluginUiRuntime(router)
        await runtime.syncAqAdminPluginUi()
      })
      .catch(() => undefined)
  }
})
app.use(router)
app.mount('#app')
