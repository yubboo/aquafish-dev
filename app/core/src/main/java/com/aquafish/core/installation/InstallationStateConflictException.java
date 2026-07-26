package com.aquafish.core.installation;

/**
 * 数据库安装状态与当前操作不匹配。
 *
 * <p>
 * 常见情况：
 * </p>
 *
 * <ul>
 *     <li>错误的初始化尝试 ID 尝试完成安装；</li>
 *     <li>FAILED 状态再次被旧请求标记；</li>
 *     <li>数据库状态已被其他请求推进；</li>
 *     <li>系统已经安装却再次执行初始化。</li>
 * </ul>
 */
public class InstallationStateConflictException
    extends IllegalStateException {

    /**
     * 创建状态冲突异常。
     *
     * @param message 安全错误说明
     */
    public InstallationStateConflictException(
        String message
    ) {
        super(message);
    }

    /**
     * 创建带原因的状态冲突异常。
     */
    public InstallationStateConflictException(
        String message,
        Throwable cause
    ) {
        super(message, cause);
    }
}
