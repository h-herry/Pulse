package com.pulse.controller;

import com.pulse.common.Result;
import com.pulse.dto.TagRequest;
import com.pulse.entity.Tag;
import com.pulse.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 团队标签端点（路径 /api/v1/teams/{teamId}/tags）。
 * 职责：团队级标签的列表、创建、更新、删除。
 * 租户约束：teamId 由 X-Team-ID 指定，标签在团队内共享且受多租户隔离，不可跨团队操作。
 */
@RestController
@RequestMapping("/api/v1/teams/{teamId}/tags")
@io.swagger.v3.oas.annotations.tags.Tag(name = "标签", description = "团队标签管理")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID = teamId）。
     * 功能：获取团队标签列表（团队内共享）。
     * 关键入参：teamId（路径，团队 ID）。
     */
    @GetMapping
    @Operation(summary = "获取标签列表")
    public Result<List<Tag>> listTags(@PathVariable Long teamId) {
        return Result.ok(tagService.listTags());
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID = teamId）。
     * 功能：在当前团队下创建标签。
     * 关键入参：teamId（路径，团队 ID）；TagRequest（标签定义）。
     */
    @PostMapping
    @Operation(summary = "创建标签")
    public Result<Tag> createTag(@PathVariable Long teamId, @Valid @RequestBody TagRequest req) {
        return Result.ok(tagService.createTag(req));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID = teamId）。
     * 功能：更新指定标签。
     * 关键入参：teamId（路径，团队 ID）；id（路径，标签 ID）；TagRequest（更新内容）。
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新标签")
    public Result<Tag> updateTag(@PathVariable Long teamId, @PathVariable Long id, @Valid @RequestBody TagRequest req) {
        return Result.ok(tagService.updateTag(id, req));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID = teamId）。
     * 功能：删除指定标签。
     * 关键入参：teamId（路径，团队 ID）；id（路径，标签 ID）。
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除标签")
    public Result<String> deleteTag(@PathVariable Long teamId, @PathVariable Long id) {
        tagService.deleteTag(id);
        return Result.ok("已删除");
    }
}
