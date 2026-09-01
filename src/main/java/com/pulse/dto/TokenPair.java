package com.pulse.dto;

/**
 * 访问令牌与刷新令牌对（登录/刷新接口响应）。
 *
 * <p>携带 {@code accessToken}（短期访问令牌）、{@code refreshToken}（用于换取新令牌），
 * 以及 {@code userId}/{@code displayName}/{@code email} 等基础身份信息。
 */
public class TokenPair {
    private String accessToken;
    private String refreshToken;
    private Long userId;
    private String displayName;
    private String email;

    public TokenPair(String accessToken, String refreshToken, Long userId, String displayName, String email) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.displayName = displayName;
        this.email = email;
    }

    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public Long getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
}
