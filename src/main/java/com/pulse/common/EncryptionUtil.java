package com.pulse.common;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 对称加密工具，用于安全存储「用户自带的 LLM API Key」。
 *
 * 主密钥来自环境变量 PULSE_ENCRYPTION_KEY（Spring 宽松绑定到 pulse.encryption-key），
 * 需为 32 字节：支持 64 位十六进制 或 Base64 编码。
 *
 * 主密钥是用户 LLM Key 安全存储的运行前提：**缺失或非法时 fail-fast**，
 * 即在应用启动时由 init() 直接抛异常中止容器启动，避免带病运行导致
 * 密钥无法加解密（否则任何保存/读取 Key 操作都会运行时 500）。
 */
@Component
public class EncryptionUtil {

    private static final Logger log = LoggerFactory.getLogger(EncryptionUtil.class);
    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;     // GCM 推荐 96-bit IV
    private static final int TAG_LEN = 128;   // GCM 认证标签位长
    private static final SecureRandom RAND = new SecureRandom();

    @Value("${pulse.encryption-key:}")
    private String rawKey;

    private javax.crypto.SecretKey secretKey;
    private boolean configured = false;

    @PostConstruct
    public void init() {
        if (rawKey == null || rawKey.isBlank()) {
            throw new IllegalStateException(
                    "PULSE_ENCRYPTION_KEY 未配置：用户 LLM Key 将无法安全存储。"
                    + "请在环境变量中设置 32 字节主密钥（64 位十六进制 或 Base64），"
                    + "缺失将导致应用无法启动（fail-fast）。");
        }
        try {
            byte[] kb = decodeKey(rawKey.trim());
            if (kb.length != 32) {
                throw new IllegalArgumentException("主密钥必须是 32 字节（64 位十六进制 或 Base64 编码）");
            }
            secretKey = new SecretKeySpec(kb, "AES");
            configured = true;
            log.info("EncryptionUtil 初始化成功（AES-256-GCM）");
        } catch (Exception e) {
            throw new IllegalStateException("PULSE_ENCRYPTION_KEY 格式非法："
                    + e.getMessage() + "。主密钥配置错误将导致应用无法启动（fail-fast）。", e);
        }
    }

    private static byte[] decodeKey(String s) {
        if (s.matches("^[0-9a-fA-F]{64}$")) {
            byte[] out = new byte[32];
            for (int i = 0; i < 32; i++) {
                out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
            }
            return out;
        }
        return Base64.getDecoder().decode(s);
    }

    private void ensureConfigured() {
        if (!configured) {
            throw new IllegalStateException("服务端未配置加密主密钥 (PULSE_ENCRYPTION_KEY)，无法加解密用户密钥");
        }
    }

    /** 明文 -> 密文（格式：base64(iv):base64(ciphertext)） */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        ensureConfigured();
        try {
            byte[] iv = new byte[IV_LEN];
            RAND.nextBytes(iv);
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LEN, iv));
            byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(ct);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败: " + e.getMessage(), e);
        }
    }

    /** 密文 -> 明文 */
    public String decrypt(String stored) {
        if (stored == null) return null;
        ensureConfigured();
        try {
            int idx = stored.indexOf(':');
            if (idx < 0) throw new IllegalArgumentException("密文格式非法");
            byte[] iv = Base64.getDecoder().decode(stored.substring(0, idx));
            byte[] ct = Base64.getDecoder().decode(stored.substring(idx + 1));
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LEN, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("解密失败: " + e.getMessage(), e);
        }
    }

    public boolean isConfigured() {
        return configured;
    }
}
