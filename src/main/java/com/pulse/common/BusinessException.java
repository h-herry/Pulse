package com.pulse.common;

/**
 * 业务异常。携带业务错误码（默认 400），由 {@code GlobalExceptionHandler} 统一转换为
 * {@link Result} 响应。用于表示校验失败、权限不足、资源不存在等可预期的业务错误，
 * 与不可预期的系统异常（500）区分。
 */
public class BusinessException extends RuntimeException {
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this(400, message);
    }

    public int getCode() { return code; }
}
