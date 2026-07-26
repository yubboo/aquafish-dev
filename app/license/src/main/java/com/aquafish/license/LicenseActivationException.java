package com.aquafish.license;

/**
 * 授权码无法激活时抛出的可展示业务异常。
 */
public final class LicenseActivationException extends RuntimeException {

    private final String code;

    /**
     * @param code 返回前端的稳定授权错误码
     * @param message 可安全展示的失败原因
     */
    public LicenseActivationException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** 返回不依赖异常文案解析的稳定业务错误码。 */
    public String code() {
        return code;
    }
}
