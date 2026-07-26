# 插件 UI 构建器使用说明

## 文档目的

本文说明 Aquafish 插件前端的目录、入口、构建配置、共享依赖和标准产物。

当前构建器源码：

```text
H:\javaweb\aquafish\admin\packages\plugin-bundler-kit
```

包名：

```text
@aquafish/plugin-bundler-kit
```

## 推荐插件目录

```text
plugin-root/
├─ src/
│  └─ main/
│     └─ resources/
│        └─ plugin.yaml
└─ ui/
   ├─ package.json
   ├─ vite.config.ts
   └─ src/
      ├─ index.ts
      └─ views/
```

构建命令应在 `plugin-root/ui` 中运行。默认清单位置为：

```text
../src/main/resources/plugin.yaml
```

## Vite 配置

```ts
import { defineAqPluginViteConfig } from '@aquafish/plugin-bundler-kit'

export default defineAqPluginViteConfig()
```

需要别名或其他 Vite 插件时：

```ts
import { resolve } from 'node:path'
import { defineAqPluginViteConfig } from '@aquafish/plugin-bundler-kit'

export default defineAqPluginViteConfig({
  vite: {
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src'),
      },
    },
  },
})
```

用户配置不能覆盖 Aquafish 的共享依赖 external 和固定产物协议。

## 插件入口

默认入口为 `src/index.ts`：

```ts
import { definePlugin } from '@aquafish/ui-shared'

import PluginSettings from './views/PluginSettings.vue'

export default definePlugin({
  name: 'example-plugin',
  applications: ['aqadmin'],
  components: {
    PluginSettings,
  },
  routes: [],
  extensionPoints: [],
})
```

插件名称应与 `plugin.yaml` 中的插件 ID 保持一致。构建器会按 Java 插件解析器相同的字符范围校验 ID。

## 共享依赖

以下依赖由 Aquafish 宿主提供，插件构建时强制 external：

```text
vue
vue-router
pinia
axios
@aquafish/components
@aquafish/api-client
@aquafish/ui-shared
```

这样可以避免多个 Vue Runtime、重复 Axios 安全策略和重复组件样式。

## 标准产物

生产模式默认输出：

```text
plugin-root/build/resources/main/ui/
├─ main.js
├─ style.css
└─ ui-manifest.json
```

没有 CSS 时可以不生成 `style.css`，此时 `ui-manifest.json` 中的 `style` 为 `null`。

开发模式默认输出：

```text
plugin-root/ui/build/dist/
```

`ui-manifest.json` 包含：

```json
{
  "schemaVersion": 1,
  "pluginId": "example-plugin",
  "pluginVersion": "1.0.0",
  "format": "iife",
  "globalName": "AqPlugin_example_plugin",
  "entry": "main.js",
  "style": "style.css",
  "externals": []
}
```

实际 `externals` 会写入完整宿主共享依赖列表。

## Gradle 打包顺序

插件 JAR 中的资源位置必须是：

```text
ui/main.js
ui/style.css
ui/ui-manifest.json
```

插件项目应保证 `processResources` 先执行、插件 UI 后构建、`jar` 最后打包。可在插件自己的 `build.gradle` 中配置：

```groovy
def pnpmCommand = System.getProperty('os.name')
    .toLowerCase()
    .contains('windows') ? 'pnpm.cmd' : 'pnpm'

tasks.register('buildPluginUi', Exec) {
    dependsOn tasks.named('processResources')
    workingDir layout.projectDirectory.dir('ui')
    commandLine pnpmCommand, 'build'
}

tasks.named('jar') {
    dependsOn tasks.named('buildPluginUi')
}
```

这样 `buildPluginUi` 会在 Java 资源处理完成后，把标准产物写入 `build/resources/main/ui`，随后 `jar` 将其放入插件 JAR 根目录的 `ui/`。

## aqadmin 宿主加载流程

1. PF4J 插件必须处于 `STARTED` 状态。
2. 后端读取插件目录或 JAR 中的 `ui/ui-manifest.json`。
3. 后端校验清单版本、插件 ID、插件版本、IIFE 全局名、固定入口和共享依赖。
4. 后端从 `plugin_permissions` 读取已经批准的能力；读取失败时按空授权处理。
5. aqadmin 通过同源 `/api/admin/plugins/{pluginId}/ui/` 加载固定脚本和样式。
6. aqadmin 校验 `definePlugin` 名称和权限，再注册扩展点与插件路由。
7. 插件停用、移除或版本变化后，宿主卸载组件、动态路由、脚本、样式和插件全局。
8. 单个插件或单个扩展工厂失败只进入插件 UI 隔离报告，不阻断其他插件和核心后台。

宿主拒绝清单提供远程 URL，也不会把插件目录映射成可任意读取的静态目录。资源响应包含 `nosniff` 与同源资源策略，并限制清单和静态文件大小。

## 扩展点和插件路由

仪表盘组件示例：

```ts
import { definePlugin } from '@aquafish/ui-shared'

import DashboardWidget from './views/DashboardWidget.vue'

export default definePlugin({
  name: 'example-plugin',
  applications: ['aqadmin'],
  extensionPoints: [
    {
      id: 'dashboard-widget',
      point: 'aqadmin:dashboard:widgets:create',
      order: 100,
      create: () => DashboardWidget,
    },
  ],
})
```

插件路由必须满足：

```text
path：plugins/<pluginId>/...
name：plugin.<pluginId>....
```

例如：

```ts
routes: [
  {
    path: 'plugins/example-plugin/settings',
    name: 'plugin.example-plugin.settings',
    component: PluginSettings,
  },
]
```

当前不允许插件路由使用绝对路径、`..`、`children`、`redirect` 或 `alias`，避免覆盖 aqadmin 核心路由。

## 当前边界

当前已经实现：

- `plugin.yaml` 安全读取、大小限制和插件 ID 校验。
- Vue + TypeScript 插件入口构建。
- 共享运行时强制 external。
- 固定 IIFE 入口名称和全局插件名称。
- 生产产物进入插件 Gradle 资源目录。
- 真实执行 IIFE 并验证宿主全局导出。
- 已启动插件的目录型和 JAR 型 UI 资源读取。
- 清单一致性、资源大小、目录穿越和符号链接边界校验。
- aqadmin 共享运行时全局注入。
- 同源脚本与样式动态加载、增量卸载和失败隔离。
- 插件级与扩展点级授权过滤。
- 受控动态路由和仪表盘扩展出口。

当前尚未实现：

- 插件安装包签名、来源信任链和市场安装流程。
- 插件能力申请同步、审批和撤销管理界面。
- 除仪表盘之外其他扩展点的页面出口。
- `user` 用户中心插件 UI 加载器。
- iframe 或 Worker 形式的不可信插件沙箱。

插件 UI 与 aqadmin 运行在同一 JavaScript Realm。能力校验控制的是宿主扩展注册，不可能阻止同 Realm 内恶意脚本直接访问浏览器 API；因此当前只允许加载已安装、已启动并受信任的本机插件，远程脚本地址始终禁止。

## 验证命令

```bat
cd /d H:\javaweb\aquafish\admin
pnpm test
pnpm build
```
