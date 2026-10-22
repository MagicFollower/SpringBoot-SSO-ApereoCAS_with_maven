package com.example.admin.client;

/**
 * CAS 控制面调用失败，携带原 HTTP 状态码。
 */
public class ControlCallException extends RuntimeException {

    private final int status;

    public ControlCallException(final int status, final String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
