package com.pulse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 集成测试基类：以 MockMvc 在内存中派发请求，完整经过
 * JwtAuthFilter + TenantInterceptor + Controller + Service 链路。
 * 每个测试方法处于 @Transactional 中，结束后回滚，保证用例间相互隔离、可重复执行。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime());

    /** 生成全局唯一的测试邮箱，避免唯一约束冲突 */
    protected String uniqueEmail() {
        return "u" + SEQ.incrementAndGet() + "@test.dev";
    }

    // ----- 认证 -----

    protected AuthResult register(String email, String password, String displayName) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        body.put("displayName", displayName);
        MvcResult r = request(post("/api/v1/auth/register"), body, null, null);
        JsonNode data = read(r).get("data");
        return new AuthResult(data.get("token").asText(), data.get("userId").asLong(), data.get("email").asText());
    }

    protected AuthResult login(String email, String password) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        MvcResult r = request(post("/api/v1/auth/login"), body, null, null);
        JsonNode data = read(r).get("data");
        return new AuthResult(data.get("token").asText(), data.get("userId").asLong(), data.get("email").asText());
    }

    // ----- 资源创建（链式复用） -----

    protected long createTeam(String token, String name) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("description", "integration test team");
        MvcResult r = request(post("/api/v1/teams"), body, token, null);
        return read(r).get("data").get("id").asLong();
    }

    protected long createProject(String token, long teamId, String name, String key) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("projectKey", key);
        body.put("enableReview", true);
        MvcResult r = request(post("/api/v1/teams/" + teamId + "/projects"), body, token, teamId);
        return read(r).get("data").get("id").asLong();
    }

    protected long createColumn(String token, long teamId, long projectId, String name, String status) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("mappedStatus", status);
        MvcResult r = request(post("/api/v1/projects/" + projectId + "/columns"), body, token, teamId);
        return read(r).get("data").get("id").asLong();
    }

    protected long createTask(String token, long teamId, long projectId, String title) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("priority", "P2");
        MvcResult r = request(post("/api/v1/projects/" + projectId + "/tasks"), body, token, teamId);
        return read(r).get("data").get("id").asLong();
    }

    protected long createIteration(String token, long teamId, long projectId, String name) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        MvcResult r = request(post("/api/v1/projects/" + projectId + "/iterations"), body, token, teamId);
        return read(r).get("data").get("id").asLong();
    }

    protected long createTag(String token, long teamId, String name, String color) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("color", color);
        MvcResult r = request(post("/api/v1/teams/" + teamId + "/tags"), body, token, teamId);
        return read(r).get("data").get("id").asLong();
    }

    // ----- 请求/响应工具 -----

    protected MvcResult request(MockHttpServletRequestBuilder b, Object body, String token, Long teamId) throws Exception {
        b.contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            b.content(objectMapper.writeValueAsString(body));
        }
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        if (teamId != null) {
            b.header("X-Team-ID", teamId);
        }
        MvcResult r = mockMvc.perform(b).andReturn();
        if (System.getProperty("pulse.debug") != null) {
            String uri = b.buildRequest(new org.springframework.mock.web.MockServletContext()).getRequestURI();
            int status = r.getResponse().getStatus();
            String respBody = r.getResponse().getContentAsString();
            System.out.println("[pulse.debug] " + b.buildRequest(new org.springframework.mock.web.MockServletContext()).getMethod()
                    + " " + uri + " -> " + status + " | " + respBody);
        }
        return r;
    }

    protected JsonNode read(MvcResult r) throws Exception {
        return objectMapper.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    protected static final class AuthResult {
        final String token;
        final long userId;
        final String email;

        AuthResult(String token, long userId, String email) {
            this.token = token;
            this.userId = userId;
            this.email = email;
        }
    }
}
