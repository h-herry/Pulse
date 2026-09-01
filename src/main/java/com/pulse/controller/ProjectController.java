package com.pulse.controller;

import com.pulse.common.Result;
import com.pulse.dto.ProjectRequest;
import com.pulse.entity.Project;
import com.pulse.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 团队项目端点（路径 /api/v1/teams/{teamId}/projects）。
 * 职责：团队内项目的创建、列表、详情、更新、状态流转（active/paused/deleted）与归档/取消归档（需求 R-029）。
 * 租户约束：teamId 由 X-Team-ID 指定，项目数据受多租户隔离；操作经服务层校验团队归属。
 */
@RestController
@RequestMapping("/api/v1/teams/{teamId}/projects")
@io.swagger.v3.oas.annotations.tags.Tag(name = "项目", description = "项目管理")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID）。
     * 功能：在当前团队下创建项目。
     * 关键入参：ProjectRequest（项目定义，含名称等）。
     */
    @PostMapping
    @Operation(summary = "创建项目")
    public Result<Project> createProject(@Valid @RequestBody ProjectRequest req) {
        return Result.ok(projectService.createProject(req));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID）。
     * 功能：获取当前团队的项目列表。
     * 关键入参：teamId（路径，团队 ID，须与当前 X-Team-ID 一致）。
     */
    @GetMapping
    @Operation(summary = "获取项目列表")
    public Result<List<Project>> listProjects() {
        return Result.ok(projectService.listProjects());
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID）。
     * 功能：获取指定项目的详情。
     * 关键入参：teamId（路径，团队 ID）；id（路径，项目 ID）。
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取项目详情")
    public Result<Project> getProject(@PathVariable Long teamId, @PathVariable Long id) {
        return Result.ok(projectService.getProject(id));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID）。
     * 功能：更新指定项目（局部/整体字段）。
     * 关键入参：teamId（路径，团队 ID）；id（路径，项目 ID）；ProjectRequest（更新内容）。
     */
    @PatchMapping("/{id}")
    @Operation(summary = "更新项目")
    public Result<Project> updateProject(@PathVariable Long teamId, @PathVariable Long id,
                                          @Valid @RequestBody ProjectRequest req) {
        return Result.ok(projectService.updateProject(id, req));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID）。
     * 功能：更新项目状态（active/paused/deleted）。
     * 关键入参：teamId（路径，团队 ID）；id（路径，项目 ID）；body.status（目标状态）。
     */
    @PatchMapping("/{id}/status")
    @Operation(summary = "更新项目状态 (active/paused/deleted)")
    public Result<String> updateProjectStatus(@PathVariable Long teamId, @PathVariable Long id,
                                              @RequestBody Map<String, String> body) {
        projectService.updateProjectStatus(id, body.get("status"));
        return Result.ok("项目状态已更新");
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID）。
     * 功能：归档指定项目（需求 R-029）。
     * 关键入参：teamId（路径，团队 ID）；id（路径，项目 ID）。
     */
    @PatchMapping("/{id}/archive")
    @Operation(summary = "归档项目（R-029）")
    public Result<String> archiveProject(@PathVariable Long teamId, @PathVariable Long id) {
        projectService.archiveProject(id);
        return Result.ok("项目已归档");
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID）。
     * 功能：取消归档指定项目（需求 R-029）。
     * 关键入参：teamId（路径，团队 ID）；id（路径，项目 ID）。
     */
    @PatchMapping("/{id}/unarchive")
    @Operation(summary = "取消归档项目（R-029）")
    public Result<String> unarchiveProject(@PathVariable Long teamId, @PathVariable Long id) {
        projectService.unarchiveProject(id);
        return Result.ok("项目已取消归档");
    }
}
