package com.pulse.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordUtilsTest {

    @Test
    void generatesRequestedLength() {
        for (int len = 8; len <= 20; len++) {
            assertEquals(len, PasswordUtils.generateTempPassword(len).length());
        }
    }

    @Test
    void excludesAmbiguousChars() {
        String pwd = PasswordUtils.generateTempPassword(64);
        for (char c : pwd.toCharArray()) {
            assertFalse(c == '0' || c == 'O' || c == '1' || c == 'l',
                    "口令含易混淆字符: " + c);
        }
    }
}
