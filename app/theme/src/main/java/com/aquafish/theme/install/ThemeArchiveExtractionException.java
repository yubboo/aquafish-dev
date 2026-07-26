package com.aquafish.theme.install;

/**
 * 主题临时解压异常。
 *
 * <p>
 * 异常中保留稳定错误代码、失败阶段和临时目录清理状态，
 * 后续 ThemeInstallService 可以直接转换成
 * ThemeInstallResult.failed 或 rejected。
 * </p>
 */
public class ThemeArchiveExtractionException
    extends RuntimeException {

    /**
     * 稳定错误代码。
     */
    private final ThemeInstallErrorCode
        errorCode;

    /**
     * 失败阶段。
     */
    private final ThemeInstallStage stage;

    /**
     * 临时目录是否已经清理。
     */
    private final boolean
        temporaryDirectoryCleaned;

    /**
     * 创建不带原始异常的解压异常。
     *
     * @param errorCode 错误代码
     * @param stage 失败阶段
     * @param message 错误说明
     * @param temporaryDirectoryCleaned 临时目录是否清理
     */
    public ThemeArchiveExtractionException(
        ThemeInstallErrorCode errorCode,
        ThemeInstallStage stage,
        String message,
        boolean temporaryDirectoryCleaned
    ) {
        this(
            errorCode,
            stage,
            message,
            null,
            temporaryDirectoryCleaned
        );
    }

    /**
     * 创建带原始异常的解压异常。
     *
     * @param errorCode 错误代码
     * @param stage 失败阶段
     * @param message 错误说明
     * @param cause 原始异常
     * @param temporaryDirectoryCleaned 临时目录是否清理
     */
    public ThemeArchiveExtractionException(
        ThemeInstallErrorCode errorCode,
        ThemeInstallStage stage,
        String message,
        Throwable cause,
        boolean temporaryDirectoryCleaned
    ) {
        super(
            requireMessage(message),
            cause
        );

        if (errorCode == null) {
            throw new IllegalArgumentException(
                "主题解压错误代码不能为空。"
            );
        }

        if (stage == null) {
            throw new IllegalArgumentException(
                "主题解压失败阶段不能为空。"
            );
        }

        this.errorCode = errorCode;
        this.stage = stage;
        this.temporaryDirectoryCleaned =
            temporaryDirectoryCleaned;
    }

    /**
     * 获取错误代码。
     *
     * @return 错误代码
     */
    public ThemeInstallErrorCode errorCode() {
        return errorCode;
    }

    /**
     * 获取失败阶段。
     *
     * @return 失败阶段
     */
    public ThemeInstallStage stage() {
        return stage;
    }

    /**
     * 获取临时目录清理状态。
     *
     * @return 已清理时返回 true
     */
    public boolean temporaryDirectoryCleaned() {
        return temporaryDirectoryCleaned;
    }

    /**
     * 验证错误说明。
     */
    private static String requireMessage(
        String message
    ) {
        if (
            message == null
                || message.isBlank()
        ) {
            throw new IllegalArgumentException(
                "主题解压错误说明不能为空。"
            );
        }

        return message.trim();
    }
}
