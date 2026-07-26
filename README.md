# Aquafish

Aquafish 是面向内容、社区与 AI 场景的可扩展平台，采用与 Halo 相近的模块化单体和单仓库组织方式。Java 服务端、Vue 管理端、主题引擎与插件运行时在同一仓库中协同开发，并可发布为一个包含管理端静态资源的可执行应用。

## 仓库组成

| 目录 | 职责 |
| --- | --- |
| `app/` | Java 21、Spring Boot、WebFlux 与 R2DBC 服务端 |
| `admin/` | Vue 3、TypeScript、Vite 管理端与安装向导 |
| `app/theme/src/main/theme/` | 随核心维护并在构建时打包的内置主题 |
| `docs/` | 当前架构、开发、安装和验收文档 |
| `scripts/` | 仓库通用的构建验证与容器入口脚本 |

`backups/`、`deploy/`、本机 AI 工具配置、运行数据和历史实施脚本仅用于本地开发，不进入源码仓库。

## 技术栈

### Server

- Java 21
- Spring Boot 4.1
- Spring WebFlux、Reactor、Netty
- Gradle 9 多模块
- R2DBC 与响应式事务
- MySQL、MariaDB、PostgreSQL
- PF4J 插件运行时
- Thymeleaf、Pebble 主题模板引擎

### Admin

- Vue 3
- TypeScript
- Vite
- Pinia、Vue Router
- Element Plus、Tailwind CSS
- Vitest

## 服务端模块

```text
app/
├─ boot/       # 应用启动与发行包
├─ core/       # 数据库、安装状态、配置等核心底座
├─ common/     # 通用模型与工具
├─ setup/      # 首次安装
├─ theme/      # 主题扫描、安装、继承与资源
├─ template/   # 模板解析、引擎调度与回退
├─ plugin/     # PF4J 插件运行时与生命周期
├─ user/       # 用户与认证
├─ content/    # CMS 内容
├─ forum/      # 社区与论坛
├─ search/     # 搜索能力
├─ ai/         # AI 能力
├─ market/     # 扩展市场
├─ license/    # 平台授权
└─ admin/      # 管理端 API
```

完整模块声明见 [`app/settings.gradle`](app/settings.gradle)。

## 本地开发

环境要求：

- JDK 21
- Node.js 22
- pnpm 11

启动管理端：

```powershell
cd admin
pnpm install --frozen-lockfile
pnpm dev
```

启动服务端：

```powershell
cd app
.\gradlew.bat :boot:bootRun
```

源码开发入口：

- 前台与 API（Spring Boot）：`http://localhost:8520/site`
- 管理端（Vite）：`http://localhost:18520/admin`
- 首次安装（Vite）：`http://localhost:18520/setup`

发行 JAR 或 Docker 镜像已经内置管理端静态资源，统一使用 Spring Boot 的
`8520` 端口访问：前台 `/site`、管理端 `/admin`、首次安装 `/setup`。

## 验证

Windows 可以在仓库根目录运行：

```powershell
.\scripts\verify-build.bat
```

也可以分别执行：

```powershell
cd admin
pnpm test
pnpm build

cd ..\app
.\gradlew.bat test --no-daemon
```

## 发行方式

管理端保持独立源码与构建流程。发行时可通过 Gradle 参数把已经构建的 `admin/dist` 嵌入 Spring Boot JAR：

```powershell
cd admin
pnpm build

cd ..\app
.\gradlew.bat :boot:bootJar `
  -PincludeAdminDist=true `
  -PaquafishReleaseBuild=true
```

也可以独立部署 `admin/dist`，并将同源 `/api` 反向代理到 Java 服务。

容器镜像从仓库根目录构建：

```powershell
docker build -t aquafish:local .
```

## 当前状态

目前已经建立多数据库安装底座、主题安装与继承、双模板引擎、模板回退链、PF4J 插件加载与生命周期、CMS/BBS 基础接口和管理端页面。

下一阶段重点是稳定第三方扩展契约：

- 独立且可版本化的插件 SDK
- Hook、Filter、Event 与模板插槽
- 插件权限和能力清单
- 插件管理端菜单、路由与设置扩展
- Aquafish 安全模板语法
- 模板编译缓存与插件包签名

项目状态与验证方式见 [`docs/current`](docs/current)。
