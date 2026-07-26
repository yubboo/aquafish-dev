# 论坛模块

## 模块定位

强论坛领域模块。第一版领域边界已经冻结，论坛基础迁移、板块模型、权限节点和板块管理服务已经写入；自动化验证已通过，等待用户确认后进入主题业务。

## 当前工程信息

- 项目相对路径：`app/forum`
- 主源码 Java 文件：13 个
- 测试 Java 文件：4 个
- Gradle 项目依赖：`:common`、`:core`
- 迁移资源：MySQL/MariaDB 共用目录与 PostgreSQL 独立目录各 1 份

## 模块边界

- 只能承担本模块职责，不把其他领域实现集中进来。
- 主题是讨论聚合根，首帖和回复统一存放在帖子楼层表。
- 回复楼层必须在事务中锁定主题后分配，禁止使用“最大楼层加一”。
- 审核动作必须保留不可覆盖的历史记录。
- 通知和搜索通过事务发件箱事件解耦。
- 数据库在线访问统一使用 R2DBC。
- 数据库结构变化通过模块迁移资源声明。
- 不允许新增 Flyway 运行时或在线 JDBC 热路径。
- 路径、表前缀、迁移文件和版本必须通过配置、注册表或真实目录扫描取得。

## 验证

模块修改后先运行 `:forum:compileJava`、`:forum:compileTestJava` 和 `:forum:test`，阶段收口前运行全工程 `clean test`。

2026-07-16 已执行：

- `:forum:compileJava`：`BUILD SUCCESSFUL`；
- `:forum:cleanTest :forum:test`：10 个测试全部通过；
- `clean test`：67 个任务全部执行成功；
- 使用 `%USERPROFILE%\.aquafish\dev\application.yaml` 执行 `:boot:bootRun`：应用在 8080 端口启动，`GET /api/health` 返回 `status=ok`。

以上为自动化验证记录，当前等待用户确认，不提前开始主题发布、楼层回复或页面开发。
