package com.pulse.common;

import java.security.SecureRandom;

/**
 * 临时密码生成工具。
 *
 * 用于管理后台重置密码（R-014）与忘记密码（R-025）场景，
 * 生成不含易混淆字符的随机密码，降低用户抄写错误。
 */
public final class PasswordUtils {

    /** 去掉了 0/O、1/l 等易混淆字符 */
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RAND = new SecureRandom();

    private PasswordUtils() {
    }

    /**
     * 生成指定长度的随机密码。
     *
     * @param length 密码长度（建议 >= 12）
     * @return 随机密码字符串
     */
    public static String generateTempPassword(int length) {
        int n = Math.max(8, length);
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(CHARS.charAt(RAND.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
