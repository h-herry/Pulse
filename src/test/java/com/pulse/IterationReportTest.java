package com.pulse;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class IterationReportTest extends AbstractIntegrationTest {

    @Test
    void iterationAndReports() throws Exception {
        AuthResult a = register(uniqueEmail(), "password123", "A");
        long teamA = createTeam(a.token, "Team A");
        long projectA = createProject(a.token, teamA, "Proj A", "PA");

        long iter = createIteration(a.token, teamA, projectA, "Sprint 1");

        // 迭代列表
        MvcResult list = request(get("/api/v1/projects/" + projectA + "/iterations"), null, a.token, teamA);
        assertEquals(1, read(list).get("data").size());

        // 直接创建属于该迭代的任务（绕过 PATCH 校验，覆盖迭代归属）
        Map<String, Object> taskBody = new HashMap<>();
        taskBody.put("title", "Task in sprint");
        taskBody.put("iterationId", iter);
        MvcResult tr = request(post("/api/v1/projects/" + projectA + "/tasks"), taskBody, a.token, teamA);
        long task = read(tr).get("data").get("id").asLong();
        assertTrue(task > 0);

        // 项目级报表端点（与前端契约一致）
        JsonNode stats = read(request(get("/api/v1/projects/" + projectA + "/reports/stats"), null, a.token, teamA)).get("data");
        assertTrue(stats.has("totalTasks"), "stats 应含 totalTasks");
        assertTrue(stats.has("doneTasks"));

        assertEquals(200, read(request(get("/api/v1/projects/" + projectA + "/reports/assignee"), null, a.token, teamA)).get("code").asInt());
        assertEquals(200, read(request(get("/api/v1/projects/" + projectA + "/reports/burndown"), null, a.token, teamA)).get("code").asInt());

        // 手动生成燃尽图快照后应能查到数据
        assertEquals(200, read(request(post("/api/v1/projects/" + projectA + "/reports/burndown/snapshot"), null, a.token, teamA)).get("code").asInt());
        JsonNode burndown = read(request(get("/api/v1/projects/" + projectA + "/reports/burndown"), null, a.token, teamA)).get("data");
        assertTrue(burndown.isArray() && burndown.size() >= 1, "燃尽图快照应至少 1 条");

        // 团队级概览
        assertEquals(200, read(request(get("/api/v1/teams/" + teamA + "/reports/overview"), null, a.token, teamA)).get("code").asInt());
    }
}
