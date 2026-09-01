package com.pulse.realtime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pulse.entity.Project;
import com.pulse.entity.TeamMember;
import com.pulse.mapper.ProjectMapper;
import com.pulse.mapper.TeamMemberMapper;
import com.pulse.security.JwtUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * WebSocket（STOMP）信道拦截器，负责实时通道的认证与团队隔离。
 *
 * <p>CONNECT 阶段解析 Bearer JWT 并将 userId 写入 STOMP 会话 principal；
 * SUBSCRIBE 阶段执行「团队隔离」校验：
 * <ul>
 *   <li>通知通道 {@code /topic/notifications.{userId}} 仅允许订阅者本人订阅；</li>
 *   <li>看板通道 {@code /topic/board.{pid}[.presence]} 需校验订阅者属于该看板所在团队的成员。</li>
 * </ul>
 * 校验不通过时返回 {@code null} 拒绝该订阅消息，实现多租户下的实时数据隔离（SEC-04）。
 */
@Component
public class RealtimeChannelInterceptor implements ChannelInterceptor {

    private final JwtUtils jwtUtils;
    private final ProjectMapper projectMapper;
    private final TeamMemberMapper teamMemberMapper;

    public RealtimeChannelInterceptor(JwtUtils jwtUtils, ProjectMapper projectMapper,
                                      TeamMemberMapper teamMemberMapper) {
        this.jwtUtils = jwtUtils;
        this.projectMapper = projectMapper;
        this.teamMemberMapper = teamMemberMapper;
    }

    /**
     * 消息发送前拦截：CONNECT 阶段完成 JWT 认证，SUBSCRIBE 阶段完成团队隔离校验。
     * 返回 {@code null} 即丢弃该消息（拒绝订阅），返回原消息则放行。
     *
     * @param message 待发送消息
     * @param channel 目标消息通道
     * @return 放行返回原消息，拒绝订阅返回 {@code null}
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String auth = accessor.getFirstNativeHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                try {
                    Long userId = jwtUtils.getUserIdFromToken(auth.substring(7));
                    accessor.setUser(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
                } catch (Exception ignored) {
                    // 无效令牌：不设置 principal，后续消息映射会因无认证被拒绝
                }
            }
            return message;
        }

        // SEC-04: SUBSCRIBE 阶段进行团队隔离校验
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination == null) {
                return message;
            }

            // R-007: 通知通道 /topic/notifications.{userId}
            Long notifUserId = extractNotificationUserId(destination);
            if (notifUserId != null) {
                Long userId = extractUserId(accessor);
                if (userId == null) {
                    return null; // 无认证用户，拒绝订阅
                }
                if (!notifUserId.equals(userId)) {
                    return null; // 只能订阅自己的通知通道
                }
                return message; // 验证通过
            }

            // 看板通道 /topic/board.{pid} 或 /topic/board.{pid}.presence
            Long pid = extractProjectId(destination);
            if (pid == null) {
                return message; // 非已知通道格式，放行（如用户队列等）
            }
            // 从 CONNECT 阶段设置的 principal 中获取 userId
            Long userId = extractUserId(accessor);
            if (userId == null) {
                return null; // 无认证用户，拒绝订阅
            }
            // 查询项目，验证用户是否属于项目所在团队
            Project project = projectMapper.selectById(pid);
            if (project == null) {
                return null; // 项目不存在，拒绝订阅
            }
            boolean isMember = teamMemberMapper.exists(
                    new LambdaQueryWrapper<TeamMember>()
                            .eq(TeamMember::getTeamId, project.getTeamId())
                            .eq(TeamMember::getUserId, userId));
            if (!isMember) {
                return null; // 用户不属于该项目团队，拒绝订阅
            }
        }

        return message;
    }

    /**
     * 从 STOMP 目标地址中解析项目 ID，支持 {@code /topic/board.{pid}} 与 {@code /topic/board.{pid}.presence}。
     *
     * @param destination STOMP 订阅目标地址
     * @return 解析到的项目 ID；地址格式不匹配时返回 {@code null}
     */
    private Long extractProjectId(String destination) {
        // destination 形如 /topic/board.123 或 /topic/board.123.presence
        String marker = "/topic/board.";
        int idx = destination.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        String rest = destination.substring(idx + marker.length());
        // 取第一个 . 之前的部分作为 pid
        int dotIdx = rest.indexOf('.');
        String pidStr = (dotIdx >= 0) ? rest.substring(0, dotIdx) : rest;
        try {
            return Long.parseLong(pidStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从 STOMP 目标地址中解析通知用户 ID，支持 {@code /topic/notifications.{userId}}。
     *
     * @param destination STOMP 订阅目标地址
     * @return 解析到的用户 ID；地址格式不匹配时返回 {@code null}
     */
    private Long extractNotificationUserId(String destination) {
        String marker = "/topic/notifications.";
        int idx = destination.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        String rest = destination.substring(idx + marker.length());
        try {
            return Long.parseLong(rest);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从 STOMP 会话 principal（{@link UsernamePasswordAuthenticationToken}）中提取 userId。
     *
     * @param accessor STOMP 消息头访问器
     * @return 解析到的用户 ID；无认证或无法解析时返回 {@code null}
     */
    private Long extractUserId(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            return null;
        }
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken authToken) {
            Object principalObj = authToken.getPrincipal();
            if (principalObj instanceof Long userId) {
                return userId;
            }
            if (principalObj instanceof Number number) {
                return number.longValue();
            }
            if (principalObj != null) {
                try {
                    return Long.parseLong(principalObj.toString());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
