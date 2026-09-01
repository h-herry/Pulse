package com.pulse;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TeamProjectTaskFlowTest extends AbstractIntegrationTest {

    @Test
    void fullCrudFlow() throws Exception {
        AuthResult a = register(uniqueEmail(), "password123", "A");
        long teamA = createTeam(a.token, "Team A");
        long projectA = createProject(a.token, teamA, "Proj A", "PA");

        // 项目列表
        MvcResult list = request(get("/api/v1/teams/" + teamA + "/projects"), null, a.token, teamA);
        JsonNode listData = read(list).get("data");
        assertEquals(200, read(list).get("code").asInt());
        assertEquals(1, listData.size());

        // 看板列
        long col = createColumn(a.token, teamA, projectA, "TODO", "TODO");

        // 任务
        long task = createTask(a.token, teamA, projectA, "Task 1");

        // 移动任务到列
        MvcResult mv = request(put("/api/v1/tasks/" + task + "/move"),
                Map.of("targetColumnId", col), a.token, teamA);
        assertEquals(200, read(mv).get("code").asInt());

        // 任务列表
        MvcResult tasks = request(get("/api/v1/projects/" + projectA + "/tasks"), null, a.token, teamA);
        assertTrue(read(tasks).get("data").get("total").asLong() >= 1);

        // 详情
        MvcResult det = request(get("/api/v1/tasks/" + task), null, a.token, teamA);
        assertEquals("Task 1", read(det).get("data").get("title").asText());

        // 更新
        MvcResult upd = request(patch("/api/v1/tasks/" + task),
                Map.of("title", "Task 1 updated", "status", "IN_PROGRESS"), a.token, teamA);
        JsonNode ud = read(upd).get("data");
        assertEquals("Task 1 updated", ud.get("title").asText());
        assertEquals("IN_PROGRESS", ud.get("status").asText());

        // 标签（在测试团队内创建，避免种子标签跨团队被拒）
        long tag = createTag(a.token, teamA, "Bug", "#dc2626");
        MvcResult addTag = request(post("/api/v1/tasks/" + task + "/tags/" + tag), null, a.token, teamA);
        assertEquals(200, read(addTag).get("code").asInt());
        MvcResult tags = request(get("/api/v1/tasks/" + task + "/tags"), null, a.token, teamA);
        assertTrue(read(tags).get("data").size() >= 1);
        MvcResult rmTag = request(delete("/api/v1/tasks/" + task + "/tags/" + tag), null, a.token, teamA);
        assertEquals(200, read(rmTag).get("code").asInt());

        // 活动日志
        MvcResult act = request(get("/api/v1/tasks/" + task + "/activity"), null, a.token, teamA);
        assertEquals(200, read(act).get("code").asInt());

        // 删除
        MvcResult del = request(delete("/api/v1/tasks/" + task), null, a.token, teamA);
        assertEquals(200, read(del).get("code").asInt());
    }
}
