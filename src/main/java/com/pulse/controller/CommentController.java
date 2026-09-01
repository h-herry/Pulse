package com.pulse.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pulse.common.Result;
import com.pulse.dto.CommentRequest;
import com.pulse.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 任务评论端点（路径 /api/v1/tasks/{taskId}/comments）。
 * 职责：管理任务下的评论，包括分页列表、创建与删除。
 * 安全约束：删除评论仅限作者本人；任务与团队归属由服务层基于 X-Team-ID 校验，越权将拒绝。
 */
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/comments")
@Tag(name = "评论", description = "任务评论管理")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /**
     * 所需权限：登录且属于任务所在团队（X-Team-ID）。
     * 功能：分页获取指定任务的评论列表。
     * 关键入参：taskId（路径）；page/size（分页）。
     */
    @GetMapping
    @Operation(summary = "评论列表（分页）")
    public Result<IPage<Map<String, Object>>> listComments(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(commentService.listComments(taskId, page, size));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID）。
     * 功能：在指定任务下创建评论。
     * 关键入参：taskId（路径）；CommentRequest（评论内容）。
     */
    @PostMapping
    @Operation(summary = "创建评论")
    public Result<Map<String, Object>> createComment(
            @PathVariable Long taskId,
            @Valid @RequestBody CommentRequest req) {
        return Result.ok(commentService.createComment(taskId, req));
    }

    /**
     * 所需权限：登录且须为评论作者本人。
     * 功能：删除指定评论。
     * 关键入参：taskId（路径）；id（路径，评论 ID）。
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除评论（仅作者）")
    public Result<String> deleteComment(
            @PathVariable Long taskId,
            @PathVariable Long id) {
        commentService.deleteComment(taskId, id);
        return Result.ok("已删除");
    }
}
