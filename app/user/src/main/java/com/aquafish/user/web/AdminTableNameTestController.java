package com.aquafish.user.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.core.database.TableNameResolver;
import com.aquafish.core.database.TableNames;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开发环境表名解析诊断接口。
 *
 * <p>该接口只用于确认表前缀和逻辑表名映射结果，不属于正式后台能力。
 * 生产环境不得注册，后续 Flyway 迁移体系完成后可以直接删除。</p>
 */
@Profile("dev")
@RestController
@RequestMapping("/api/admin/users")
public class AdminTableNameTestController {

    private final TableNameResolver tableNameResolver;

    public AdminTableNameTestController(TableNameResolver tableNameResolver) {
        this.tableNameResolver = tableNameResolver;
    }

    @GetMapping("/table-name-test")
    public ApiResult<TableNameTestResponse> tableNameTest() {
        TableNameTestResponse data = new TableNameTestResponse(
            tableNameResolver.currentPrefix(),
            tableNameResolver.tableName(TableNames.USERS),
            tableNameResolver.tableName(TableNames.USER_LOGIN_LOGS),
            tableNameResolver.tableName(TableNames.ROLES),
            tableNameResolver.tableName(TableNames.PERMISSIONS),
            tableNameResolver.tableName(TableNames.USER_ROLES),
            tableNameResolver.tableName(TableNames.USER_GROUPS),
            "仅用于 dev Profile 下验证 TableNameResolver。"
        );

        return ApiResult.ok(data, "表名解析测试成功");
    }

    public record TableNameTestResponse(
        String tablePrefix,
        String usersTable,
        String userLoginLogsTable,
        String rolesTable,
        String permissionsTable,
        String userRolesTable,
        String userGroupsTable,
        String note
    ) {
    }
}
