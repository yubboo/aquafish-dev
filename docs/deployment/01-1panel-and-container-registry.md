# 1Panel 与容器镜像发布

Aquafish 采用与 Halo 相同的职责分离方式：

1. GitHub Actions 分别构建开发镜像与正式 Release 镜像；
2. 1Panel 应用包只保存应用信息、安装表单和 Compose；
3. 1Panel 安装应用时从镜像仓库拉取指定版本；
4. 用户数据挂载到应用安装目录，不写进镜像。

## 镜像通道

`main` 分支每次推送都会发布开发镜像：

```text
ghcr.io/yubboo/aquafish-dev:main
ghcr.io/yubboo/aquafish-dev:sha-<commit>
```

开发镜像用于持续集成和提前验证，不保证配置或数据结构长期兼容，不得用于
1Panel 正式安装。

GitHub Release 发布正式镜像：

```text
ghcr.io/yubboo/aquafish:<version>
```

例如：

```bash
docker pull ghcr.io/yubboo/aquafish:0.0.1
```

发布工作流位于：

```text
.github/workflows/publish-image.yml
```

## 发布正式版本

确认 `main` 的 CI 与开发镜像均已通过后，在 GitHub 仓库中进入
`Releases → Draft a new release`，选择或创建符合以下格式的标签：

```text
v<major>.<minor>.<patch>
```

例如发布 `v0.0.1`。只有点击 `Publish release` 才会触发正式镜像工作流；
单独推送 Git 标签不会发布正式镜像。

正式发布工作流会执行以下操作：

- 构建 `linux/amd64` 和 `linux/arm64`；
- 推送 `ghcr.io/yubboo/aquafish:0.0.1`；
- 同时更新 `ghcr.io/yubboo/aquafish:0.0`、`0` 和 `latest`；
- 为镜像生成 provenance 和 SBOM。

首次推送 `main` 后，在 GitHub Packages 中把 `aquafish-dev` 确认为公开包；
首次发布 Release 后，把 `aquafish` 确认为公开包。只有公开镜像才能让未登录
GitHub 的 1Panel 服务器直接拉取。

## 构建 1Panel 本地应用包

在仓库根目录运行 `p.bat` 并选择：

```text
7. 一键生成 1Panel 应用包
```

也可以直接执行：

```bat
p.bat --package-1panel
```

脚本自动选择 `packaging/1panel/aquafish/` 中最高的语义化版本目录。产物与
正式模板源码统一放在 `packaging/1panel/`：

```text
packaging/1panel/aquafish-0.0.1-1panel.zip
```

ZIP 内部路径统一使用 `/`，可以在 Linux 和 1Panel 中正确解压为目录，不能使用
Windows 反斜杠形式的条目名。

包内结构：

```text
aquafish/
├─ data.yml
├─ logo.png
├─ README.md
├─ README_en.md
└─ 0.0.1/
   ├─ data.yml
   └─ docker-compose.yml
```

## 安装到 1Panel

把 ZIP 上传到服务器并解压到：

```text
/opt/1panel/resource/apps/local/
```

最终路径必须是：

```text
/opt/1panel/resource/apps/local/aquafish/data.yml
```

然后在 1Panel 执行：

1. 应用商店 → 本地应用；
2. 同步本地应用；
3. 选择 Aquafish；
4. 选择数据库、填写外部访问地址和端口；
5. 安装并等待容器健康；
6. 访问 `http://服务器地址:端口/setup` 完成初始化。

正式绑定域名时，在 1Panel 创建反向代理网站：

```text
https://example.com  →  http://aquafish容器或宿主机端口:8520
```

外部访问地址应填写最终域名，例如 `https://example.com`。发布镜像已经包含
管理端静态资源，因此正式环境不需要单独启动 Vite 的 `18520` 端口。

## 升级版本

发布 `0.0.2` 时：

1. 复制 `packaging/1panel/aquafish/0.0.1` 为 `0.0.2`；
2. 将 Compose 镜像改为 `ghcr.io/yubboo/aquafish:0.0.2`；
3. 在 GitHub 发布 `v0.0.2` Release；
4. 确认正式镜像发布成功并可公开拉取；
5. 使用 `p.bat` 菜单 `7` 重新生成并同步 1Panel 应用包。

不要让 1Panel 的正式版本直接使用 `latest`。版本目录应绑定不可变的语义化版本
标签，升级和回滚才有明确边界。
