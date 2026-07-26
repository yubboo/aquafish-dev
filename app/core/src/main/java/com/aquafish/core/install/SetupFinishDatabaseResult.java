package com.aquafish.core.install;

import java.time.Instant;

/**
 * 安装最终事务的数据库提交结果。
 */
public record SetupFinishDatabaseResult(
    long userId,
    String username,
    Instant installedAt,
    String installedVersion,
    boolean alreadyInstalled
) {
}
