package com.pulse.dto;

/**
 * 登录成功响应。
 *
 * <p>携带 JWT {@code token} 及基础身份信息；{@code mustChangePassword} 指示是否强制改密，
 * {@code isSystemAdmin} 指示是否为系统管理员（跨团队全局权限，区别于团队内角色）。
 */
public class LoginResponse {
    private String token;
    private Long userId;
    private String displayName;
    private String email;
    private Boolean mustChangePassword = false;
    private Boolean isSystemAdmin = false;

    public LoginResponse(String token, Long userId, String displayName, String email) {
        this.token = token;
        this.userId = userId;
        this.displayName = displayName;
        this.email = email;
    }

    public String getToken() { return token; }
    public Long getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public Boolean getMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(Boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public Boolean getIsSystemAdmin() { return isSystemAdmin; }
    public void setIsSystemAdmin(Boolean isSystemAdmin) { this.isSystemAdmin = isSystemAdmin; }
}
