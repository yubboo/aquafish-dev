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
plugin-root/ui/build/dist/
├─ main.js
├─ style.css
└─ ui-manifest.json
```

没有 CSS 时可以不生成 `style.css`，此时 `ui-manifest.json` 中的 `style` 为 `null`。

开发模式默认输出：

```text
plugin-root/build/resources/main/ui/
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

## 当前边界

当前已经实现：

- `plugin.yaml` 安全读取、大小限制和插件 ID 校验。
- Vue + TypeScript 插件入口构建。
- 共享运行时强制 external。
- 固定 IIFE 入口名称和全局插件名称。
- `main.js`、`style.css`、`ui-manifest.json` 产物测试。

当前尚未实现：

- Java 插件 JAR 自动复制和打包 UI 产物。
- aqadmin 插件静态资源控制器。
- 宿主共享依赖全局注入。
- 根据 `ui-manifest.json` 动态加载并注册插件。
- 插件签名、权限和兼容版本的宿主侧最终校验。

在宿主加载器完成前，构建器产物只用于开发和协议验证，不能通过远程 URL 直接注入 aqadmin。

## 验证命令

```bat
cd /d H:\javaweb\aquafish\admin
pnpm test
pnpm build
```
