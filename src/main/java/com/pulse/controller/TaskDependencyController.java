package com.pulse.controller;

import com.pulse.common.BusinessException;
import com.pulse.common.Result;
import com.pulse.common.TenantContext;
import com.pulse.entity.TaskDependency;
import com.pulse.service.TaskDependencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 任务依赖端点（路径 /api/v1/tasks/{taskId}/dependencies，需求 R-021）。
 * 职责：管理任务的前置依赖关系（列表、新增、移除），新增时执行 DAG 循环校验防止依赖成环。
 * 安全约束：依赖任务须归属当前团队（TenantContext.tenantId 校验），防止越权操作跨团队依赖。
 */
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/dependencies")
@Tag(name = "任务依赖", description = "任务依赖关系（R-021）")
public class TaskDependencyController {

    private final TaskDependencyService taskDependencyService;

    public TaskDependencyController(TaskDependencyService taskDependencyService) {
        this.taskDependencyService = taskDependencyService;
    }

    /**
     * 所需权限：登录且任务须归属当前团队（TenantContext.tenantId，由服务层校验）。
     * 功能：获取指定任务的依赖列表。
     * 关键入参：taskId（路径，任务 ID）。
     */
    @GetMapping
    @Operation(summary = "获取任务的依赖列表")
    public Result<List<Map<String, Object>>> list(@PathVariable Long taskId) {
        return Result.ok(taskDependencyService.list(taskId, TenantContext.getTenantId()));
    }

    /**
     * 所需权限：登录且任务须归属当前团队（TenantContext.tenantId，由服务层校验）。
     * 功能：新增任务依赖，含 DAG 循环校验防止依赖成环（dependsOnId 缺失抛 BusinessException）。
     * 关键入参：taskId（路径，任务 ID）；body.dependsOnId（前置任务 ID，必填）。
     */
    @PostMapping
    @Operation(summary = "添加任务依赖（含 DAG 循环校验）")
    public Result<TaskDependency> create(@PathVariable Long taskId, @RequestBody Map<String, Long> body) {
        Long dependsOnId = body.get("dependsOnId");
        if (dependsOnId == null) {
            throw new BusinessException("dependsOnId 不能为空");
        }
        return Result.ok(taskDependencyService.create(taskId, dependsOnId, TenantContext.getTenantId()));
    }

    /**
     * 所需权限：登录且任务须归属当前团队（TenantContext.tenantId，由服务层校验）。
     * 功能：移除指定任务依赖。
     * 关键入参：taskId（路径，任务 ID）；depId（路径，依赖记录 ID）。
     */
    @DeleteMapping("/{depId}")
    @Operation(summary = "移除任务依赖")
    public Result<String> delete(@PathVariable Long taskId, @PathVariable Long depId) {
        taskDependencyService.delete(depId, TenantContext.getTenantId());
        return Result.ok("已移除依赖");
    }
}
