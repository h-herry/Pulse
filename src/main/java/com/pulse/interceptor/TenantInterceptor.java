package com.pulse.interceptor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pulse.common.BusinessException;
import com.pulse.common.TenantContext;
import com.pulse.entity.TeamMember;
import com.pulse.mapper.TeamMemberMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * 多租户（团队隔离）拦截器。
 *
 * <p>在 Controller 之前执行，依据请求头 {@code X-Team-ID} 解析团队上下文：
 * <ul>
 *   <li>白名单与认证类端点（/api/v1/auth/**、/users/me、我的任务、创建团队等）豁免团队校验；</li>
 *   <li>管理后台（/api/v1/admin/**）与全局提醒扫描免除成员校验（系统管理员权限在 Service 层再判定）；</li>
 *   <li>{@code X-Team-ID: all} 开启全局视图（上帝视角），仅系统管理员可触发，跨团队查询绕过 team_id 过滤；</li>
 *   <li>其余请求校验当前用户是否属于该团队，合法则写入 {@link TenantContext#setTenantId(Long)}。</li>
 * </ul>
 *
 * <p>与 {@link com.pulse.service.AuthorizationService} 形成纵深防御：
 * 拦截器做「是否属于团队」的粗粒度门禁，Service 层做「是否队长/管理员」的细粒度授权。
 */
@Component
public class TenantInterceptor implements HandlerInterceptor {

    private static final Set<String> WHITELIST = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login"
    );

    private final TeamMemberMapper teamMemberMapper;

    public TenantInterceptor(TeamMemberMapper teamMemberMapper) {
        this.teamMemberMapper = teamMemberMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        // Skip whitelist
        if (WHITELIST.contains(uri)) {
            return true;
        }

        // 关键修复：这些端点不依赖团队上下文，必须无条件放行，无论请求是否携带 X-Team-ID。
        // 原逻辑只在「无 X-Team-ID」时放行 /api/v1/auth/** 等，导致刷新令牌接口在已选团队后
        // 因带 X-Team-ID 但无登录用户而被误判为 401「未登录」。
        if (uri.startsWith("/api/v1/auth")
                || uri.startsWith("/api/v1/users/me")
                || (uri.equals("/api/v1/teams") && "GET".equals(method))
                || uri.equals("/api/v1/tasks/my")) {
            return true;
        }

        // 注意：/api/v1/notifications 不再整体豁免。
        // 原豁免会让 listForUser/unreadCount 在 TenantContext.tenantId 为 null 时按 teamId=null
        // 过滤，导致通知列表永远为空。通知本就是「按团队隔离」的资源，应走下方正常
        // X-Team-ID + 成员校验流程（前端请求拦截器始终注入 X-Team-ID）。

        // Admin endpoints: JWT authenticated but exempt from team membership check
        if (uri.startsWith("/api/v1/admin")) {
            // 双保险：仅系统管理员可访问管理后台（与 @PreAuthorize 形成纵深防御）
            if (!TenantContext.isSystemAdmin()) {
                throw new BusinessException(403, "仅系统管理员可访问管理后台");
            }
            return true;
        }

        // 智能提醒手动触发：系统级扫描所有团队，不绑定单个团队上下文
        if (uri.startsWith("/api/v1/reminders")) {
            return true;
        }

        // Handle team creation (POST /api/v1/teams) — no X-Team-ID needed
        if ("POST".equals(method) && uri.equals("/api/v1/teams")) {
            return true;
        }

        String teamIdHeader = request.getHeader("X-Team-ID");

        // 全局视图（上帝视角）：仅系统管理员可触发，跨团队查询将绕过 team_id 过滤。
        if (teamIdHeader != null && "all".equalsIgnoreCase(teamIdHeader.trim())) {
            if (!TenantContext.isSystemAdmin()) {
                throw new BusinessException(403, "仅系统管理员可使用全局视图");
            }
            TenantContext.setGlobalView(true);
            return true;
        }

        if (teamIdHeader == null || teamIdHeader.isBlank()) {
            throw new BusinessException(400, "缺少 X-Team-ID header");
        }

        Long teamId;
        try {
            teamId = Long.parseLong(teamIdHeader);
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "X-Team-ID 格式无效");
        }

        Long userId = TenantContext.getUserId();

        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }

        // 系统管理员跳过成员校验：可操作/查看任意团队（上帝视角），
        // 配合 requireTeamLead 已放开的管理权限，构成完整的跨团队可见可管理能力。
        if (!TenantContext.isSystemAdmin()) {
            boolean isMember = teamMemberMapper.exists(
                    new LambdaQueryWrapper<TeamMember>()
                            .eq(TeamMember::getTeamId, teamId)
                            .eq(TeamMember::getUserId, userId)
            );
            if (!isMember) {
                throw new BusinessException(403, "您不是该团队的成员");
            }
        }

        TenantContext.setTenantId(teamId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TenantContext.clear();
    }
}
