package com.example.admin.common;

/**
 * 统一响应包装。
 */
public record ApiResult<T>(int code, String message, T data) {

    public static <T> ApiResult<T> ok(final T data) {
        return new ApiResult<>(0, "ok", data);
    }

    public static <T> ApiResult<T> fail(final int code, final String message) {
        return new ApiResult<>(code, message, null);
    }
}
