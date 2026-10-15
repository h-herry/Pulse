package com.pulse.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员新增用户请求（邮箱 + 密码 + 显示名 + 可选全局角色）。
 * role 取值：user=普通用户（默认）、admin=系统管理员；缺省按 user 处理。
 */
public class AdminCreateUserRequest {
    @NotBlank @Email
    private String email;
    @NotBlank @Size(min = 6, max = 100)
    private String password;
    @NotBlank @Size(max = 100)
    private String displayName;
    /** 全局角色：user / admin，缺省为 user */
    private String role;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
