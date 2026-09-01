package com.pulse.controller;

import com.pulse.common.BusinessException;
import com.pulse.common.Result;
import com.pulse.common.TenantContext;
import com.pulse.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 团队报表端点（路径 /api/v1/teams/{teamId}/reports）。
 * 职责：团队维度概览、成员效能、迭代报表、燃尽趋势、CSV 导出，以及多维报表（速度/周期时间/状态分布/优先级分布，需求 R-008）。
 * 租户约束：teamId 由 X-Team-ID 指定；多维报表支持按 projectId 进一步过滤（resolveProjectId 在团队内解析，越权项目将被忽略）。
 */
@RestController
@RequestMapping("/api/v1/teams/{teamId}/reports")
@io.swagger.v3.oas.annotations.tags.Tag(name = "报表", description = "报表统计")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * 防御性校验：路径 {teamId} 必须与当前 X-Team-ID 上下文一致（系统管理员上帝视角豁免）。
     * 服务层实际以 TenantContext 隔离数据，此处仅闭合「路径 teamId 为死参数」的契约歧义，
     * 防止前端传错团队 ID 却拿到其它团队数据。
     */
    private void assertTeamMatches(Long teamId) {
        if (TenantContext.isSystemAdmin()) {
            return;
        }
        Long current = TenantContext.getTenantId();
        if (current == null || !current.equals(teamId)) {
            throw new BusinessException("团队标识不一致");
        }
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID = teamId）。
     * 功能：获取团队概览统计。
     * 关键入参：teamId（路径，团队 ID）。
     */
    @GetMapping("/overview")
    @Operation(summary = "概览统计")
    public Result<Map<String, Object>> overview(@PathVariable Long teamId) {
        assertTeamMatches(teamId);
        return Result.ok(reportService.getOverview(teamId));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID = teamId）。
     * 功能：获取团队成员效能统计。
     * 关键入参：teamId（路径，团队 ID）。
     */
    @GetMapping("/member-efficiency")
    @Operation(summary = "成员效能")
    public Result<Map<String, Object>> memberEfficiency(@PathVariable Long teamId) {
        assertTeamMatches(teamId);
        return Result.ok(reportService.getMemberEfficiency(teamId));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID = teamId）。
     * 功能：获取指定迭代的报表数据。
     * 关键入参：teamId（路径，团队 ID）；iid（路径，迭代 ID）。
     */
    @GetMapping("/iterations/{iid}")
    @Operation(summary = "迭代报表")
    public Result<Map<String, Object>> iterationReport(@PathVariable Long teamId, @PathVariable Long iid) {
        assertTeamMatches(teamId);
        return Result.ok(reportService.getIterationReport(iid));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID = teamId）。
     * 功能：获取燃尽图趋势数据，支持按 projectId 在团队内过滤。
     * 关键入参：teamId（路径）；projectId（可选，团队内项目过滤）。
     */
    @GetMapping("/trend")
    @Operation(summary = "燃尽图趋势")
    public Result<Map<String, Object>> trend(@PathVariable Long teamId,
                                              @RequestParam(required = false) Long projectId) {
        assertTeamMatches(teamId);
        return Result.ok(reportService.getTrend(reportService.resolveProjectId(teamId, projectId)));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID = teamId）。
     * 功能：导出团队任务为 CSV 文件（作为附件下载）。
     * 关键入参：teamId（路径）；format（默认 csv）。
     */
    @GetMapping("/tasks/export")
    @Operation(summary = "导出任务 CSV")
    public ResponseEntity<Resource> exportTasks(@PathVariable Long teamId,
                                                 @RequestParam(defaultValue = "csv") String format) {
        assertTeamMatches(teamId);
        Resource resource = reportService.exportTasks(teamId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tasks.csv")
                .body(resource);
    }

    // ===== R-008: 多维报表 =====

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID = teamId）。
     * 功能：速度图——各迭代已完成故事点（需求 R-008 多维报表），支持 projectId 过滤。
     * 关键入参：teamId（路径）；projectId（可选）。
     */
    @GetMapping("/velocity")
    @Operation(summary = "速度图：各迭代已完成故事点")
    public Result<List<Map<String, Object>>> velocity(@PathVariable Long teamId,
                                                       @RequestParam(required = false) Long projectId) {
        assertTeamMatches(teamId);
        return Result.ok(reportService.getVelocity(reportService.resolveProjectId(teamId, projectId)));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID = teamId）。
     * 功能：周期时间分布统计（需求 R-008 多维报表），支持 projectId 过滤。
     * 关键入参：teamId（路径）；projectId（可选）。
     */
    @GetMapping("/cycle-time")
    @Operation(summary = "周期时间分布")
    public Result<Map<String, Object>> cycleTime(@PathVariable Long teamId,
                                                 @RequestParam(required = false) Long projectId) {
        assertTeamMatches(teamId);
        return Result.ok(reportService.getCycleTime(reportService.resolveProjectId(teamId, projectId)));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID = teamId）。
     * 功能：任务状态分布统计（需求 R-008 多维报表），支持 projectId 过滤。
     * 关键入参：teamId（路径）；projectId（可选）。
     */
    @GetMapping("/status-distribution")
    @Operation(summary = "状态分布")
    public Result<Map<String, Object>> statusDistribution(@PathVariable Long teamId,
                                                          @RequestParam(required = false) Long projectId) {
        assertTeamMatches(teamId);
        return Result.ok(reportService.getStatusDistribution(reportService.resolveProjectId(teamId, projectId)));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID = teamId）。
     * 功能：任务优先级分布统计（需求 R-008 多维报表），支持 projectId 过滤。
     * 关键入参：teamId（路径）；projectId（可选）。
     */
    @GetMapping("/priority-distribution")
    @Operation(summary = "优先级分布")
    public Result<Map<String, Object>> priorityDistribution(@PathVariable Long teamId,
                                                            @RequestParam(required = false) Long projectId) {
        assertTeamMatches(teamId);
        return Result.ok(reportService.getPriorityDistribution(reportService.resolveProjectId(teamId, projectId)));
    }
}
