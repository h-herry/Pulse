package com.pulse.controller;

import com.pulse.common.Result;
import com.pulse.dto.ColumnRequest;
import com.pulse.entity.BoardColumn;
import com.pulse.service.BoardColumnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 看板列管理端点（路径 /api/v1/projects/{pid}/columns）。
 * 职责：按项目维护看板列，包括列定义查询、新增、排序、更新与删除。
 * 租户约束：pid 为当前 X-Team-ID 下项目；操作经 TenantInterceptor 隔离，项目/团队归属校验在服务层完成。
 */
@RestController
@RequestMapping("/api/v1/projects/{pid}/columns")
@Tag(name = "看板列", description = "看板列管理")
public class ColumnController {

    private final BoardColumnService columnService;

    public ColumnController(BoardColumnService columnService) {
        this.columnService = columnService;
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID）。
     * 功能：获取指定项目的看板列列表。
     * 关键入参：pid（路径，项目 ID）。
     */
    @GetMapping
    @Operation(summary = "获取看板列")
    public Result<List<BoardColumn>> getColumns(@PathVariable Long pid) {
        return Result.ok(columnService.getColumns(pid));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID）。
     * 功能：在指定项目下新增看板列。
     * 关键入参：pid（路径，项目 ID）；ColumnRequest（列定义，含名称等）。
     */
    @PostMapping
    @Operation(summary = "添加看板列")
    public Result<BoardColumn> createColumn(@PathVariable Long pid, @Valid @RequestBody ColumnRequest req) {
        return Result.ok(columnService.createColumn(pid, req));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID）。
     * 功能：对指定项目的看板列进行重新排序。
     * 关键入参：pid（路径）；body.columnIds（列 ID 的顺序列表）。
     */
    @PutMapping("/reorder")
    @Operation(summary = "列排序")
    public Result<String> reorderColumns(@PathVariable Long pid, @RequestBody java.util.Map<String, java.util.List<Long>> body) {
        columnService.reorderColumns(pid, body.get("columnIds"));
        return Result.ok("已排序");
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID）。
     * 功能：更新指定看板列。
     * 关键入参：pid（路径，项目 ID）；id（路径，列 ID）；ColumnRequest（更新内容）。
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新看板列")
    public Result<BoardColumn> updateColumn(@PathVariable Long pid, @PathVariable Long id,
                                            @Valid @RequestBody ColumnRequest req) {
        return Result.ok(columnService.updateColumn(id, req));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID）。
     * 功能：删除指定看板列。
     * 关键入参：pid（路径，项目 ID）；id（路径，列 ID）。
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除看板列")
    public Result<String> deleteColumn(@PathVariable Long pid, @PathVariable Long id) {
        columnService.deleteColumn(id);
        return Result.ok("已删除");
    }
}
