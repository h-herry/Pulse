package com.pulse;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AuthIntegrationTest extends AbstractIntegrationTest {

    @Test
    void registerReturnsTokenAndUser() throws Exception {
        String email = uniqueEmail();
        AuthResult ar = register(email, "password123", "Alice");
        assertNotNull(ar.token);
        assertTrue(ar.token.length() > 10, "JWT 应非空且足够长");
        assertTrue(ar.userId > 0);

        // /me 携带 token 应回显当前用户
        MvcResult r = request(get("/api/v1/auth/me"), null, ar.token, null);
        JsonNode node = read(r);
        assertEquals(200, node.get("code").asInt());
        JsonNode data = node.get("data");
        assertEquals(email, data.get("email").asText());
        assertEquals(ar.userId, data.get("id").asLong());
    }

    @Test
    void loginWithCorrectPasswordSucceeds() throws Exception {
        String email = uniqueEmail();
        register(email, "password123", "Bob");
        AuthResult ar = login(email, "password123");
        assertNotNull(ar.token);
        assertEquals(email, ar.email);
    }

    @Test
    void meWithoutTokenReturns401() throws Exception {
        MvcResult r = request(get("/api/v1/auth/me"), null, null, null);
        assertEquals(401, read(r).get("code").asInt());
    }

    @Test
    void duplicateRegisterReturns400() throws Exception {
        String email = uniqueEmail();
        register(email, "password123", "Carol");
        MvcResult r = request(post("/api/v1/auth/register"),
                Map.of("email", email, "password", "password123", "displayName", "Carol2"),
                null, null);
        JsonNode node = read(r);
        assertEquals(400, node.get("code").asInt());
        assertTrue(node.get("message").asText().contains("已"), "应提示邮箱已注册");
    }

    @Test
    void loginWithWrongPasswordReturns400() throws Exception {
        String email = uniqueEmail();
        register(email, "password123", "Dave");
        MvcResult r = request(post("/api/v1/auth/login"),
                Map.of("email", email, "password", "wrongpass"),
                null, null);
        assertEquals(400, read(r).get("code").asInt());
    }
}
