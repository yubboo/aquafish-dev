## 产品介绍

Aquafish 是面向内容、社区与 AI 场景的可扩展平台，将博客、CMS、强论坛、
用户中心、主题系统和管理后台统一在一个应用中。

## 主要能力

- 内容管理：文章、页面、分类与标签；
- 社区论坛：板块、主题、帖子、回复和审核；
- 主题系统：主题安装、继承、模板渲染和资源管理；
- 用户系统：管理账号、普通用户、角色与权限；
- AI 能力：为写作、审核、搜索和站点运营预留统一能力层；
- 一体化部署：管理端静态资源与 Java 服务打包进同一镜像。

## 部署说明

- 容器内服务端口为 `8520`；
- 持久化数据保存在安装目录的 `data/`；
- 支持由 1Panel 管理的 MySQL、MariaDB 或 PostgreSQL；
- 安装完成后可在 1Panel 创建网站并反向代理到 Aquafish；
- 首次访问 `/setup` 完成站点和超级管理员初始化。

## 相关链接

- 源码仓库：https://github.com/yubboo/aquafish-dev
- 容器镜像：https://github.com/users/yubboo/packages/container/package/aquafish
