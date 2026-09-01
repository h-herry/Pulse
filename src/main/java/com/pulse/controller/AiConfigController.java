package com.pulse.controller;

import com.pulse.common.Result;
import com.pulse.common.TenantContext;
import com.pulse.dto.AiConfigSaveRequest;
import com.pulse.dto.UserLlmConfigView;
import com.pulse.entity.AuditLog;
import com.pulse.service.AiConfigService;
import com.pulse.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户级 AI 配置端点。
 *
 * 路径挂在 /api/v1/auth/ 下，复用 TenantInterceptor 对 /api/v1/auth 前缀的豁免，
 * 因此不要求 X-Team-ID（配置是 per-user 而非 per-team）。
 * 鉴权由 JwtAuthFilter 注入的 TenantContext.getUserId() 保证：无 token 时 userId 为 null，返回 401。
 */
@RestController
@RequestMapping("/api/v1/auth/ai-config")
@Tag(name = "AI 配置", description = "用户级 LLM API Key 管理（per-user，自托管数据主权）")
public class AiConfigController {

    private final AiConfigService aiConfigService;
    private final AuditService auditService;

    public AiConfigController(AiConfigService aiConfigService, AuditService auditService) {
        this.aiConfigService = aiConfigService;
        this.auditService = auditService;
    }

    @PostMapping
    @Operation(summary = "保存当前用户的 LLM 配置")
    public Result<Void> save(@RequestBody AiConfigSaveRequest req) {
        Long userId = TenantContext.getUserId();
        if (userId == null) return Result.fail(401, "未登录");
        aiConfigService.save(userId, req.getBaseUrl(), req.getApiKey(), req.getModel(), req.isEnabled());
        // 敏感操作审计：记录配置变更，但绝不写入明文 Key；仅记脱敏后的元信息
        auditService.record(AuditLog.Action.AI_KEY_SAVE, AuditLog.Target.AI, userId,
                String.format("baseUrl=%s; model=%s; enabled=%s", req.getBaseUrl(), req.getModel(), req.isEnabled()));
        return Result.ok();
    }

    @GetMapping
    @Operation(summary = "读取当前用户的 LLM 配置（脱敏）")
    public Result<UserLlmConfigView> get() {
        Long userId = TenantContext.getUserId();
        if (userId == null) return Result.fail(401, "未登录");
        return Result.ok(aiConfigService.getMasked(userId));
    }

    @DeleteMapping
    @Operation(summary = "删除当前用户的 LLM 配置")
    public Result<Void> delete() {
        Long userId = TenantContext.getUserId();
        if (userId == null) return Result.fail(401, "未登录");
        aiConfigService.delete(userId);
        auditService.record(AuditLog.Action.AI_KEY_DELETE, AuditLog.Target.AI, userId, "删除 AI 配置");
        return Result.ok();
    }

    @PostMapping("/test")
    @Operation(summary = "测试当前用户的 LLM 配置是否可用（不依赖团队上下文）")
    public Result<Map<String, Object>> test() {
        Long userId = TenantContext.getUserId();
        if (userId == null) return Result.fail(401, "未登录");
        Map<String, Object> r = aiConfigService.test(userId);
        boolean ok = Boolean.TRUE.equals(r.get("ok"));
        // 连通性探测同样属于 AI 调用，纳入可观测审计
        auditService.record(ok ? AuditLog.Action.AI_INVOKE_SUCCESS : AuditLog.Action.AI_INVOKE_FAIL,
                AuditLog.Target.AI, userId, "场景=配置连通性测试; ok=" + ok);
        return Result.ok(r);
    }
}
