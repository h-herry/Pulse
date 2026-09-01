package com.pulse.controller;

import com.pulse.common.Result;
import com.pulse.common.TenantContext;
import com.pulse.dto.IterationRequest;
import com.pulse.dto.LlmCredentials;
import com.pulse.entity.AuditLog;
import com.pulse.entity.Iteration;
import com.pulse.service.AiConfigService;
import com.pulse.service.AiService;
import com.pulse.service.AuditService;
import com.pulse.service.IterationService;
import com.pulse.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 迭代管理端点（路径 /api/v1/projects/{pid}/iterations）。
 * 职责：迭代的增删改查、聚合统计、复盘生成，以及基于用户级 AI Key 的复盘/周期摘要草稿预览与保存。
 * 安全约束：迭代须归属当前项目与团队（pid + teamId 双重校验，越权抛 BusinessException）；
 * AI 预览依赖用户自身配置的 LLM Key（per-user，绝不跨用户调用），无 Key 时优雅降级提示。
 */
@RestController
@RequestMapping("/api/v1/projects/{pid}/iterations")
@Tag(name = "迭代", description = "迭代管理")
public class IterationController {

    private final IterationService iterationService;
    private final ReportService reportService;
    private final AiConfigService aiConfigService;
    private final AiService aiService;
    private final AuditService auditService;

