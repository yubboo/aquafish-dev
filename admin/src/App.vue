
<!--
  Aquafish 后台根组件。
  只承载 RouterView，具体布局由路由选择 SetupPage、登录页或 AdminLayout；全局初始化与
  守卫注册全部位于 main.ts，避免根组件在每次渲染时重复执行副作用。
-->
<template>
  <router-view />
</template>
