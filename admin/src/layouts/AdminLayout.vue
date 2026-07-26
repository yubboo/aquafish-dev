<!--
  后台主布局组件。
  根据 admin-menu.ts 渲染可折叠侧栏，通过 RouterView 承载后台子页面，并在侧栏底部组合
  AdminAuthBar.vue。布局尺寸由同目录两个当前 CSS 文件共同控制。
-->
<script setup lang="ts">
import { computed, onMounted, ref, watchEffect, type Component } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import {
  ArrowRight,
  Bell,
  Brush,
  Calendar,
  ChatDotRound,
  Connection,
  Document,
  House,
  Lock,
  Notebook,
  Pouring,
  Setting,
  User,
} from '@element-plus/icons-vue'
import {
  adminMenus,
  filterAdminMenusByLicense,
  type AdminMenuIcon,
  type AdminMenuItem,
} from '../config/admin-menu'
import {
  currentLicenseStatus,
  loadLicenseStatus,
} from '../stores/license-status'
import AdminAuthBar from '../components/admin/AdminAuthBar.vue'
import './admin-layout-boxed-sidebar.css'
import './admin-layout-viewport-fix.css'

const route = useRoute()
const openedMenus = ref<string[]>([])

/**
 * 仅映射当前侧栏实际使用的七个图标。
 * 命名导入可被 Vite tree-shaking，不会把整个 Element Plus 图标包打进首屏。
 */
const menuIcons: Record<AdminMenuIcon, Component> = {
  dashboard: House,
  users: User,
  forum: ChatDotRound,
  content: Document,
  theme: Brush,
  plugin: Connection,
  license: Lock,
  system: Setting,
}

/** 菜单未声明图标时使用文档图标作为稳定兜底。 */
function resolveMenuIcon(icon?: AdminMenuIcon): Component {
  return icon ? menuIcons[icon] : Document
}

/** 顶栏日期只在布局初始化时计算，不引入额外日期库。 */
const todayText = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  weekday: 'short',
}).format(new Date())

/**
 * 只展示当前许可证包含的商业模块；基础控制台、用户、授权和系统设置始终保留。
 * 直接输入隐藏 URL 时仍会被路由守卫和后端 API 拦截，因此这里不承担安全职责。
 */
const visibleAdminMenus = computed(() => {
  return filterAdminMenusByLicense(adminMenus, currentLicenseStatus.value)
})

/** 收集所有含子菜单的父级 key，供“一键展开/收起”使用。 */
const allParentMenuKeys = computed(() => {
  return visibleAdminMenus.value
    .filter((menu) => menu.children?.length)
    .map((menu) => menu.key)
})

/** 判断当前是否已展开全部父菜单。 */
const isAllMenusOpen = computed(() => {
  return allParentMenuKeys.value.length > 0
    && allParentMenuKeys.value.every((key) => openedMenus.value.includes(key))
})

/** 根据当前路由判断菜单或其任一子菜单是否处于激活状态。 */
function isMenuActive(menu: AdminMenuItem): boolean {
  if (menu.path && route.path === menu.path) {
    return true
  }
  if (menu.children?.length) {
    return menu.children.some((child) => isMenuActive(child))
  }
  return false
}

/** 查询父菜单是否存在于本地展开集合。 */
function isMenuOpen(menuKey: string): boolean {
  return openedMenus.value.includes(menuKey)
}

/** 切换单个父菜单；无子菜单项不产生展开状态。 */
function toggleMenu(menu: AdminMenuItem): void {
  if (!menu.children?.length) {
    return
  }
  if (openedMenus.value.includes(menu.key)) {
    openedMenus.value = openedMenus.value.filter((key) => key !== menu.key)
    return
  }
  openedMenus.value = [...openedMenus.value, menu.key]
}

/** 根据当前状态一次展开或收起所有父菜单。 */
function toggleAllMenus(): void {
  if (isAllMenusOpen.value) {
    openedMenus.value = []
    return
  }
  openedMenus.value = [...allParentMenuKeys.value]
}

/** 从当前路由反查需要自动展开的父菜单 key。 */
const activeParentKeys = computed(() => {
  return visibleAdminMenus.value
    .filter((menu) => menu.children?.some((child) => isMenuActive(child)))
    .map((menu) => menu.key)
})

