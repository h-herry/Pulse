package com.pulse.security;

import com.pulse.common.TenantContext;
import com.pulse.entity.User;
import com.pulse.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * JWT 认证过滤器（无状态）。
 *
 * <p>在每个 HTTP 请求进入 Controller 之前执行，职责：
 * <ol>
 *   <li>从 {@code Authorization: Bearer <token>} 提取 JWT；</li>
 *   <li>校验签名与有效期（{@link JwtUtils#validateToken}）；</li>
 *   <li>加载用户，写入 {@link TenantContext}（userId、系统管理员标记）；</li>
 *   <li>构建 Spring {@code SecurityContext}，装入 authority：所有登录用户含 {@code ROLE_USER}，
 *       系统管理员额外含 {@code ROLE_ADMIN}；</li>
 *   <li>拦截被禁用账户（R-014），直接返回 401，不构建认证、不放行。</li>
 * </ol>
 *
 * <p>团队内角色（LEAD）不在此处装配——它由 {@code AuthorizationService} 依据
 * {@code team_members.role} 在业务层按需判定，保持「全局管理员」与「团队内角色」双轴解耦。
 *
 * <p>注意：WebSocket(STOMP) 消息处理不经过本过滤器，其鉴权交由
 * {@code RealtimeChannelInterceptor} 完成（见实时模块）。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserMapper userMapper;

    /** 构造注入：JWT 工具与用户 Mapper。 */
    public JwtAuthFilter(JwtUtils jwtUtils, UserMapper userMapper) {
        this.jwtUtils = jwtUtils;
        this.userMapper = userMapper;
    }

    /**
     * 过滤器核心。解析并校验 JWT，成功则建立租户上下文与安全上下文；
     * 无论成功失败，finally 中都会清理 {@link TenantContext}，避免线程复用污染。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String token = extractToken(request);
            if (token != null && jwtUtils.validateToken(token)) {
                Long userId = jwtUtils.getUserIdFromToken(token);
                User user = userMapper.selectById(userId);
                if (user != null) {
                    TenantContext.setUserId(userId);

                    // 系统级管理员：依据 users.is_system_admin，与团队内角色(LEAD)完全解耦。
                    boolean isSystemAdmin = user.getIsSystemAdmin() != null && user.getIsSystemAdmin();
                    TenantContext.setSystemAdmin(isSystemAdmin);
                    user.setIsAdmin(isSystemAdmin);

                    // R-014: 禁用用户直接拒绝访问，不构建认证、不放行
                    if (user.getEnabled() == null || !user.getEnabled()) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write(
                                "{\"code\":401,\"message\":\"账户已被禁用，请联系管理员\",\"data\":null}");
                        return;
                    }

                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                    if (isSystemAdmin) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                    }

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(user, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * 从请求头提取 JWT。仅识别 {@code Authorization: Bearer <token>} 形式；
     * 其它情况（无头/非 Bearer）返回 null，交由后续链路按匿名处理。
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
