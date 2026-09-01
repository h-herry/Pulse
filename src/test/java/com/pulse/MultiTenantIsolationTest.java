package com.pulse;

import com.pulse.common.BusinessException;
import com.pulse.common.TenantContext;
import com.pulse.service.TaskService;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MultiTenantIsolationTest extends AbstractIntegrationTest {

    @Autowired
    private TaskService taskService;

    /** 成员 B 访问团队 A 的资源必须被拦截器拒绝（403） */
    @Test
    void crossTeamAccessDenied() throws Exception {
        AuthResult a = register(uniqueEmail(), "password123", "A");
        long teamA = createTeam(a.token, "Team A");

        AuthResult b = register(uniqueEmail(), "password123", "B");

        MvcResult r1 = request(get("/api/v1/teams/" + teamA), null, b.token, teamA);
        assertEquals(403, read(r1).get("code").asInt(), "非成员读取其他团队应被拒");

        MvcResult r2 = request(get("/api/v1/teams/" + teamA + "/projects"), null, b.token, teamA);
        assertEquals(403, read(r2).get("code").asInt(), "非成员列举其他团队项目应被拒");
    }

    /** 同时是两团队成员时，用错误的 X-Team-ID 读其他团队应被控制器层防御拒绝（400 无权访问） */
    @Test
    void teamScopingDefenseInDepth() throws Exception {
        AuthResult a = register(uniqueEmail(), "password123", "A");
        long teamA = createTeam(a.token, "Team A");

        AuthResult b = register(uniqueEmail(), "password123", "B");
        long teamB = createTeam(b.token, "Team B");

        // B 把 A 以 MEMBER 角色加入团队 B（替代已下线的邀请链接机制）
        request(post("/api/v1/teams/" + teamB + "/members"),
                Map.of("email", a.email, "role", "USER"), b.token, teamB);

        // A 以 X-Team-ID=teamA 读取 teamB 详情 -> 控制器 id != tenant -> 400
        MvcResult r = request(get("/api/v1/teams/" + teamB), null, a.token, teamA);
        assertEquals(400, read(r).get("code").asInt());

        // A 以 X-Team-ID=teamA 读取 teamA 详情 -> 200
        MvcResult ok = request(get("/api/v1/teams/" + teamA), null, a.token, teamA);
        assertEquals(200, read(ok).get("code").asInt());
    }

    /** 直接验证 Service 层对跨租户任务访问的拒绝（P0 越权修复的核心） */
    @Test
    void taskServiceRejectsCrossTeamAccess() throws Exception {
        AuthResult a = register(uniqueEmail(), "password123", "A");
        long teamA = createTeam(a.token, "Team A");
        long projectA = createProject(a.token, teamA, "Proj A", "PA");
        long taskA = createTask(a.token, teamA, projectA, "Task A");

        // 用不匹配的租户上下文直调 getTask -> 必须抛 BusinessException("无权访问")
        TenantContext.setUserId(a.userId);
        TenantContext.setTenantId(999999L);
        try {
            BusinessException ex = assertThrows(BusinessException.class, () -> taskService.getTask(taskA));
            assertTrue(ex.getMessage().contains("无权访问"));
        } finally {
            TenantContext.clear();
        }
    }
}