watchEffect(() => {
  for (const key of activeParentKeys.value) {
    if (!openedMenus.value.includes(key)) {
      openedMenus.value.push(key)
    }
  }
})

onMounted(() => {
  // 授权页本身可在未激活时进入，布局因此需要主动读取一次状态来刷新侧栏。
  void loadLicenseStatus().catch(() => undefined)
})
</script>

<template>
  <div class="admin-layout">
    <aside class="admin-layout__sidebar">
      <!-- 顶部品牌盒子 -->
      <section class="admin-sidebar-brand-box">
        <div class="admin-sidebar-brand-main">
          <div class="admin-layout__brand-logo" aria-hidden="true">
            <Pouring />
          </div>

          <div class="admin-layout__brand-text">
            <strong>Aquafish</strong>
            <span>CMS · 论坛 · 社区</span>
          </div>
        </div>

        <button
          class="admin-sidebar-toggle-all"
          type="button"
          :title="isAllMenusOpen ? '全部收起菜单' : '全部展开菜单'"
          @click="toggleAllMenus"
        >
          <span v-if="isAllMenusOpen">收起</span>
          <span v-else>展开</span>
        </button>
      </section>

      <a class="admin-sidebar-site-link" href="/" target="_blank" rel="noreferrer">
        <span>访问前台</span>
        <ArrowRight aria-hidden="true" />
      </a>

      <!-- 中间菜单大盒子 -->
      <section class="admin-sidebar-menu-box">
        <nav class="admin-layout__menu">
          <div
            v-for="menu in visibleAdminMenus"
            :key="menu.key"
            class="admin-layout__menu-group"
          >
            <template v-if="menu.children?.length">
              <button
                type="button"
                class="admin-layout__menu-parent"
                :class="{ 'is-active': isMenuActive(menu), 'is-open': isMenuOpen(menu.key) }"
                @click="toggleMenu(menu)"
              >
                <span class="admin-layout__menu-parent-main">
                  <span class="admin-layout__menu-icon" aria-hidden="true">
                    <component :is="resolveMenuIcon(menu.icon)" />
                  </span>
                  <span class="admin-layout__menu-title">{{ menu.title }}</span>
                </span>

                <ArrowRight class="admin-layout__menu-arrow" aria-hidden="true" />
              </button>

              <transition name="admin-submenu">
                <div
                  v-show="isMenuOpen(menu.key)"
                  class="admin-layout__submenu"
                >
                  <RouterLink
                    v-for="child in menu.children"
                    :key="child.key"
                    :to="child.path || '/admin'"
                    class="admin-layout__submenu-link"
                    :class="{ 'is-active': isMenuActive(child) }"
                  >
                    <span class="admin-layout__submenu-dot"></span>
                    <span>{{ child.title }}</span>
                  </RouterLink>
                </div>
              </transition>
            </template>

            <RouterLink
              v-else
              :to="menu.path || '/admin'"
              class="admin-layout__menu-link"
              :class="{ 'is-active': isMenuActive(menu) }"
            >
              <span class="admin-layout__menu-icon" aria-hidden="true">
                <component :is="resolveMenuIcon(menu.icon)" />
              </span>
              <span class="admin-layout__menu-title">{{ menu.title }}</span>
            </RouterLink>
          </div>
        </nav>
      </section>

      <!-- 底部登录登出大盒子 -->
      <section class="admin-sidebar-user-box">
        <AdminAuthBar />
      </section>
    </aside>

    <main class="admin-layout__main">
      <header class="admin-layout__topbar">
        <span class="admin-layout__date">
          <Calendar aria-hidden="true" />
          {{ todayText }}
        </span>
        <span class="admin-layout__topbar-divider" aria-hidden="true"></span>
        <a href="/content" target="_blank" rel="noreferrer">
          <Notebook aria-hidden="true" />
          内容前台
        </a>
        <span class="admin-layout__topbar-divider" aria-hidden="true"></span>
        <button type="button" title="通知中心（初版）" aria-label="通知中心">
          <Bell aria-hidden="true" />
        </button>
      </header>
      <div class="admin-layout__content">
        <RouterView />
      </div>
    </main>
  </div>
</template>
