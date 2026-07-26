# license 模块

## 模块定位

`app/license` 负责 Aquafish 系统平台许可证的设备识别、授权码验签、本地激活状态和服务端访问拦截。

当前采用“本地 Ed25519 验签 + 独立授权中心”的混合授权：客户可输入授权中心生成的
`AQO1` 短码在线绑定设备，也可把实例 ID 发给发行方取得设备专用 `AQF1`。两种方式最终
都在本机验签；启用在线中心后还会异步查询暂停、解绑、吊销和到期状态，但普通业务请求
绝不会等待网络。

## 安全边界

- 产品只包含公钥，只能验证授权码，不能自行签发授权码。
- 私钥只允许保存在发行方环境，不能提交 Git、打入 JAR、ZIP 或 Docker 镜像。
- 授权码保存在 `workdir/licenses/platform.license`，实例 ID 保存在 `workdir/instance.id`。
- 原始授权码不会通过状态 API 返回给浏览器。
- 在线状态缓存只保存完整 AQL1 签名租约，不保存 AQF1、管理员令牌或私钥；读取时必须
  重新验签，旧版明文 ACTIVE 缓存和任何人工修改都会被拒绝。
- 在线中心只加载独立在线签名密钥，绝不能加载离线根密钥 `.aqvault`；两把密钥用途分离。
- 整个平台未授权时仍允许登录、控制台、用户查看和基础系统设置；内容、主题、插件、论坛、
  市场、AI、搜索、更新等高级 API 返回 HTTP 423。平台有效但缺少指定模块时返回 HTTP 403
  和 `LICENSE_FEATURE_REQUIRED`。前端路由与菜单只负责引导，不作为安全边界。
- 安装接口、后台登录、健康检查和授权管理接口始终保留，避免系统进入无法激活的死锁状态。

## 后台接口

- `GET /api/admin/license/status`：读取实时授权状态和设备码。
- `POST /api/admin/license/activation`：验签成功后原子保存授权码。
- `POST /api/admin/license/online/activation`：把 AQO1 和后端读取的真实设备码提交独立中心，
  对中心返回的 AQF1 再次本地验签后原子保存。
- `DELETE /api/admin/license/activation`：删除本机授权文件并取消激活。
- `POST /api/admin/license/online/refresh`：主动等待一次有超时限制的在线状态复核。

接口受后台登录与 CSRF 保护。除上述豁免接口外，正式环境的 `/api/**` 业务接口必须拥有有效授权。

## 模块授权

`LicenseFeature.java` 是授权功能项与 API 路径的后端唯一映射。当前可控制：

| 功能代码 | 功能 | 受保护 API 前缀示例 |
| --- | --- | --- |
| `forum` | 论坛管理 | `/api/admin/forum/**`、`/api/forum/**` |
| `content` | 内容管理 | `/api/admin/content/**`、`/api/content/**` |
| `theme` | 主题管理 | `/api/admin/theme/**`、`/api/admin/themes/**` |
| `plugin` | 插件管理 | `/api/admin/plugin/**`、`/api/admin/plugins/**` |
| `market` | 应用市场 | `/api/admin/market/**`、`/api/market/**` |
| `ai` | AI 能力 | `/api/admin/ai/**`、`/api/ai/**` |
| `search` | 站内搜索 | `/api/admin/search/**`、`/api/search/**` |
| `updates` | 更新服务 | `/api/admin/license/updates/**` |

早期 `cms` 功能代码作为兼容总包，同时授予 `content` 与 `theme`，不会授予 plugin、
market 或其他模块。`platform` 表示基础平台权益；有效 AQF1 授权仍是所有模块判断的前提。

前端对应关系：

- `admin/src/config/license-features.ts`：页面路径与功能代码映射；
- `admin/src/config/admin-menu.ts`：按当前授权隐藏未购买模块；
- `admin/src/router/license-status-guard.ts`：直接输入 URL 时进入授权不足页；
- `admin/src/api/admin-fetch-guard.ts`：业务请求收到后端 403 时统一进入授权不足页；
- `LicenseFeatureRequiredPage.vue`：展示所需模块并提供升级、复核入口。

## AQF1 授权内容

