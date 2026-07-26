package com.aquafish.core.operation;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 已取得的扩展操作协调句柄。
 *
 * <p>
 * 必须通过 try-with-resources 使用，
 * 确保业务方法无论成功、失败或提前返回，
 * 都会释放当前操作键。
 * </p>
 */
public final class ExtensionOperationHandle
    implements AutoCloseable {

    /**
     * 操作键。
     */
    private final String operationKey;

    /**
     * 当前 JVM 内存互斥对象。
     */
    private final ReentrantLock operationLock;

    /**
     * 是否已经关闭。
     */
    private boolean closed;

    /**
     * 创建操作句柄。
     *
     * @param operationKey 操作键
     * @param operationLock 已取得的互斥对象
     */
    ExtensionOperationHandle(
        String operationKey,
        ReentrantLock operationLock
    ) {
        if (
            operationKey == null
            || operationKey.isBlank()
        ) {
            throw new IllegalArgumentException(
                "扩展操作键不能为空。"
            );
        }

        if (operationLock == null) {
            throw new IllegalArgumentException(
                "扩展操作互斥对象不能为空。"
            );
        }

        this.operationKey =
            operationKey;

        this.operationLock =
            operationLock;
    }

    /**
     * 获取当前操作键。
     *
     * @return 操作键
     */
    public String operationKey() {
        return operationKey;
    }

    /**
     * 释放当前操作协调权。
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }

        closed = true;

        if (
            operationLock
                .isHeldByCurrentThread()
        ) {
            operationLock.unlock();
        }
    }
}
