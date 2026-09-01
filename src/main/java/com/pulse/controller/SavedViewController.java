package com.pulse.controller;

import com.pulse.common.Result;
import com.pulse.common.TenantContext;
import com.pulse.dto.SavedViewRequest;
import com.pulse.entity.SavedView;
import com.pulse.service.SavedViewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 保存视图端点（路径 /api/v1/projects/{pid}/saved-views，需求 R-020）。
 * 职责：按用户保存与管理筛选视图（列表、创建、更新、删除）。
 * 安全约束：视图归属「当前用户 + 当前团队」（TenantContext 的 userId/tenantId 双维度隔离），不跨用户、不跨团队共享。
 */
@RestController
@RequestMapping("/api/v1/projects/{pid}/saved-views")
@Tag(name = "保存视图", description = "保存的筛选视图（R-020）")
public class SavedViewController {

    private final SavedViewService savedViewService;

    public SavedViewController(SavedViewService savedViewService) {
        this.savedViewService = savedViewService;
    }

    /**
     * 所需权限：登录且须为视图所有者（userId + 当前团队 tenantId，双维度隔离）。
     * 功能：获取当前用户在当前项目下的保存视图列表。
     * 关键入参：pid（路径，项目 ID）。
     */
    @GetMapping
    @Operation(summary = "获取当前用户的保存视图列表")
    public Result<List<SavedView>> list(@PathVariable Long pid) {
        return Result.ok(savedViewService.list(pid, TenantContext.getTenantId(), TenantContext.getUserId()));
    }

    /**
     * 所需权限：登录且须为视图所有者（userId + 当前团队 tenantId）。
     * 功能：在当前项目下创建保存视图。
     * 关键入参：pid（路径，项目 ID）；SavedViewRequest（筛选条件）。
     */
    @PostMapping
    @Operation(summary = "创建保存视图")
    public Result<SavedView> create(@PathVariable Long pid, @Valid @RequestBody SavedViewRequest req) {
        return Result.ok(savedViewService.create(pid, req, TenantContext.getTenantId(), TenantContext.getUserId()));
    }

    /**
     * 所需权限：登录且须为视图所有者（tenantId 隔离）。
     * 功能：更新指定保存视图。
     * 关键入参：pid（路径，项目 ID）；id（路径，视图 ID）；SavedViewRequest（更新内容）。
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新保存视图")
    public Result<SavedView> update(@PathVariable Long pid, @PathVariable Long id, @RequestBody SavedViewRequest req) {
        return Result.ok(savedViewService.update(id, req, TenantContext.getTenantId()));
    }

    /**
     * 所需权限：登录且须为视图所有者（tenantId 隔离）。
     * 功能：删除指定保存视图。
     * 关键入参：pid（路径，项目 ID）；id（路径，视图 ID）。
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除保存视图")
    public Result<String> delete(@PathVariable Long pid, @PathVariable Long id) {
        savedViewService.delete(id, TenantContext.getTenantId());
        return Result.ok("已删除");
    }
}