授权码签名载荷包括：结构版本、授权编号、产品、版本、客户名称、实例 ID、签发时间、
生效时间、到期时间、模块功能项和具体资源权益。修改任意内容都会导致 Ed25519 验签失败。

资源权益使用 `type + id`，例如 `theme:official-default`。`LicenseService.isAssetUsable(...)`
必须先确认平台授权和 `theme` 模块，再确认具体主题权益，因此后续官方主题可以实现
“系统平台授权 + 主题商品授权”的双授权，不能只伪造一个主题字段绕过平台授权。

## 在线校验、解绑与远程吊销

在线能力默认关闭，不影响原有 AQF1 离线授权。正式客户实例按环境变量启用：

```text
AQUAFISH_LICENSE_ONLINE_ENABLED=true
AQUAFISH_LICENSE_ONLINE_URL=https://license.example.com/api/v1/licenses/check
AQUAFISH_LICENSE_ONLINE_ACTIVATION_URL=https://license.example.com/api/v1/activations
AQUAFISH_LICENSE_PORTAL_URL=https://license.example.com/portal
AQUAFISH_LICENSE_ONLINE_REFRESH_SECONDS=3600
AQUAFISH_LICENSE_OFFLINE_GRACE_SECONDS=2592000
```

行为规则：

1. 每个 API 请求仍先在本机完整验签；在线查询只在后台异步执行。
2. 首次启用但尚未取得签名租约时不放行高级能力，避免删除缓存/重启无限套取首次宽限；
   成功取得 ACTIVE 租约后，中心故障时可用到签名截止时间，默认最长三十天。
3. 在线中心明确返回 `SUSPENDED`、`REVOKED` 或 `UNBOUND` 后立即停止高级功能放行，
   不再使用宽限期；`SUSPENDED` 可由发行方恢复，`REVOKED` 永久不可逆。
4. 最近成功的完整 AQL1 签名租约原子保存在 `workdir/licenses/online-status.json`，
   每次读取重新验签，切换授权时自动重置。
5. 管理员可在授权页看到脱敏在线状态并点击“重新校验”，该操作受登录和 CSRF 保护。
6. 每次请求包含安全随机 nonce；响应必须匹配 nonce、授权编号、设备码和 keyId，并拒绝
   rowVersion 更小的旧状态。正式 URL 必须 HTTPS，只有回环地址联调允许 HTTP。
7. `AQUAFISH_LICENSE_ONLINE_PREVIOUS_KEY_ID/PUBLIC_KEY` 可在轮换窗口信任上一把在线公钥；
   等旧发行版升级且旧租约过期后再移除。

在线查询失败不会把客户请求线程挂在网络上，也不会把临时故障误写成吊销；宽限期结束后
状态变为 `ONLINE_CHECK_REQUIRED`。客户删除本地缓存不能伪造有效签名，下一次查询仍会
从在线中心恢复真实吊销/解绑状态。

## 发行方签发工具

### 可视化本地签发中心

开发者电脑可以从项目根目录运行 `p.bat`，选择菜单 `16`。也可以直接执行：

```bat
node docs\cjs\aquafish_license_center.cjs
```

启动流程：

1. 在终端隐藏输入 `.aqvault` 主密码；
2. 服务只监听 `127.0.0.1:5199`，不会监听局域网地址；
3. 浏览器打开后输入终端显示的本次随机访问码；
4. 填写客户名称和设备码，选择版本、期限、生效时间及模块权益；
5. 签发结果保存到 `%USERPROFILE%\.aquafish\authority\issued`；
6. 脱敏审计写入 `%USERPROFILE%\.aquafish\authority\issuer-audit.jsonl`；
7. 点击“锁定并退出”或空闲十五分钟后，服务停止并释放私钥会话。

签发中心现在还会维护脱敏授权登记簿，可设置每份授权的最大解绑次数，并在授权列表中
执行解绑和不可逆吊销。使用 `p.bat` 菜单 `17` 启动本机联调在线中心后，再选择 `16`，
本次菜单会在内存中自动传递随机管理员令牌，签发、解绑和吊销会同步到联调中心；令牌
不会写文件或显示在终端。

浏览器不会接触仓库主密码。完整授权码只出现在本次签发结果和授权文件中，审计日志
只保存授权编号、业务摘要和授权码 SHA-256。

