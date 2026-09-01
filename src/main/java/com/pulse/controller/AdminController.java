package com.pulse.controller;

import com.pulse.common.Result;
import com.pulse.dto.AdminUpdateProfileRequest;
import com.pulse.dto.AdminUpdateRoleRequest;
import com.pulse.entity.User;
import com.pulse.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统管理员全局管理端点（路径 /api/v1/admin）。
 * 职责：以「上帝视角」跨团队查询团队/项目/用户/成员/审计日志/任务，并提供用户启停、密码重置、成员转组、角色设置等全局治理操作。
 * 安全约束：类级 @PreAuthorize("hasRole('ADMIN')") 要求系统管理员（users.is_system_admin=true）权限，并非团队队长；
 * 所有接口与具体团队无关，属全局管理视图，操作时无需 X-Team-ID。
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@io.swagger.v3.oas.annotations.tags.Tag(name = "管理员", description = "全局管理视图")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * 所需权限：系统管理员（hasRole('ADMIN')）。
     * 功能：获取所有团队列表（含成员数、项目数统计）。
     * 关键入参：无。
     */
    @GetMapping("/teams")
    @Operation(summary = "获取所有团队列表（含成员数、项目数统计）")
    public Result<List<Map<String, Object>>> getAllTeams() {
        return Result.ok(adminService.getAllTeams());
    }

    /**
     * 所需权限：系统管理员（hasRole('ADMIN')）。
     * 功能：获取所有未删除项目列表（含团队名称）。
     * 关键入参：无。
     */
    @GetMapping("/projects")
    @Operation(summary = "获取所有非已删除项目列表（含团队名称）")
    public Result<List<Map<String, Object>>> getAllProjects() {
        return Result.ok(adminService.getAllProjects());
    }

    /**
     * 所需权限：系统管理员（hasRole('ADMIN')）。
     * 功能：获取指定团队下的项目列表。
     * 关键入参：teamId（路径变量，目标团队 ID）。
     */
    @GetMapping("/teams/{teamId}/projects")
    @Operation(summary = "获取指定团队的项目列表")
    public Result<List<Map<String, Object>>> getTeamProjects(@PathVariable Long teamId) {
        return Result.ok(adminService.getTeamProjects(teamId));
    }

    /**
     * 所需权限：系统管理员（hasRole('ADMIN')）。
     * 功能：获取所有已归档项目（需求 R-029，供管理后台使用）。
     * 关键入参：无。
     */
    @GetMapping("/projects/archived")
    @Operation(summary = "获取所有已归档项目（R-029，供管理后台）")
    public Result<List<Map<String, Object>>> getArchivedProjects() {
        return Result.ok(adminService.listArchivedProjects());
    }

    /**
     * 所需权限：系统管理员（hasRole('ADMIN')）。
     * 功能：获取所有用户列表（含启用状态）。
     * 关键入参：无。
     */
    @GetMapping("/users")
    @Operation(summary = "获取所有用户列表（含启用状态）")
    public Result<List<User>> listUsers() {
        return Result.ok(adminService.listUsers());
    }

    /**
     * 所需权限：系统管理员（hasRole('ADMIN')）。
     * 功能：启用/禁用指定用户。
     * 关键入参：id（路径，用户 ID）；body.enabled（请求体，布尔，必填，缺失返回失败）。
     */
    @PutMapping("/users/{id}/enabled")
    @Operation(summary = "启用/禁用用户")
    public Result<Void> toggleUserEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return Result.fail("enabled 不能为空");
        }
        adminService.toggleUserEnabled(id, enabled);
        return Result.ok();
    }

    /**
     * 所需权限：系统管理员（hasRole('ADMIN')）。
     * 功能：重置用户密码，返回一次性重置令牌与重置链接（不再返回明文临时密码，避免凭据经响应体泄露）。
     * 关键入参：id（路径，用户 ID）。
     */
    @PostMapping("/users/{id}/reset-password")
    @Operation(summary = "重置用户密码（返回一次性重置链接）")
    public Result<Map<String, String>> resetPassword(@PathVariable Long id) {
        return Result.ok(adminService.resetPassword(id));
    }

    /**
     * 所需权限：系统管理员（hasRole('ADMIN')）。
     * 功能：获取所有成员（含团队信息，管理员视角）。
     * 关键入参：无。
     */
    @GetMapping("/members")
    @Operation(summary = "获取所有成员（含团队信息，管理员用）")
    public Result<List<Map<String, Object>>> getAllMembers() {
        return Result.ok(adminService.getAllMembers());
    }

    /**
     * 所需权限：系统管理员（hasRole('ADMIN')）。
     * 功能：将成员从一个团队转移到另一个团队。
     * 关键入参：userId（路径）；body.fromTeamId/toTeamId（请求体，必填，缺失返回失败）。
     */
    @PutMapping("/members/{userId}/team")
    @Operation(summary = "修改成员所属团队")
    public Result<String> moveMemberTeam(@PathVariable Long userId, @RequestBody Map<String, Long> body) {
        Long fromTeamId = body.get("fromTeamId");
        Long toTeamId = body.get("teamId");
        if (fromTeamId == null || toTeamId == null) {
            return Result.fail("fromTeamId 和 teamId 不能为空");
        }
        adminService.moveMemberTeam(userId, fromTeamId, toTeamId);
        return Result.ok("团队已更新");
    }

    /**
     * 所需权限：系统管理员（hasRole('ADMIN')）。
     * 功能：更新用户资料（显示名等）。
     * 关键入参：id（路径）；body.displayName（AdminUpdateProfileRequest，必填）。
     */
    @PutMapping("/users/{id}/profile")
    @Operation(summary = "更新用户资料（管理员）")
    public Result<User> updateUserProfile(@PathVariable Long id, @Valid @RequestBody AdminUpdateProfileRequest body) {
        return Result.ok(adminService.updateUserProfile(id, body.getDisplayName()));
    }

    /**
     * 所需权限：系统管理员（hasRole('ADMIN')）。
     * 功能：设置用户全局角色（admin=系统管理员，user=普通用户）。
     * 关键入参：id（路径）；body.role（AdminUpdateRoleRequest，必填）。
     */
    @PutMapping("/users/{id}/role")
    @Operation(summary = "设置用户全局角色（user=普通用户，admin=系统管理员）")
    public Result<Void> updateUserRole(@PathVariable Long id, @Valid @RequestBody AdminUpdateRoleRequest body) {
        adminService.setUserAdmin(id, "admin".equalsIgnoreCase(body.getRole()));
        return Result.ok();
    }

    /**
     * 所需权限：系统管理员（hasRole('ADMIN')）。
     * 功能：查询操作审计日志，支持按团队/动作过滤与分页。
     * 关键入参：teamId（可选，团队过滤）；action（可选，动作过滤）；limit/offset（分页）。
     */
    @GetMapping("/audit-logs")
    @Operation(summary = "查询操作审计日志（v7），支持按团队/动作过滤与分页")
    public Result<List<Map<String, Object>>> getAuditLogs(
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return Result.ok(adminService.listAuditLogs(teamId, action, limit, offset));
    }

    /**
     * 所需权限：系统管理员（hasRole('ADMIN')）。
     * 功能：返回去重的审计动作类型列表（供前端筛选下拉使用）。
     * 关键入参：无。
     */
    @GetMapping("/audit-logs/actions")
    @Operation(summary = "返回去重的审计动作类型列表（供筛选下拉）")
    public Result<List<String>> getAuditActions() {
        return Result.ok(adminService.listAuditActions());
    }

    /**
     * 所需权限：系统管理员（hasRole('ADMIN')，上帝视角）。
     * 功能：跨团队查看全部任务（分页 + 关键字搜索 + 状态筛选）。
     * 关键入参：page/size（分页）；keyword（可选，搜索关键字）；status（可选，状态过滤）。
     */
    @GetMapping("/tasks")
    @Operation(summary = "上帝视角：跨团队查看全部任务（分页+搜索+状态筛选）")
    public Result<Map<String, Object>> getAllTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.ok(adminService.listAllTasks(page, size, keyword, status));
    }
}
