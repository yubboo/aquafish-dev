# admin

## 模块定位

Java 后台管理 API 与后台适配层；Vue 前端位于项目根目录 admin。

## 当前工程信息

- 项目相对路径：`app/admin`
- 主源码 Java 文件：14 个
- 测试 Java 文件：3 个
- Gradle 项目依赖：`:common`、`:core`

## 模块边界

- 只能承担本模块职责，不把其他领域实现集中进来。
- 数据库在线访问统一使用 R2DBC。
- 数据库结构变化通过模块迁移资源声明。
- 不允许新增 Flyway 运行时或在线 JDBC 热路径。
- 路径、表前缀、迁移文件和版本必须通过配置、注册表或真实目录扫描取得。

## 验证

模块修改后先运行 `:admin:compileJava`、`:admin:compileTestJava` 和 `:admin:test`，阶段收口前运行全工程 `clean test`。
