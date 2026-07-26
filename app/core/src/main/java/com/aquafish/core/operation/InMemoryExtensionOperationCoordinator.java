package com.aquafish.core.operation;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * standalone 模式下的 JVM 内存扩展操作协调器。
 *
 * <p>
 * 所有协调器实例共享同一个静态操作映射，
 * 因此同一个 JVM 中即使创建了多个 Service 实例，
 * 相同操作键仍然只能被一个线程持有。
 * </p>
 *
 * <p>
 * 当前实现不创建任何 .lock 文件。
 * 如果未来支持真正的多节点 cluster 模式，
 * 可以增加数据库租约或 Redis 实现，
 * 业务服务无需修改。
 * </p>
 */
@Component
public class InMemoryExtensionOperationCoordinator
    implements ExtensionOperationCoordinator {

    /**
     * 合法操作键格式。
     */
    private static final Pattern
        OPERATION_KEY_PATTERN =
            Pattern.compile(
                "^[a-z][a-z0-9:._-]{0,127}$"
            );

    /**
     * 同 JVM 共享操作互斥表。
     *
     * <p>
     * 操作键来源于受控主题和插件 ID，
     * 不接受任意用户文本，因此键数量有限。
     * </p>
     */
    private static final ConcurrentMap<
        String,
        ReentrantLock
    > OPERATION_LOCKS =
        new ConcurrentHashMap<>();

    /**
     * 非阻塞尝试取得操作协调权。
     *
     * @param operationKey 操作键
     * @return 成功时返回操作句柄
     */
    @Override
    public Optional<ExtensionOperationHandle>
        tryAcquire(
            String operationKey
        ) {

        String normalizedKey =
            normalizeOperationKey(
                operationKey
            );

        ReentrantLock operationLock =
            OPERATION_LOCKS.computeIfAbsent(
                normalizedKey,
                ignored ->
                    new ReentrantLock()
            );

        /*
         * 不允许当前线程重复取得同一个业务操作键。
         * 业务服务出现递归进入时应立即暴露，
         * 而不是依赖 ReentrantLock 的可重入特性。
         */
        if (
            operationLock
                .isHeldByCurrentThread()
        ) {
            return Optional.empty();
        }

        if (!operationLock.tryLock()) {
            return Optional.empty();
        }

        return Optional.of(
            new ExtensionOperationHandle(
                normalizedKey,
                operationLock
            )
        );
    }

    /**
     * 标准化并校验操作键。
     */
    private String normalizeOperationKey(
        String operationKey
    ) {
        if (
            operationKey == null
            || operationKey.isBlank()
        ) {
            throw new IllegalArgumentException(
                "扩展操作键不能为空。"
            );
        }

        String normalizedKey =
            operationKey
                .trim()
                .toLowerCase(Locale.ROOT);

        if (
            !OPERATION_KEY_PATTERN
                .matcher(normalizedKey)
                .matches()
        ) {
            throw new IllegalArgumentException(
                "非法扩展操作键："
                    + normalizedKey
            );
        }

        return normalizedKey;
    }
}
