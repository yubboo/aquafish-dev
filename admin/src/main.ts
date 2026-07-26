/**
 * Aquafish 后台前端启动入口。
 *
 * 先安装全局 API 防护，再按“安装状态 → 授权状态 → 管理员登录”顺序注册路由守卫，
 * 最后挂载 Vue。这个顺序保证空白系统先安装、已安装系统先授权、之后才判断登录。
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'

import { installAdminFetchGuard } from './api/admin-fetch-guard'
import App from './App.vue'
import router from './router'
import { installAdminAuthGuard } from './router/admin-auth-guard'
import { installLicenseStatusGuard } from './router/license-status-guard'
import { installSetupStatusGuard } from './router/setup-status-guard'
import './styles/index.css'
import './styles.css'

const app = createApp(App)

app.use(createPinia())
installAdminFetchGuard()
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
app.use(router)
app.mount('#app')
