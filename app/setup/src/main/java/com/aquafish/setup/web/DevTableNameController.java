package com.aquafish.setup.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.core.database.TableNameResolver;
import com.aquafish.core.database.TableNames;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开发阶段表名解析调试接口。
 *
 * 当前阶段：
 * Step 17-19-3：验证 TableNameResolver 是否已经改为读取 workdir/application.yaml。
 *
 * 当前验证内容：
 * 1. 当前表前缀是否来自 aquafish.database.table-prefix；
 * 2. 逻辑表名 users 是否能解析成 aq_users；
 * 3. TableNames 常量是否能配合 TableNameResolver 使用；
 * 4. 后续业务代码是否可以统一通过 TableNameResolver 获取真实表名。
 *
 * 注意：
 * 这个 Controller 是开发阶段调试接口。
 * 正式发布前可以删除，或者移动到后台系统诊断页面。
 */
@RestController
@Profile("dev")
public class DevTableNameController {

    /**
     * 表名解析工具。
     *
     * 当前这个 Bean 来自：
     * app/core/src/main/java/com/aquafish/core/database/TableNameResolver.java
     */
    private final TableNameResolver tableNameResolver;

    /**
     * 构造方法注入。
     *
     * @param tableNameResolver 表名解析工具
     */
    public DevTableNameController(TableNameResolver tableNameResolver) {
        this.tableNameResolver = tableNameResolver;
    }

    /**
     * 表名解析调试接口。
     *
     * 请求示例：
     * GET /api/dev/table-name?name=users
     *
     * 参数说明：
     * name 是逻辑表名。
     *
     * @param logicalTableName 逻辑表名
     * @return 表名解析结果
     */
    @GetMapping("/api/dev/table-name")
    public ApiResult<DevTableNameResponse> tableName(
        @RequestParam(name = "name", defaultValue = TableNames.USERS) String logicalTableName
    ) {
        String currentPrefix = tableNameResolver.currentPrefix();
        String realTableName = tableNameResolver.tableName(logicalTableName);

        List<DevTableNameExample> examples = List.of(
            new DevTableNameExample(TableNames.USERS, tableNameResolver.tableName(TableNames.USERS)),
            new DevTableNameExample(TableNames.USER_LOGIN_LOGS, tableNameResolver.tableName(TableNames.USER_LOGIN_LOGS)),
            new DevTableNameExample(TableNames.ROLES, tableNameResolver.tableName(TableNames.ROLES)),
            new DevTableNameExample(TableNames.PERMISSIONS, tableNameResolver.tableName(TableNames.PERMISSIONS)),
            new DevTableNameExample(TableNames.USER_ROLES, tableNameResolver.tableName(TableNames.USER_ROLES)),
            new DevTableNameExample(TableNames.ROLE_PERMISSIONS, tableNameResolver.tableName(TableNames.ROLE_PERMISSIONS)),
            new DevTableNameExample(TableNames.USER_GROUPS, tableNameResolver.tableName(TableNames.USER_GROUPS)),
            new DevTableNameExample(TableNames.FORUM_THREADS, tableNameResolver.tableName(TableNames.FORUM_THREADS)),
            new DevTableNameExample(TableNames.CONTENT_ARTICLES, tableNameResolver.tableName(TableNames.CONTENT_ARTICLES)),
            new DevTableNameExample(TableNames.AI_PROVIDERS, tableNameResolver.tableName(TableNames.AI_PROVIDERS)),
            new DevTableNameExample(TableNames.LICENSE_ACTIVATIONS, tableNameResolver.tableName(TableNames.LICENSE_ACTIVATIONS)),
            new DevTableNameExample(TableNames.OPTIONS, tableNameResolver.tableName(TableNames.OPTIONS))
        );

        DevTableNameResponse data = new DevTableNameResponse(
            currentPrefix,
            logicalTableName,
            realTableName,
            examples,
            "表名解析成功。当前表前缀来自 workdir/application.yaml 的 aquafish.database.table-prefix。"
        );

        return ApiResult.ok(data, "表名解析成功");
    }

    /**
     * 表名解析响应结构。
     *
     * @param currentPrefix 当前系统读取到的数据库表前缀
     * @param logicalTableName 请求传入的逻辑表名
     * @param realTableName 最终解析出的真实表名
     * @param examples 常见逻辑表名解析示例
     * @param message 调试说明
     */
    public record DevTableNameResponse(
        String currentPrefix,
        String logicalTableName,
        String realTableName,
        List<DevTableNameExample> examples,
        String message
    ) {
    }

    /**
     * 表名解析示例对象。
     *
     * @param logicalTableName 逻辑表名
     * @param realTableName 真实表名
     */
    public record DevTableNameExample(
        String logicalTableName,
        String realTableName
    ) {
    }
}
