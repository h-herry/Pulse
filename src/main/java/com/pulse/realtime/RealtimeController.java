package com.pulse.realtime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pulse.entity.Project;
import com.pulse.entity.TeamMember;
import com.pulse.mapper.ProjectMapper;
import com.pulse.mapper.TeamMemberMapper;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 看板实时协作中枢。
 * - presence：谁在看这个看板（在线成员）
 * - edit：谁在查看/编辑哪个任务（细粒度协同编辑指示）
 * - move / update：任务移动或字段更新广播，让其他端即时同步
 */
@Controller
public class RealtimeController {

    private final SimpMessagingTemplate template;
    private final ProjectMapper projectMapper;
    private final TeamMemberMapper teamMemberMapper;
    // projectId -> (sessionId -> memberInfo)
    private final Map<Long, Map<String, Map<String, String>>> presence = new ConcurrentHashMap<>();
    // projectId -> (taskId -> (sessionId -> {name,email,action}))
    private final Map<Long, Map<Long, Map<String, Map<String, String>>>> editors = new ConcurrentHashMap<>();

    public RealtimeController(SimpMessagingTemplate template, ProjectMapper projectMapper,
                              TeamMemberMapper teamMemberMapper) {
        this.template = template;
        this.projectMapper = projectMapper;
        this.teamMemberMapper = teamMemberMapper;
    }

    /**
     * SEC-04: 验证消息发送者是否有权操作该项目的看板。
     * 通过 pid 查询项目，验证项目的 teamId 是否在用户所属团队中。
     * WebSocket 消息处理中 TenantContext 未设置，需从 Principal 中获取 userId。
     *
     * @param pid  项目 ID
     * @param principal STOMP 连接认证后的 Principal
     * @return true 如果用户有权访问该项目看板
     */
    private boolean isAuthorized(Long pid, Principal principal) {
        if (principal == null) {
            return false;
        }
        Long userId = extractUserId(principal);
        if (userId == null) {
            return false;
        }
        Project project = projectMapper.selectById(pid);
        if (project == null) {
            return false;
        }
        return teamMemberMapper.exists(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, project.getTeamId())
                        .eq(TeamMember::getUserId, userId));
    }

    /** 从 Principal 中提取 userId。 */
    private Long extractUserId(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken authToken) {
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

    @MessageMapping("board.{pid}.presence")
    public void presence(@DestinationVariable Long pid,
                         @Payload Map<String, Object> body,
                         Principal principal) {
        if (!isAuthorized(pid, principal)) return;

        String sessionId = body.get("sessionId") != null ? body.get("sessionId").toString()
                : UUID.randomUUID().toString();
        String action = (String) body.getOrDefault("action", "ping");
        Map<String, Map<String, String>> room = presence.computeIfAbsent(pid, k -> new ConcurrentHashMap<>());
        if ("leave".equals(action)) {
            room.remove(sessionId);
            // 离开看板时，一并清除该会话在任意任务上的编辑指示
            clearEditorSession(pid, sessionId);
        } else {
            Map<String, String> info = new HashMap<>();
            info.put("name", String.valueOf(body.getOrDefault("name", "")));
            info.put("email", String.valueOf(body.getOrDefault("email", "")));
            room.put(sessionId, info);
        }
        broadcastPresence(pid);
        broadcastEditors(pid);
    }

    @MessageMapping("board.{pid}.move")
    public void move(@DestinationVariable Long pid, @Payload Map<String, Object> body,
                     Principal principal) {
        if (!isAuthorized(pid, principal)) return;

        template.convertAndSend("/topic/board." + pid,
                Map.of("type", "moved", "taskId", body.get("taskId"), "by", body.get("by")));
    }

    /** 字段级更新广播（标题/状态/优先级/到期/负责人/子任务等变更）。 */
    @MessageMapping("board.{pid}.update")
    public void update(@DestinationVariable Long pid, @Payload Map<String, Object> body,
                       Principal principal) {
        if (!isAuthorized(pid, principal)) return;

        template.convertAndSend("/topic/board." + pid,
                Map.of("type", "updated", "taskId", body.get("taskId"), "by", body.get("by")));
    }

    /** 编辑/查看某任务的在线指示，供其他端显示"谁在编辑"。 */
    @MessageMapping("board.{pid}.edit")
    public void edit(@DestinationVariable Long pid, @Payload Map<String, Object> body,
                     Principal principal) {
        if (!isAuthorized(pid, principal)) return;

        String sessionId = body.get("sessionId") != null ? body.get("sessionId").toString()
                : UUID.randomUUID().toString();
        String action = (String) body.getOrDefault("action", "leave");
        Long taskId = toLong(body.get("taskId"));
        if (taskId == null) return;
        Map<Long, Map<String, Map<String, String>>> room = editors.computeIfAbsent(pid, k -> new ConcurrentHashMap<>());
        Map<String, Map<String, String>> taskEditors = room.computeIfAbsent(taskId, k -> new ConcurrentHashMap<>());
        if ("leave".equals(action)) {
            taskEditors.remove(sessionId);
            if (taskEditors.isEmpty()) room.remove(taskId);
        } else {
            Map<String, String> info = new HashMap<>();
            info.put("name", String.valueOf(body.getOrDefault("name", "")));
            info.put("email", String.valueOf(body.getOrDefault("email", "")));
            info.put("action", "editing".equals(action) ? "editing" : "viewing");
            taskEditors.put(sessionId, info);
        }
        broadcastEditors(pid);
    }

    private void broadcastPresence(Long pid) {
        Map<String, Map<String, String>> room = presence.getOrDefault(pid, Map.of());
        List<Map<String, String>> list = new ArrayList<>(new LinkedHashSet<>(room.values()));
        template.convertAndSend("/topic/board." + pid + ".presence",
                Map.of("type", "presence", "members", list));
    }

    private void broadcastEditors(Long pid) {
        Map<Long, Map<String, Map<String, String>>> room = editors.getOrDefault(pid, Map.of());
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Map<String, String>>> te : room.entrySet()) {
            for (Map.Entry<String, Map<String, String>> se : te.getValue().entrySet()) {
                Map<String, Object> e = new HashMap<>();
                e.put("taskId", te.getKey());
                e.put("name", se.getValue().get("name"));
                e.put("email", se.getValue().get("email"));
                e.put("action", se.getValue().get("action"));
                list.add(e);
            }
        }
        template.convertAndSend("/topic/board." + pid + ".edit",
                Map.of("type", "editors", "editors", list));
    }

    private void clearEditorSession(Long pid, String sessionId) {
        Map<Long, Map<String, Map<String, String>>> room = editors.get(pid);
        if (room == null) return;
        boolean changed = false;
        for (Iterator<Map.Entry<Long, Map<String, Map<String, String>>>> it = room.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Map<String, Map<String, String>>> e = it.next();
            if (e.getValue().remove(sessionId) != null) changed = true;
            if (e.getValue().isEmpty()) it.remove();
        }
        if (changed) broadcastEditors(pid);
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return null; }
    }
}
