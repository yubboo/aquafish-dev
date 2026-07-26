package com.aquafish.common.web;

/**
 * Aquafish 统一 API 返回结构。
 *
 * 当前阶段：
 * Step 17 用户系统基础设施开发。
 *
 * 设计目的：
 * 1. 统一所有 /api/** 接口的响应格式。
 * 2. 前端可以用同一套逻辑判断接口是否成功。
 * 3. 后端不同模块不用各自发明返回格式。
 * 4. 后续错误码、权限错误、参数错误、登录失效都可以统一处理。
 *
 * 推荐返回格式：
 *
 * {
 *   "success": true,
 *   "code": "OK",
 *   "message": "操作成功",
 *   "data": {}
 * }
 *
 * 字段说明：
 * success：接口是否成功。
 * code：业务状态码，不等同于 HTTP 状态码。
 * message：给前端展示或调试用的提示信息。
 * data：真正的业务数据。
 *
 * 注意：
 * 当前类只负责统一返回结构。
 * 不负责异常处理。
 * 后续可以继续新增 GlobalExceptionHandler 统一处理异常。
 */
public record ApiResult<T>(
    boolean success,
    String code,
    String message,
    T data
) {

    /**
     * 成功返回，使用默认成功文案。
     *
     * 示例：
     * ApiResult.ok(userList)
     */
    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(
            true,
            "OK",
            "操作成功",
            data
        );
    }

    /**
     * 成功返回，自定义成功文案。
     *
     * 示例：
     * ApiResult.ok(userList, "用户列表获取成功")
     */
    public static <T> ApiResult<T> ok(T data, String message) {
        return new ApiResult<>(
            true,
            "OK",
            message,
            data
        );
    }

    /**
     * 失败返回，不携带业务数据。
     *
     * 示例：
     * ApiResult.fail("USER_NOT_FOUND", "用户不存在")
     */
    public static <T> ApiResult<T> fail(String code, String message) {
        return new ApiResult<>(
            false,
            code,
            message,
            null
        );
    }

    /**
     * 失败返回，携带业务数据。
     *
     * 这个方法主要预留给表单校验错误等场景。
     *
     * 示例：
     * ApiResult.fail("VALIDATION_ERROR", "参数错误", errors)
     */
    public static <T> ApiResult<T> fail(String code, String message, T data) {
        return new ApiResult<>(
            false,
            code,
            message,
            data
        );
    }
}