    public IterationController(IterationService iterationService, ReportService reportService,
                               AiConfigService aiConfigService, AiService aiService, AuditService auditService) {
        this.iterationService = iterationService;
        this.reportService = reportService;
        this.aiConfigService = aiConfigService;
        this.aiService = aiService;
        this.auditService = auditService;
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID），迭代须归属当前项目。
     * 功能：获取指定项目下的迭代列表。
     * 关键入参：pid（路径，项目 ID）。
     */
    @GetMapping
    @Operation(summary = "获取迭代列表")
    public Result<List<Iteration>> listIterations(@PathVariable Long pid) {
        return Result.ok(iterationService.listIterations(pid));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID）。
     * 功能：获取迭代聚合统计（完成率/逾期率/速度/燃尽/状态分布），先校验迭代归属，越权抛 BusinessException。
     * 关键入参：pid（路径，项目 ID）；id（路径，迭代 ID）。
     */
    @GetMapping("/{id}/stats")
    @Operation(summary = "迭代聚合统计（完成率/逾期率/速度/燃尽/状态分布）")
    public Result<Map<String, Object>> iterationStats(@PathVariable Long pid, @PathVariable Long id) {
        Iteration iter = iterationService.getById(id);
        if (!iter.getProjectId().equals(pid) || !iter.getTeamId().equals(TenantContext.getTenantId())) {
            throw new com.pulse.common.BusinessException("迭代不存在");
        }
        return Result.ok(reportService.getIterationStats(id));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID）。
     * 功能：获取单个迭代详情，先校验迭代归属，越权抛 BusinessException。
     * 关键入参：pid（路径，项目 ID）；id（路径，迭代 ID）。
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取单个迭代")
    public Result<Iteration> getOne(@PathVariable Long pid, @PathVariable Long id) {
        Iteration iter = iterationService.getById(id);
        if (!iter.getProjectId().equals(pid) || !iter.getTeamId().equals(TenantContext.getTenantId())) {
            throw new com.pulse.common.BusinessException("迭代不存在");
        }
        return Result.ok(iter);
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID）。
     * 功能：生成/重生成迭代复盘内容（模板版，不依赖 AI）。
     * 关键入参：pid（路径，项目 ID）；id（路径，迭代 ID）。
     */
    @PostMapping("/{id}/retro")
    @Operation(summary = "生成/重生成迭代复盘")
    public Result<Iteration> generateRetro(@PathVariable Long pid, @PathVariable Long id) {
        Iteration iter = iterationService.getById(id);
        if (!iter.getProjectId().equals(pid) || !iter.getTeamId().equals(TenantContext.getTenantId())) {
            throw new com.pulse.common.BusinessException("迭代不存在");
        }
        return Result.ok(iterationService.generateRetro(id));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID）。
     * 功能：在当前项目下创建迭代。
     * 关键入参：pid（路径，项目 ID）；IterationRequest（迭代定义）。
     */
    @PostMapping
    @Operation(summary = "创建迭代")
    public Result<Iteration> createIteration(@PathVariable Long pid, @Valid @RequestBody IterationRequest req) {
        return Result.ok(iterationService.createIteration(pid, req));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID）。
     * 功能：更新指定迭代。
     * 关键入参：pid（路径，项目 ID）；id（路径，迭代 ID）；IterationRequest（更新内容）。
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新迭代")
    public Result<Iteration> updateIteration(@PathVariable Long pid, @PathVariable Long id,
                                             @Valid @RequestBody IterationRequest req) {
        return Result.ok(iterationService.updateIteration(id, req));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID）。
     * 功能：删除指定迭代。
     * 关键入参：pid（路径，项目 ID）；id（路径，迭代 ID）。
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除迭代")
    public Result<String> deleteIteration(@PathVariable Long pid, @PathVariable Long id) {
        iterationService.deleteIteration(id);
        return Result.ok("已删除");
    }

    // ============================ AI 增强（per-user Key） ============================

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID）；AI 预览依赖用户自身配置的 LLM Key。
     * 功能：基于 AI 生成迭代复盘草稿（不落库，仅预览）；无 Key 或调用失败时优雅降级提示。
     * 关键入参：pid（路径，项目 ID）；id（路径，迭代 ID）；LLM 凭证取自当前用户配置。
     */
    @PostMapping("/{id}/retro/ai-preview")
    @Operation(summary = "基于 AI 生成迭代复盘草稿（不落库，需用户已配置 Key）")
    public Result<Map<String, Object>> aiRetroPreview(@PathVariable Long pid, @PathVariable Long id) {
        Iteration iter = iterationService.getById(id);
        guard(pid, iter);
        Long userId = TenantContext.getUserId();
        LlmCredentials creds = aiConfigService.resolveCredentials(userId);
        if (creds == null) {
            auditService.record(AuditLog.Action.AI_INVOKE_FAIL, AuditLog.Target.AI, userId,
                    "场景=迭代复盘草稿; iterationId=" + id + "; 原因=未配置AI Key");
            return Result.ok(Map.of("available", false,
                    "message", "尚未配置 AI Key，请在「账户 → AI 设置」中填写后使用。"));
        }
        String statsText = iterationService.buildStatsText(id);
        String draft = aiService.generateRetroDraft(statsText, iter.getRetroContent(), creds);
        if (draft == null) {
            auditService.record(AuditLog.Action.AI_INVOKE_FAIL, AuditLog.Target.AI, userId,
                    "场景=迭代复盘草稿; iterationId=" + id + "; 原因=调用失败或返回格式异常");
            return Result.ok(Map.of("available", false, "draft", "",
                    "message", "AI 调用失败，请检查 Key 或稍后重试，也可直接生成模板复盘。"));
        }
        auditService.record(AuditLog.Action.AI_INVOKE_SUCCESS, AuditLog.Target.AI, userId,
                "场景=迭代复盘草稿; iterationId=" + id + "; 输出长度=" + draft.length());
        return Result.ok(Map.of("available", true, "draft", draft));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID）；AI 预览依赖用户自身配置的 LLM Key。
     * 功能：基于 AI 生成周期（周报）摘要草稿（不落库，仅预览）；无 Key 或调用失败时优雅降级提示。
     * 关键入参：pid（路径，项目 ID）；id（路径，迭代 ID）；LLM 凭证取自当前用户配置。
     */
    @PostMapping("/{id}/report/ai-preview")
    @Operation(summary = "基于 AI 生成周期（周报）摘要（不落库，需用户已配置 Key）")
    public Result<Map<String, Object>> aiReportPreview(@PathVariable Long pid, @PathVariable Long id) {
        Iteration iter = iterationService.getById(id);
        guard(pid, iter);
        Long userId = TenantContext.getUserId();
        LlmCredentials creds = aiConfigService.resolveCredentials(userId);
        if (creds == null) {
            auditService.record(AuditLog.Action.AI_INVOKE_FAIL, AuditLog.Target.AI, userId,
                    "场景=周期摘要; iterationId=" + id + "; 原因=未配置AI Key");
            return Result.ok(Map.of("available", false,
                    "message", "尚未配置 AI Key，请在「账户 → AI 设置」中填写后使用。"));
        }
        String statsText = iterationService.buildStatsText(id);
        String summary = aiService.generateReportSummary(statsText, creds);
        if (summary == null) {
            auditService.record(AuditLog.Action.AI_INVOKE_FAIL, AuditLog.Target.AI, userId,
                    "场景=周期摘要; iterationId=" + id + "; 原因=调用失败或返回格式异常");
            return Result.ok(Map.of("available", false, "summary", "",
                    "message", "AI 调用失败，请检查 Key 或稍后重试。"));
        }
        auditService.record(AuditLog.Action.AI_INVOKE_SUCCESS, AuditLog.Target.AI, userId,
                "场景=周期摘要; iterationId=" + id + "; 输出长度=" + summary.length());
        return Result.ok(Map.of("available", true, "summary", summary));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID）。
     * 功能：保存迭代复盘内容（AI 草稿确认或手动编辑后落库）。
     * 关键入参：pid（路径，项目 ID）；id（路径，迭代 ID）；body.content（复盘正文，缺省为空串）。
     */
    @PutMapping("/{id}/retro")
    @Operation(summary = "保存迭代复盘内容（AI 草稿确认或手动编辑）")
    public Result<Iteration> saveRetro(@PathVariable Long pid, @PathVariable Long id,
                                       @RequestBody Map<String, String> body) {
        Iteration iter = iterationService.getById(id);
        guard(pid, iter);
        String content = body.getOrDefault("content", "");
        return Result.ok(iterationService.saveRetroContent(id, content));
    }

    private void guard(Long pid, Iteration iter) {
        if (iter == null || !iter.getProjectId().equals(pid)
                || !iter.getTeamId().equals(TenantContext.getTenantId())) {
            throw new com.pulse.common.BusinessException("迭代不存在");
        }
    }
}
