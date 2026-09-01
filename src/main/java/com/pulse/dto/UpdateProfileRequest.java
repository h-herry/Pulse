package com.pulse.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 当前用户更新个人资料请求。
 *
 * <p>{@code displayName} 显示名，{@code email} 邮箱（含格式与长度校验），
 * {@code avatarUrl} 头像地址（可选，长度受限）。
 */
public class UpdateProfileRequest {
    @NotBlank(message = "显示名称不能为空")
    @Size(max = 100, message = "显示名称最多 100 字符")
    private String displayName;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 255, message = "邮箱最多 255 字符")
    private String email;

    @Size(max = 500, message = "头像 URL 最多 500 字符")
    private String avatarUrl;

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
}
