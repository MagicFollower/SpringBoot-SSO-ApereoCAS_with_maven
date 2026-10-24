package com.example.erp.web;

import com.example.erp.common.ApiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ErpExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErpExceptionHandler.class);

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResult<Void> badRequest(final MissingServletRequestParameterException e) {
        return ApiResult.fail(400, "缺少参数：" + e.getParameterName());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResult<Void> illegalArgument(final IllegalArgumentException e) {
        return ApiResult.fail(400, e.getMessage());
    }

    /**
     * 未匹配路径（Spring Boot 3.2+ 对静态资源兜底改为抛 NoResourceFoundException）。
     * 必须在 Exception 兜底之前拦截，否则误报 500——典型触发：开发态 Vite 代理
     * 把 SPA 路由（如 /erp/board）整体转发到后端。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResult<Void>> noResource(final NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResult.fail(404, "接口不存在：" + e.getResourcePath()));
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> internalError(final Exception e) {
        LOGGER.error("ERP request failed", e);
        return ApiResult.fail(500, "服务器内部错误");
    }
}