package com.aquafish.core.operation;

import java.util.Optional;

/**
 * Aquafish 扩展操作协调器。
 *
 * <p>
 * 用于协调主题、插件等扩展资源的安装、升级、
 * 删除、启用和回滚操作。
 * </p>
 *
 * <p>
 * 本接口只负责正常业务请求之间的并发协调，
 * 不作为身份认证、安全授权或防篡改边界。
 * </p>
 */
public interface ExtensionOperationCoordinator {

    /**
     * 非阻塞尝试取得指定操作键。
     *
     * @param operationKey 操作键
     * @return 成功时返回操作句柄；繁忙时返回空
     */
    Optional<ExtensionOperationHandle>
        tryAcquire(
            String operationKey
        );
}
