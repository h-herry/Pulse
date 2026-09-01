package com.pulse.controller;

import com.pulse.common.Result;
import com.pulse.dto.ProjectRequest;
import com.pulse.entity.Project;
import com.pulse.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 顶层项目路由（与前端契约一致：/api/v1/projects）。
 * 团队上下文由 X-Team-ID 头解析，无需在 URL 中携带 teamId。
 */
@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "项目（顶层）", description = "前端使用的顶层项目路由")
public class AppProjectController {

    private final ProjectService projectService;

    public AppProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    @Operation(summary = "当前团队成员项目列表")
    public Result<List<Project>> listProjects() {
        return Result.ok(projectService.listProjects());
    }

    @PostMapping
    @Operation(summary = "在当前团队下创建项目")
    public Result<Project> createProject(@Valid @RequestBody ProjectRequest req) {
        return Result.ok(projectService.createProject(req));
    }
}
