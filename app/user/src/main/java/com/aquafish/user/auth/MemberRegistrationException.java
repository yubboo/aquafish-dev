package com.aquafish.user.auth;

/**
 * 用户自主注册的可预期业务异常。
 *
 * <p>异常码用于前端区分表单错误、账号冲突与系统初始化问题；异常消息不得包含
 * SQL、表名、密码哈希或数据库驱动细节。</p>
 */
public class MemberRegistrationException extends IllegalStateException {

    private final String code;
    private final boolean conflict;

    public MemberRegistrationException(
        String code,
        String message,
        boolean conflict
    ) {
        super(message);
        this.code = code;
        this.conflict = conflict;
    }

    public String code() {
        return code;
    }

    public boolean conflict() {
        return conflict;
    }
}
