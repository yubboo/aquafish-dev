# boot

## 模块定位

应用启动与模块组装，提供 Spring Boot 主程序和运行时依赖。

## 当前工程信息

- 项目相对路径：`app/boot`
- 主源码 Java 文件：5 个
- 测试 Java 文件：2 个
- Gradle 项目依赖：`:admin`、`:ai`、`:common`、`:content`、`:core`、`:forum`、`:license`、`:market`、`:plugin`、`:search`、`:setup`、`:template`、`:theme`、`:user`

## 模块边界

- 只能承担本模块职责，不把其他领域实现集中进来。
- 数据库在线访问统一使用 R2DBC。
- 数据库结构变化通过模块迁移资源声明。
- 不允许新增 Flyway 运行时或在线 JDBC 热路径。
- 路径、表前缀、迁移文件和版本必须通过配置、注册表或真实目录扫描取得。

## 验证

模块修改后先运行 `:boot:compileJava`、`:boot:compileTestJava` 和 `:boot:test`，阶段收口前运行全工程 `clean test`。
