package com.pulse.controller;

import com.pulse.common.BusinessException;
import com.pulse.common.Result;
import com.pulse.common.TenantContext;
import com.pulse.entity.Project;
import com.pulse.mapper.ProjectMapper;
import com.pulse.service.BurndownService;
import com.pulse.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 项目级统计报表端点（路径 /api/v1/projects/{pid}/reports）。
 * 职责：提供项目任务概览、成员任务分布、燃尽图趋势数据与手动快照生成。
 * 安全约束：经 assertOwnsProject 校验项目归属当前租户（TenantContext.tenantId），防止越权读取跨团队报表数据。
 */
@RestController
@RequestMapping("/api/v1/projects/{pid}/reports")
@Tag(name = "项目报表", description = "项目级统计报表（概览 / 燃尽图 / 成员分布）")
public class ProjectReportController {

    private final ReportService reportService;
    private final BurndownService burndownService;
    private final ProjectMapper projectMapper;

    public ProjectReportController(ReportService reportService,
                                   BurndownService burndownService,
                                   ProjectMapper projectMapper) {
        this.reportService = reportService;
        this.burndownService = burndownService;
        this.projectMapper = projectMapper;
    }

    /** 校验项目归属当前租户，防止越权读取 */
    private void assertOwnsProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || !project.getTeamId().equals(TenantContext.getTenantId())) {
            throw new BusinessException("项目不存在");
        }
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID，经 assertOwnsProject 校验）。
     * 功能：获取项目任务概览统计。
     * 关键入参：pid（路径，项目 ID，须归属当前租户）。
     */
    @GetMapping("/stats")
    @Operation(summary = "项目任务概览")
    public Result<Map<String, Object>> stats(@PathVariable Long pid) {
        assertOwnsProject(pid);
        return Result.ok(reportService.getProjectStats(pid));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID，经 assertOwnsProject 校验）。
     * 功能：获取项目成员任务分布。
     * 关键入参：pid（路径，项目 ID，须归属当前租户）。
     */
    @GetMapping("/assignee")
    @Operation(summary = "成员任务分布")
    public Result<List<Map<String, Object>>> assignee(@PathVariable Long pid) {
        assertOwnsProject(pid);
        return Result.ok(reportService.getAssigneeStats(pid));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID，经 assertOwnsProject 校验）。
     * 功能：获取燃尽图趋势数据。
     * 关键入参：pid（路径，项目 ID，须归属当前租户）。
     */
    @GetMapping("/burndown")
    @Operation(summary = "燃尽图趋势数据")
    public Result<List<Map<String, Object>>> burndown(@PathVariable Long pid) {
        assertOwnsProject(pid);
        return Result.ok(burndownService.getBurndownData(pid));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID，经 assertOwnsProject 校验）。
     * 功能：手动生成一次燃尽图快照。
     * 关键入参：pid（路径，项目 ID，须归属当前租户）。
     */
    @PostMapping("/burndown/snapshot")
    @Operation(summary = "手动生成燃尽图快照")
    public Result<Void> burndownSnapshot(@PathVariable Long pid) {
        assertOwnsProject(pid);
        burndownService.takeSnapshot(pid);
        return Result.ok();
    }
}
