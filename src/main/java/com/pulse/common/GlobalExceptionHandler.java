package com.pulse.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器（{@code @RestControllerAdvice}）。
 * 统一拦截控制器层抛出的异常，将其转换为 {@code com.pulse.common.Result} 标准响应，
 * 避免异常堆栈直接暴露给客户端，并保证前端始终收到一致的错误结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常 {@link BusinessException}。
     * 将业务自定义的错误码与消息包装为失败 Result 返回（HTTP 200 + 业务 code）。
     *
     * @param e 业务异常
     * @return 失败响应，携带业务 code 与 message
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("Business error: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理 Spring Security 鉴权失败（{@link AccessDeniedException}）。
     * 对应「已登录但无权限」场景，返回 HTTP 403 与「无权访问」提示。
     *
     * @param e 访问拒绝异常
     * @return HTTP 403 的失败响应
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.fail(403, "无权访问"));
    }

    /**
     * 处理请求参数校验失败（{@link MethodArgumentNotValidException}）。
     * 收集所有字段校验默认消息并以分号拼接，返回 HTTP 400 失败响应。
     *
     * @param e 参数校验异常
     * @return HTTP 400 失败响应，message 为各字段校验信息拼接
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.fail(400, msg));
    }

    /**
     * 兜底处理其余未被上述处理器覆盖的异常（含运行时异常）。
     * 记录错误日志并返回 HTTP 500「服务器内部错误」，避免不可预期异常泄露细节。
     *
     * @param e 未预期异常
     * @return HTTP 500 失败响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleAll(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(500, "服务器内部错误"));
    }
}
