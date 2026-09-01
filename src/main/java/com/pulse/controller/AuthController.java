package com.pulse.controller;

import com.pulse.common.Result;
import com.pulse.common.TenantContext;
import com.pulse.dto.*;
import com.pulse.entity.User;
import com.pulse.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

/**
 * 认证端点（路径 /api/v1/auth）。
 * 职责：处理用户注册、登录、刷新令牌、登出，以及当前用户信息读取/更新、忘记密码、主动改密。
 * 安全约束：认证类端点走 /api/v1/auth 前缀（TenantInterceptor 对该前缀豁免 X-Team-ID）；
 * 无状态 JWT 由 JwtAuthFilter 装配认证；刷新令牌仅在 HttpOnly Cookie 中流转，绝不写入响应体以防 XSS 读取。
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "认证", description = "用户注册与登录")
public class AuthController {

    private final AuthService authService;

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    /**
     * 刷新令牌 Cookie 的 Secure 标志：由配置驱动而非 request.isSecure()，
     * 避免代理转发头缺失时静默降级为明文可传输的 Cookie。生产保持 true（仅 HTTPS 下发）；
     * 本地 http 调试可用 REFRESH_COOKIE_SECURE=false 显式关闭。
     */
    @Value("${jwt.refresh-cookie-secure:true}")
    private boolean refreshCookieSecure;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 所需权限：公开（无需登录，认证端点）。
     * 功能：用户注册并签发访问/刷新令牌；刷新令牌写入 HttpOnly Cookie。
     * 关键入参：RegisterRequest（用户名、密码、邮箱等）；HttpServletRequest/Response（用于写 Cookie）。
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest req,
                                          HttpServletRequest request, HttpServletResponse response) {
        LoginResponse resp = authService.register(req);
        addRefreshCookie(response, authService.issueRefreshToken(resp.getUserId()), request);
        return Result.ok(resp);
    }

    /**
     * 所需权限：公开（无需登录，认证端点）。
     * 功能：用户登录并签发访问/刷新令牌；刷新令牌写入 HttpOnly Cookie。
     * 关键入参：LoginRequest（用户名/邮箱、密码）；HttpServletRequest/Response（写 Cookie）。
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                       HttpServletRequest request, HttpServletResponse response) {
        LoginResponse resp = authService.login(req);
        addRefreshCookie(response, authService.issueRefreshToken(resp.getUserId()), request);
        return Result.ok(resp);
    }

    /**
     * 所需权限：公开（凭刷新令牌 Cookie，无 Cookie 返回 401）。
     * 功能：用刷新令牌换发新的访问令牌；新刷新令牌仍仅经 HttpOnly Cookie 下发，绝不写入响应体。
     * 关键入参：HttpServletRequest（读取 pulse_refresh Cookie）/Response（写新 Cookie）。
     */
    @PostMapping("/refresh")
    @Operation(summary = "用刷新令牌换发新的访问令牌")
    public Result<LoginResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refresh = extractRefreshCookie(request);
        if (refresh == null) {
            return Result.fail(401, "登录已过期，请重新登录");
        }
        TokenPair pair = authService.refresh(refresh);
        // 刷新令牌仅在 HttpOnly Cookie 中流转，绝不写入响应体，避免被 XSS 读取
        addRefreshCookie(response, pair.getRefreshToken(), request);
        LoginResponse refreshed = new LoginResponse(pair.getAccessToken(), pair.getUserId(),
                pair.getDisplayName(), pair.getEmail());
        User refreshedUser = authService.getUserById(pair.getUserId());
        refreshed.setMustChangePassword(refreshedUser.getMustChangePassword() != null
                && refreshedUser.getMustChangePassword());
        return Result.ok(refreshed);
    }

    /**
     * 所需权限：登录用户（清除自身刷新令牌）。
     * 功能：使刷新令牌失效（将 pulse_refresh Cookie 置空并 maxAge=0）。
     * 关键入参：HttpServletRequest/Response（操作 Cookie）。
     */
    @PostMapping("/logout")
    @Operation(summary = "退出登录（使刷新令牌失效）")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("pulse_refresh", "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return Result.ok();
    }

    /**
     * 所需权限：需登录（未登录返回 401）。
     * 功能：获取当前登录用户的基本信息。
     * 关键入参：无（用户身份取自 TenantContext.getUserId()）。
     */
    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息")
    public Result<User> me() {
        Long userId = TenantContext.getUserId();
        if (userId == null) return Result.fail(401, "未登录");
        return Result.ok(authService.getUserById(userId));
    }

    /**
     * 所需权限：需登录（未登录返回 401）。
     * 功能：更新当前登录用户的个人资料。
     * 关键入参：UpdateProfileRequest（待更新的资料字段）。
     */
    @PutMapping("/me")
    @Operation(summary = "更新当前用户信息")
    public Result<User> updateMe(@Valid @RequestBody UpdateProfileRequest req) {
        Long userId = TenantContext.getUserId();
        if (userId == null) return Result.fail(401, "未登录");
        return Result.ok(authService.updateProfile(userId, req));
    }

    /**
     * 所需权限：公开（无需登录）。
     * 功能：请求重置密码（MVP 不实际发送邮件）；无论邮箱是否注册均返回 200，避免泄露邮箱是否存在。
     * 关键入参：ForgotPasswordRequest.email（目标邮箱）。
     */
    @PostMapping("/forgot-password")
    @Operation(summary = "请求重置密码（MVP 不实际发送邮件）")
    public Result<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.requestPasswordReset(req.getEmail());
        // 无论邮箱是否注册，均返回 200，避免泄露邮箱是否存在
        return Result.ok();
    }

    /**
     * 所需权限：需登录（首次登录改密或主动改密，未登录返回 401）。
     * 功能：校验旧密码后更新为新密码。
     * 关键入参：ChangePasswordRequest.oldPassword/newPassword。
     */
    @PostMapping("/change-password")
    @Operation(summary = "首次登录改密 / 主动改密（需登录）")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        Long userId = TenantContext.getUserId();
        if (userId == null) return Result.fail(401, "未登录");
        authService.changePassword(userId, req.getOldPassword(), req.getNewPassword());
        return Result.ok();
    }

    /**
     * 所需权限：公开（凭一次性重置令牌，无令牌/令牌失效返回「链接无效或已过期」）。
     * 功能：管理员重置 / 邀请建号后，用户凭重置令牌自助设置新密码；令牌 15 分钟有效、不含明文凭据。
     * 关键入参：PasswordResetConfirmRequest.token/newPassword。
     */
    @PostMapping("/reset-password")
    @Operation(summary = "凭一次性重置令牌设置新密码")
    public Result<Void> resetPassword(@Valid @RequestBody PasswordResetConfirmRequest req) {
        authService.confirmPasswordReset(req.getToken(), req.getNewPassword());
        return Result.ok();
    }

    private void addRefreshCookie(HttpServletResponse response, String token, HttpServletRequest request) {
        ResponseCookie cookie = ResponseCookie.from("pulse_refresh", token)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Strict")
                .path("/")
                .maxAge(refreshExpiration / 1000)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if ("pulse_refresh".equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
