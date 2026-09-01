package com.pulse.controller;

import com.pulse.common.Result;
import com.pulse.common.TenantContext;
import com.pulse.dto.AiParseRequest;
import com.pulse.dto.AiSummaryRequest;
import com.pulse.dto.AssignmentSuggestRequest;
import com.pulse.dto.AssignmentSuggestion;
import com.pulse.dto.LlmCredentials;
import com.pulse.dto.MemberWorkload;
import com.pulse.dto.ParsedTask;
import com.pulse.entity.AuditLog;
import com.pulse.entity.Task;
import com.pulse.service.AiConfigService;
import com.pulse.service.AiService;
import com.pulse.service.AuditService;
import com.pulse.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI", description = "自然语言建任务 / 智能概述 / 智能分派")
public class AiController {

    private final AiService aiService;
    private final AiConfigService aiConfigService;
    private final TaskService taskService;
    private final AuditService auditService;

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    public AiController(AiService aiService, AiConfigService aiConfigService, TaskService taskService, AuditService auditService) {
        this.aiService = aiService;
        this.aiConfigService = aiConfigService;
        this.taskService = taskService;
        this.auditService = auditService;
    }

    @PostMapping("/parse")
    @Operation(summary = "解析一句话为任务清单")
    public Result<List<ParsedTask>> parse(@RequestBody AiParseRequest req) {
        Long userId = TenantContext.getUserId();
        boolean decryptFailed = false;
        LlmCredentials creds;
        try {
            creds = aiConfigService.resolveCredentials(userId);
        } catch (Exception e) {
            decryptFailed = true;
            log.warn("AI Key 解密失败，回退本地解析：{}", e.getMessage());
            creds = null;
        }
        if (creds == null) {
            auditService.record(AuditLog.Action.AI_INVOKE_FAIL, AuditLog.Target.AI, userId,
                    decryptFailed ? "场景=自然语言解析; 原因=密钥解密失败，已回退本地解析"
                                 : "场景=自然语言解析; 原因=未配置AI Key，已回退本地解析");
            return Result.ok(aiService.parse(req, null)); // 本地启发式降级，始终可用
        }
        auditService.record(AuditLog.Action.AI_INVOKE_SUCCESS, AuditLog.Target.AI, userId, "场景=自然语言解析");
        return Result.ok(aiService.parse(req, creds));
    }

    @PostMapping("/summary")
    @Operation(summary = "基于任务清单生成智能概述")
    public Result<Map<String, String>> summary(@RequestBody AiSummaryRequest req) {
        Long userId = TenantContext.getUserId();
        boolean decryptFailed = false;
        LlmCredentials creds;
        try {
            creds = aiConfigService.resolveCredentials(userId);
        } catch (Exception e) {
            decryptFailed = true;
            log.warn("AI Key 解密失败，回退本地概述：{}", e.getMessage());
            creds = null;
        }
        if (creds == null) {
            auditService.record(AuditLog.Action.AI_INVOKE_FAIL, AuditLog.Target.AI, userId,
                    decryptFailed ? "场景=智能概述; 原因=密钥解密失败，已回退本地概述"
                                 : "场景=智能概述; 原因=未配置AI Key，已回退本地概述");
            return Result.ok(Map.of("summary", aiService.summarize(req, null)));
        }
        auditService.record(AuditLog.Action.AI_INVOKE_SUCCESS, AuditLog.Target.AI, userId, "场景=智能概述");
        return Result.ok(Map.of("summary", aiService.summarize(req, creds)));
    }

    @PostMapping("/assignment/suggest")
    @Operation(summary = "智能分派建议（草稿，不落库）")
    public Result<Map<String, Object>> suggestAssignment(@RequestBody AssignmentSuggestRequest req) {
        Long teamId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (req == null) return Result.fail(400, "请求体不能为空");
        if (req.getTaskId() == null && (req.getTitle() == null || req.getTitle().isBlank())) {
            return Result.fail(400, "需提供待分派任务（taskId 或 title）");
        }

        // 若提供 taskId，校验归属并补全 brief（若请求未填）
        if (req.getTaskId() != null) {
            Task t = taskService.getTaskIfInTeam(req.getTaskId(), teamId);
            if (t == null) return Result.fail(404, "任务不存在或无权限");
            if (req.getTitle() == null) req.setTitle(t.getTitle());
            if (req.getPriority() == null) req.setPriority(t.getPriority());
            if (req.getStoryPoints() == null) req.setStoryPoints(t.getStoryPoints());
            if (req.getDescription() == null) req.setDescription(t.getDescription());
            if (req.getProjectId() == null) req.setProjectId(t.getProjectId());
        }

        // 计算成员负载（按项目过滤，实现项目内均衡）
        List<MemberWorkload> members = taskService.computeMemberWorkloads(teamId, req.getProjectId());
        if (members.isEmpty()) {
            return Result.ok(Map.of("available", false, "suggestions", Collections.emptyList(),
                    "message", "当前团队暂无可分派成员"));
        }

        boolean decryptFailed = false;
        LlmCredentials creds;
        try {
            creds = aiConfigService.resolveCredentials(userId);
        } catch (Exception e) {
            decryptFailed = true;
            log.warn("AI Key 解密失败，降级手动分派：{}", e.getMessage());
            creds = null;
        }
        if (creds == null) {
            auditService.record(AuditLog.Action.AI_INVOKE_FAIL, AuditLog.Target.AI, userId,
                    decryptFailed ? "场景=智能分派; 原因=密钥解密失败"
                                 : "场景=智能分派; 原因=未配置AI Key");
            return Result.ok(Map.of("available", false, "suggestions", Collections.emptyList(),
                    "message", decryptFailed ? "用户密钥解密失败，无法生成智能分派建议"
                                             : "尚未配置 AI Key，无法生成智能分派建议"));
        }

        List<AssignmentSuggestion> suggestions = aiService.suggestAssignees(req, members, creds);
        if (suggestions == null) {
            auditService.record(AuditLog.Action.AI_INVOKE_FAIL, AuditLog.Target.AI, userId,
                    "场景=智能分派; 原因=调用失败或返回格式异常");
            return Result.ok(Map.of("available", false, "suggestions", Collections.emptyList(),
                    "message", "AI 未能生成建议（调用失败或返回格式异常），可手动分派"));
        }
        auditService.record(AuditLog.Action.AI_INVOKE_SUCCESS, AuditLog.Target.AI, userId,
                String.format("场景=智能分派; taskId=%s; 建议数=%d", req.getTaskId(), suggestions.size()));
        return Result.ok(Map.of("available", true, "suggestions", suggestions, "message", ""));
    }
}
