package com.example.admin.web;

import com.example.admin.client.ControlCallException;
import com.example.admin.common.ApiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 管理 API 统一错误响应。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResult<Void>> badRequest(final IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiResult.fail(400, e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResult<Void>> conflict(final IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResult.fail(409, e.getMessage()));
    }

    @ExceptionHandler(ControlCallException.class)
    public ResponseEntity<ApiResult<Void>> controlFailure(final ControlCallException e) {
        LOGGER.warn("Control plane call failed with status [{}]: {}", e.getStatus(), e.getMessage());
        if (e.getStatus() == HttpStatus.UNAUTHORIZED.value()
                || e.getStatus() == HttpStatus.FORBIDDEN.value()) {
            // 上游鉴权失败不能伪装成管理 API 的 401，否则前端会清掉有效的 UI 令牌并跳回入口。
            final String hint = e.getStatus() == HttpStatus.UNAUTHORIZED.value()
                    ? "CAS 控制面鉴权失败（检查 ADMIN_CONTROL_TOKEN 与 CAS_ADMIN_TOKEN 是否一致）"
                    : "CAS 控制面拒绝访问（控制面仅允许回环地址）";
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResult.fail(HttpStatus.BAD_GATEWAY.value(),
                            hint + "：" + e.getMessage()));
        }
        return ResponseEntity.status(e.getStatus())
                .body(ApiResult.fail(e.getStatus(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> internalError(final Exception e) {
        LOGGER.error("Admin API failure", e);
        return ResponseEntity.internalServerError().body(ApiResult.fail(500, "服务器内部错误"));
    }
}
