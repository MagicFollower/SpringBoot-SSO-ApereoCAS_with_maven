package com.example.erp.web;

import com.example.erp.common.ApiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> internalError(final Exception e) {
        LOGGER.error("ERP request failed", e);
        return ApiResult.fail(500, "服务器内部错误");
    }
}