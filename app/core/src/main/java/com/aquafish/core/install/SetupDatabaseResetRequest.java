package com.aquafish.core.install;

import com.aquafish.core.database.DatabaseSettings;

/**
 * 安装向导危险重装请求。
 *
 * <p>后端会重新检测真实数据库状态。前端传入的 expectedMode
 * 只用于防止用户确认期间状态发生变化，不能替代服务器判断。</p>
 */
public record SetupDatabaseResetRequest(
    DatabaseSettings database,
    SetupDatabaseMode expectedMode,
    Boolean dataLossConfirmed,
    String confirmationText
) {

    /**
     * 规范化危险重装请求。
     */
    public SetupDatabaseResetRequest normalized() {
        return new SetupDatabaseResetRequest(
            database == null
                ? null
                : database.normalized(),
            expectedMode,
            Boolean.TRUE.equals(
                dataLossConfirmed
            ),
            confirmationText == null
                ? ""
                : confirmationText.trim()
        );
    }
}
