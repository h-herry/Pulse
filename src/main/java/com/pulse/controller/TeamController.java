package com.pulse.controller;

import com.pulse.common.BusinessException;
import com.pulse.common.Result;
import com.pulse.common.TenantContext;
import com.pulse.dto.AddMemberRequest;
import com.pulse.dto.AddMemberResult;
import com.pulse.dto.MemberVO;
import com.pulse.dto.TeamRequest;
import com.pulse.entity.Team;
import com.pulse.entity.TeamMember;
import com.pulse.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 团队管理端点（路径 /api/v1/teams）。
 * 职责：团队创建、查询、更新、解散，以及成员列表、角色修改、移除与新增。
 * 安全约束：写操作与成员查询要求「当前用户属于该团队（X-Team-ID 匹配）或系统管理员」的双重判定，越权抛 BusinessException；
 * 系统管理员的 isSystemAdmin 可越过团队归属限制。
 */
@RestController
@RequestMapping("/api/v1/teams")
@io.swagger.v3.oas.annotations.tags.Tag(name = "团队", description = "团队管理")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    /**
     * 所需权限：登录（经 JwtAuthFilter 装配认证）。
     * 功能：创建团队；创建者自动成为该团队成员。
     * 关键入参：TeamRequest（团队定义，含名称等）。
     */
    @PostMapping
    @Operation(summary = "创建团队")
    public Result<Team> createTeam(@Valid @RequestBody TeamRequest req) {
        return Result.ok(teamService.createTeam(req));
    }

    /**
     * 所需权限：登录（经 JwtAuthFilter 装配认证）。
     * 功能：获取当前用户已加入的团队列表。
     * 关键入参：无（用户身份取自 TenantContext.getUserId()）。
     */
    @GetMapping
    @Operation(summary = "获取我加入的团队列表")
    public Result<List<Team>> listMyTeams() {
        return Result.ok(teamService.listMyTeams());
    }

    /**
     * 所需权限：登录（经 JwtAuthFilter 装配认证）。
     * 功能：获取指定团队的基本信息。
     * 关键入参：id（路径，团队 ID）。
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取团队信息")
    public Result<Team> getTeam(@PathVariable Long id) {
        return Result.ok(teamService.getTeam(id));
    }

    /**
     * 所需权限：登录（控制器层未做显式团队归属强制，依赖服务层校验）；管理员场景通常由系统管理员或团队内成员操作。
     * 功能：更新团队信息。
     * 关键入参：id（路径，团队 ID）；TeamRequest（更新内容）。
     */
    @PatchMapping("/{id}")
    @Operation(summary = "更新团队信息")
    public Result<Team> updateTeam(@PathVariable Long id, @Valid @RequestBody TeamRequest req) {
        return Result.ok(teamService.updateTeam(id, req));
    }

    /**
     * 所需权限：当前用户属于该团队（X-Team-ID 匹配）或系统管理员；否则抛「无权操作」。
     * 功能：解散指定团队。
     * 关键入参：id（路径，团队 ID）。
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "解散团队")
    public Result<String> deleteTeam(@PathVariable Long id) {
        if (!TenantContext.isSystemAdmin() && !id.equals(TenantContext.getTenantId())) {
            throw new BusinessException("无权操作");
        }
        teamService.deleteTeam(id);
        return Result.ok("团队已解散");
    }

    /**
     * 所需权限：当前用户属于该团队（X-Team-ID 匹配）或系统管理员；否则抛「无权访问」。
     * 功能：批量获取团队下各项目的进度。
     * 关键入参：id（路径，团队 ID）。
     */
    @GetMapping("/{id}/projects/progress")
    @Operation(summary = "团队项目进度（批量）")
    public Result<List<java.util.Map<String, Object>>> getProjectsProgress(@PathVariable Long id) {
        if (!TenantContext.isSystemAdmin() && !id.equals(TenantContext.getTenantId())) {
            throw new BusinessException("无权访问");
        }
        return Result.ok(teamService.getProjectsProgress(id));
    }

    /**
     * 所需权限：当前用户属于该团队（X-Team-ID 匹配）或系统管理员；否则抛「无权访问」。
     * 功能：获取团队成员列表。
     * 关键入参：id（路径，团队 ID）。
     */
    @GetMapping("/{id}/members")
    @Operation(summary = "获取成员列表")
    public Result<List<MemberVO>> listMembers(@PathVariable Long id) {
        if (!TenantContext.isSystemAdmin() && !id.equals(TenantContext.getTenantId())) {
            throw new BusinessException("无权访问");
        }
        return Result.ok(teamService.listMembers(id));
    }

    /**
     * 所需权限：当前用户属于该团队（X-Team-ID 匹配）或系统管理员；否则抛「无权访问」。
     * 功能：修改团队成员角色（如 LEAD/USER）。
     * 关键入参：id（路径，团队 ID）；uid（路径，成员用户 ID）；TeamMember.role（目标角色）。
     */
    @PatchMapping("/{id}/members/{uid}")
    @Operation(summary = "修改成员角色")
    public Result<String> updateMemberRole(@PathVariable Long id, @PathVariable Long uid,
                                           @RequestBody TeamMember req) {
        if (!TenantContext.isSystemAdmin() && !id.equals(TenantContext.getTenantId())) {
            throw new BusinessException("无权访问");
        }
        teamService.updateMemberRole(id, uid, req.getRole());
        return Result.ok("角色已更新");
    }

    /**
     * 所需权限：当前用户属于该团队（X-Team-ID 匹配）或系统管理员；否则抛「无权访问」。
     * 功能：移除团队成员。
     * 关键入参：id（路径，团队 ID）；uid（路径，成员用户 ID）。
     */
    @DeleteMapping("/{id}/members/{uid}")
    @Operation(summary = "移除成员")
    public Result<String> removeMember(@PathVariable Long id, @PathVariable Long uid) {
        if (!TenantContext.isSystemAdmin() && !id.equals(TenantContext.getTenantId())) {
            throw new BusinessException("无权访问");
        }
        teamService.removeMember(id, uid);
        return Result.ok("成员已移除");
    }

    /**
     * 所需权限：当前用户属于该团队（X-Team-ID 匹配）或系统管理员；否则抛「无权访问」。
     * 功能：管理员新增成员到团队。
     * 关键入参：id（路径，团队 ID）；AddMemberRequest（新成员信息）。
     */
    @PostMapping("/{id}/members")
    @Operation(summary = "管理员新增成员")
    public Result<AddMemberResult> addMember(@PathVariable Long id, @RequestBody AddMemberRequest req) {
        if (!TenantContext.isSystemAdmin() && !id.equals(TenantContext.getTenantId())) {
            throw new BusinessException("无权访问");
        }
        return Result.ok(teamService.addMember(id, req));
    }
}
