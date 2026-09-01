package com.pulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 凭一次性重置令牌设置新密码请求。
 *
 * <p>{@code token} 为管理员重置 / 邀请建号后签发的一次性令牌（15 分钟有效）；
 * {@code newPassword} 为用户自助设置的新密码。响应体不含任何明文凭据。
 */
@Data
public class PasswordResetConfirmRequest {
    @NotBlank(message = "重置令牌不能为空")
    private String token;
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 128, message = "新密码需 8-128 位")
    private String newPassword;
}
