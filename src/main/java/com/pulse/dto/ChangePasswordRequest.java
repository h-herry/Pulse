package com.pulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 首次登录改密 / 主动改密请求。
 * oldPassword 校验当前密码；newPassword 至少 8 位。
 */
@Data
public class ChangePasswordRequest {
    @NotBlank(message = "当前密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 128, message = "新密码需 8-128 位")
    private String newPassword;
}
