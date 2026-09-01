package com.pulse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pulse.common.BusinessException;
import com.pulse.common.EncryptionUtil;
import com.pulse.dto.LlmCredentials;
import com.pulse.dto.UserLlmConfigView;
import com.pulse.entity.UserLlmConfig;
import com.pulse.mapper.UserLlmConfigMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户级 LLM 配置服务：负责 per-user Key 的加密存储、脱敏读取与凭证解析。
 *
 * 安全边界：
 * - 所有密钥以 AES-GCM 加密落库，明文仅在本服务内存中短暂存在。
 * - 配置按 userId 强隔离，绝不跨用户读取。
 * - resolveCredentials 仅返回「已启用且有配置」的凭证；否则返回 null，
 *   调用方据此回退到本地启发式，保证功能降级可用。
 */
@Service
public class AiConfigService {

    private final UserLlmConfigMapper mapper;
    private final EncryptionUtil encryptionUtil;

    @Value("${ai.base-url:https://api.openai.com/v1}")
    private String defaultBaseUrl;

    @Value("${ai.model:gpt-4o-mini}")
    private String defaultModel;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    public AiConfigService(UserLlmConfigMapper mapper, EncryptionUtil encryptionUtil) {
        this.mapper = mapper;
        this.encryptionUtil = encryptionUtil;
    }

    /** 保存/更新当前用户的 LLM 配置（apiKey 加密落库；更新时留空表示保留原密钥） */
    public void save(Long userId, String baseUrl, String apiKey, String model, boolean enabled) {
        if (userId == null) throw new BusinessException(401, "未登录");

        UserLlmConfig existing = mapper.selectOne(
                new LambdaQueryWrapper<UserLlmConfig>().eq(UserLlmConfig::getUserId, userId));
        if (existing == null && (apiKey == null || apiKey.isBlank())) {
            throw new BusinessException(400, "API Key 不能为空");
        }

        UserLlmConfig cfg = existing != null ? existing : new UserLlmConfig();
        cfg.setUserId(userId);
        cfg.setBaseUrl(baseUrl != null && !baseUrl.isBlank() ? baseUrl : defaultBaseUrl);
        cfg.setModel(model != null && !model.isBlank() ? model : defaultModel);
        if (apiKey != null && !apiKey.isBlank()) {
            cfg.setApiKeyEnc(encryptionUtil.encrypt(apiKey));
        } else if (existing != null) {
            cfg.setApiKeyEnc(existing.getApiKeyEnc()); // 更新时未提供新密钥 → 保留原加密密钥
        }
        cfg.setEnabled(enabled);
        if (existing != null) mapper.updateById(cfg);
        else mapper.insert(cfg);
    }

    /** 读取脱敏配置（不返回明文/密文 Key） */
    public UserLlmConfigView getMasked(Long userId) {
        if (userId == null) throw new BusinessException(401, "未登录");
        UserLlmConfig cfg = mapper.selectOne(
                new LambdaQueryWrapper<UserLlmConfig>().eq(UserLlmConfig::getUserId, userId));
        if (cfg == null) return null;
        UserLlmConfigView v = new UserLlmConfigView();
        v.setBaseUrl(cfg.getBaseUrl());
        v.setModel(cfg.getModel());
        v.setEnabled(cfg.getEnabled());
        v.setConfigured(true);
        v.setKeyMasked("已配置 (密文 " + (cfg.getApiKeyEnc() != null ? cfg.getApiKeyEnc().length() : 0) + " 字符)");
        return v;
    }

    /** 删除当前用户的 LLM 配置 */
    public void delete(Long userId) {
        if (userId == null) throw new BusinessException(401, "未登录");
        mapper.delete(new LambdaQueryWrapper<UserLlmConfig>().eq(UserLlmConfig::getUserId, userId));
    }

    /**
     * 解析当前用户的可调用凭证；未配置或未启用返回 null。
     * 解密失败时抛错（由调用方捕获并回退）。
     */
    public LlmCredentials resolveCredentials(Long userId) {
        if (userId == null) return null;
        UserLlmConfig cfg = mapper.selectOne(
                new LambdaQueryWrapper<UserLlmConfig>().eq(UserLlmConfig::getUserId, userId));
        if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())) return null;
        LlmCredentials c = new LlmCredentials();
        c.setBaseUrl(cfg.getBaseUrl() != null ? cfg.getBaseUrl() : defaultBaseUrl);
        c.setModel(cfg.getModel() != null ? cfg.getModel() : defaultModel);
        c.setApiKey(encryptionUtil.decrypt(cfg.getApiKeyEnc()));
        return c;
    }

    /**
     * 用当前用户凭证发起一次最小 LLM 调用，验证 Key 与服务商是否可用。
     * 不依赖团队上下文（端点挂在 /auth 下），返回 { ok, message } 供前端展示。
     */
    public Map<String, Object> test(Long userId) {
        Map<String, Object> r = new HashMap<>();
        LlmCredentials c = resolveCredentials(userId);
        if (c == null) {
            r.put("ok", false);
            r.put("message", "尚未配置或启用 API Key");
            return r;
        }
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", c.getModel());
            body.put("temperature", 0);
            body.put("max_tokens", 8);
            ArrayNode messages = MAPPER.createArrayNode();
            ObjectNode m = MAPPER.createObjectNode();
            m.put("role", "user");
            m.put("content", "ping");
            messages.add(m);
            body.set("messages", messages);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(c.getBaseUrl().replaceAll("/$", "") + "/chat/completions"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + c.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                r.put("ok", true);
                r.put("message", "连接成功，模型可用");
            } else {
                r.put("ok", false);
                r.put("message", "HTTP " + resp.statusCode() + ": " + truncate(resp.body()));
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            r.put("ok", false);
            r.put("message", e.getClass().getSimpleName() + ": " + (msg != null ? truncate(msg) : "未知错误"));
        }
        return r;
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
