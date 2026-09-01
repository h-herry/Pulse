package com.pulse.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    private static final String STRONG_SECRET =
            "a-strong-secret-key-at-least-32-bytes-long-1234567890";

    private JwtUtils newUtils(String secret) {
        return new JwtUtils(secret, 86400000L, 604800000L);
    }

    @Test
    void signAndValidateRoundtrip() {
        JwtUtils utils = newUtils(STRONG_SECRET);
        String token = utils.generateToken(1L, "a@b.com");
        assertTrue(utils.validateToken(token));
        assertEquals(1L, utils.getUserIdFromToken(token));
        assertEquals("a@b.com", utils.getEmailFromToken(token));
    }

    @Test
    void rejectsTamperedToken() {
        JwtUtils utils = newUtils(STRONG_SECRET);
        String token = utils.generateToken(1L, "a@b.com") + "x";
        assertFalse(utils.validateToken(token));
    }

    @Test
    void shortSecretFailsFast() {
        // 密钥不足 32 字节应在构造期直接失败（fail-fast），避免弱密钥上线
        assertThrows(IllegalStateException.class, () -> newUtils("short"));
    }
}
