
package com.aquafish.setup;

/**
 * 首次初始化：站点信息、管理员、许可证、演示数据。
 */
public final class SetupModule {

    private SetupModule() {
    }

    /** @return 用于模块注册、日志和诊断输出的稳定模块标识 */
    public static String name() {
        return "setup";
    }
}
