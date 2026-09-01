package com.pulse.common;

/**
 * 统一 API 响应包装。
 *
 * <p>约定：{@code code=200} 表示成功，其余为业务失败；{@code data} 携带业务载荷（可为 null），
 * {@code message} 为提示文本。Controller 一律通过 {@code ok/fail} 构造，
 * 异常由 {@code GlobalExceptionHandler} 统一转换为该结构。
 *
 * @param <T> 响应数据类型
 */
public class Result<T> {
    private int code;
    private T data;
    private String message;

    private Result(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, data, "ok");
    }

    public static <T> Result<T> ok() {
        return new Result<>(200, null, "ok");
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, null, message);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(400, null, message);
    }

    public int getCode() { return code; }
    public T getData() { return data; }
    public String getMessage() { return message; }
}
