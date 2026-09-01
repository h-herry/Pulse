package com.pulse.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
/**
 * 忘记密码请求：提交账户邮箱以触发重置/验证码流程。
 */
public class ForgotPasswordRequest {
    @NotBlank
    @Email
    private String email;
}