### 命令行兼容工具

现有明文私钥应先迁移为带主密码的 `.aqvault` 加密仓库：

```bat
node docs\cjs\aquafish_license_issuer.cjs vault-import ^
  --private-key "%USERPROFILE%\.aquafish\authority\aquafish-private-key.pem" ^
  --key-vault "%USERPROFILE%\.aquafish\authority\aquafish-signing-key.aqvault" ^
  --public-key app\license\src\main\resources\aquafish-license-public-key.txt
```

新建项目可以直接生成加密密钥仓库：

```bat
node docs\cjs\aquafish_license_issuer.cjs vault-keygen ^
  --key-vault "%USERPROFILE%\.aquafish\authority\aquafish-signing-key.aqvault" ^
  --public-key app\license\src\main\resources\aquafish-license-public-key.txt
```

使用加密仓库签发一年期授权：

```bat
node docs\cjs\aquafish_license_issuer.cjs issue ^
  --key-vault "%USERPROFILE%\.aquafish\authority\aquafish-signing-key.aqvault" ^
  --instance-id 后台显示的设备码 ^
  --customer 客户名称 ^
  --edition professional ^
  --days 365 ^
  --features cms,forum,market ^
  --out "%USERPROFILE%\.aquafish\authority\customer-license.txt"
```

`--days 0` 表示永久授权。主密码只允许在交互式终端输入，不得写入命令参数。
加密仓库仍必须另外保存至少两个可解锁备份；主密码与仓库分开保管。源码进度 ZIP
按安全规则不会包含 `workdir`、明文私钥或 `.aqvault`。

迁移、备份和恢复说明由独立授权中心项目维护，本仓库只保留客户程序需要的
授权协议、验证逻辑和测试。

## 独立授权中心

正式授权中心位于独立私有项目，拥有自己的后端、前端、
数据库迁移和部署配置，不会打入 Aquafish 客户生产包。旧的 `docs/cjs` 工具仅保留为
历史兼容/离线恢复工具，不再作为正式在线中心。主要接口：

- `POST /api/v1/licenses/check`：客户实例公开查询最小状态；
- `POST /api/v1/activations`：使用 AQO1 在线绑定设备并取得一次 AQF1；
- `GET /api/v1/admin/licenses`：读取管理登记；
- `POST /api/v1/admin/licenses`：创建带模块和资源权益的授权；
- `POST /api/v1/admin/licenses/{licenseId}/offline-code`：按客户设备码签发离线 AQF1；
- `POST /api/v1/admin/licenses/{licenseId}/activation-code/rotate`：轮换泄露激活码；
- `POST /api/v1/admin/licenses/{licenseId}/suspend`：临时暂停；
- `POST /api/v1/admin/licenses/{licenseId}/restore`：恢复临时暂停；
- `POST /api/v1/admin/licenses/import`：导入本地签发中心的脱敏记录；
- `POST /api/v1/admin/licenses/{licenseId}/unbind`：解绑设备并扣减次数；
- `POST /api/v1/admin/licenses/{licenseId}/revoke`：不可逆吊销。

管理员接口使用至少 32 字符的 Bearer Token。公网监听必须显式声明位于 HTTPS 反向代理
之后，还应在代理/WAF 配置公网限速、日志保护和访问控制。该服务属于开发者运维资产，
不会进入客户生产 ZIP、JAR 或 Docker 镜像。

## 当前限制与下一版

当前已实现在线/离线激活、模块强制控制、具体资源权益、解绑次数、激活码轮换、暂停/恢复、
远程吊销、AQL1 签名在线租约、nonce 防重放、双 keyId 轮换窗口和停机租约。尚未实现
人员账号/MFA、客户自助、订单与支付、数据库高可用、强可信时间源和 KMS/HSM。公开商用前应继续完成身份、
告警、灾备和独立安全审计；在线进程只能加载用途隔离的服务密钥，不能加载离线根密钥。

## 验证

```bat
cd app
gradlew.bat :license:test
```

```bat
node --test docs\cjs\aquafish_license_registry.test.cjs ^
  docs\cjs\aquafish_license_authority_server.test.cjs ^
  docs\cjs\aquafish_license_center.test.cjs
```
