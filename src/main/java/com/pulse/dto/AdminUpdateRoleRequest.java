package com.pulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 管理员设置用户全局角色请求。
 * role 取值：user=普通用户，admin=系统管理员（跨团队全局权限）。
 */
public class AdminUpdateRoleRequest {
    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "user|admin", message = "角色仅支持 user 或 admin")
    private String role;

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
