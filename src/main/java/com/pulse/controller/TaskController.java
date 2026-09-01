package com.pulse.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pulse.common.Result;
import com.pulse.dto.TaskMoveRequest;
import com.pulse.dto.TaskPatchRequest;
import com.pulse.dto.TaskRequest;
import com.pulse.entity.Tag;
import com.pulse.entity.Task;
import com.pulse.entity.TaskActivityLog;
import com.pulse.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import com.pulse.dto.BatchTaskRequest;

/**
 * 任务管理端点（路径 /api/v1，含项目内与跨团队任务操作）。
 * 职责：任务增删改查、看板分组、移动、活动日志、子任务、标签关联、批量更新、全局搜索与「我的任务」聚合。
 * 安全约束：任务归属由服务层基于 X-Team-ID 校验；/tasks/search 与 /tasks/my 支持跨团队聚合，但按当前用户权限限定可见范围。
 */
@RestController
@RequestMapping("/api/v1")
@io.swagger.v3.oas.annotations.tags.Tag(name = "任务", description = "任务管理")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID，任务归属由服务层校验）。
     * 功能：在指定项目下创建任务。
     * 关键入参：projectId（路径，项目 ID）；TaskRequest（任务定义）。
     */
    @PostMapping("/projects/{projectId}/tasks")
    @Operation(summary = "创建任务")
    public Result<Task> createTask(@PathVariable Long projectId, @Valid @RequestBody TaskRequest req) {
        return Result.ok(taskService.createTask(projectId, req));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID，项目归属由服务层校验）。
     * 功能：分页获取项目任务列表，支持按列/负责人/迭代过滤。
     * 关键入参：projectId（路径）；page/size（分页）；columnId/assigneeId/iterationId（可选过滤）。
     */
    @GetMapping("/projects/{projectId}/tasks")
    @Operation(summary = "任务列表")
    public Result<IPage<Map<String, Object>>> listTasks(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long columnId,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Long iterationId) {
        return Result.ok(taskService.listTasks(projectId, page, size, columnId, assigneeId, iterationId));
    }

    /**
     * 所需权限：登录且任务须归属当前团队（X-Team-ID，由服务层校验）。
     * 功能：获取任务详情。
     * 关键入参：id（路径，任务 ID）。
     */
    @GetMapping("/tasks/{id}")
    @Operation(summary = "获取任务详情")
    public Result<Map<String, Object>> getTask(@PathVariable Long id) {
        return Result.ok(taskService.getTaskDetail(id));
    }

    /**
     * 所需权限：登录且任务须归属当前团队（X-Team-ID，由服务层校验）。
     * 功能：局部更新任务（支持仅传单个字段）。
     * 关键入参：id（路径，任务 ID）；TaskPatchRequest（待更新字段）。
     */
    @PatchMapping("/tasks/{id}")
    @Operation(summary = "更新任务（局部更新，支持仅传单个字段）")
    public Result<Task> updateTask(@PathVariable Long id, @Valid @RequestBody TaskPatchRequest req) {
        return Result.ok(taskService.updateTask(id, req));
    }

    /**
     * 所需权限：登录且任务须归属当前团队（X-Team-ID，由服务层校验）。
     * 功能：PUT 兼容更新任务（语义同 PATCH，局部更新）。
     * 关键入参：id（路径，任务 ID）；TaskPatchRequest（待更新字段）。
     */
    @PutMapping("/tasks/{id}")
    @Operation(summary = "更新任务（PUT 兼容，局部更新）")
    public Result<Task> updateTaskPut(@PathVariable Long id, @Valid @RequestBody TaskPatchRequest req) {
        return Result.ok(taskService.updateTask(id, req));
    }

    /**
     * 所需权限：登录且任务须归属当前团队（X-Team-ID，由服务层校验）。
     * 功能：移动任务（如看板列间拖动）。
     * 关键入参：id（路径，任务 ID）；TaskMoveRequest（目标位置/列）。
     */
    @PutMapping("/tasks/{id}/move")
    @Operation(summary = "移动任务")
    public Result<String> moveTask(@PathVariable Long id, @Valid @RequestBody TaskMoveRequest req) {
        taskService.moveTask(id, req);
        return Result.ok("已移动");
    }

    /**
     * 所需权限：登录且任务须归属当前团队（X-Team-ID，由服务层校验）。
     * 功能：删除任务。
     * 关键入参：id（路径，任务 ID）。
     */
    @DeleteMapping("/tasks/{id}")
    @Operation(summary = "删除任务")
    public Result<String> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return Result.ok("已删除");
    }

    /**
     * 所需权限：登录且任务须归属当前团队（X-Team-ID，由服务层校验）。
     * 功能：获取任务活动日志。
     * 关键入参：id（路径，任务 ID）。
     */
    @GetMapping("/tasks/{id}/activity")
    @Operation(summary = "活动日志")
    public Result<List<TaskActivityLog>> getTaskLogs(@PathVariable Long id) {
        return Result.ok(taskService.getTaskLogs(id));
    }

    /**
     * 所需权限：登录且任务须归属当前团队（X-Team-ID，由服务层校验）。
     * 功能：获取任务活动日志（前端别名，等价 /activity）。
     * 关键入参：id（路径，任务 ID）。
     */
    @GetMapping("/tasks/{id}/logs")
    @Operation(summary = "活动日志（前端别名）")
    public Result<List<TaskActivityLog>> getTaskLogsAlias(@PathVariable Long id) {
        return Result.ok(taskService.getTaskLogs(id));
    }

    /**
     * 所需权限：登录且任务须归属当前团队（X-Team-ID，由服务层校验）。
     * 功能：获取任务子任务列表。
     * 关键入参：id（路径，任务 ID）。
     */
    @GetMapping("/tasks/{id}/subtasks")
    @Operation(summary = "子任务列表")
    public Result<List<Map<String, Object>>> getSubtasks(@PathVariable Long id) {
        return Result.ok(taskService.getSubtasks(id));
    }

    /**
     * 所需权限：登录且属于当前团队（X-Team-ID，项目归属由服务层校验）。
     * 功能：获取看板任务（按列分组的扁平列表），可按迭代过滤。
     * 关键入参：pid（路径，项目 ID）；iterationId（可选，迭代过滤）。
     */
    @GetMapping("/projects/{pid}/board")
    @Operation(summary = "看板任务（按列分组所需扁平列表），可按迭代过滤")
    public Result<List<Map<String, Object>>> getBoard(@PathVariable Long pid,
                                                      @RequestParam(required = false) Long iterationId) {
        return Result.ok(taskService.getBoardTasks(pid, iterationId));
    }

    /**
     * 所需权限：登录；支持跨团队搜索，但按当前用户可见范围限定。
     * 功能：全局搜索任务（按关键字）。
     * 关键入参：q（搜索关键字）；teamId（团队范围）；limit（返回上限）。
     */
    @GetMapping("/tasks/search")
    @Operation(summary = "全局搜索任务")
    public Result<List<Map<String, Object>>> searchTasks(
            @RequestParam String q,
            @RequestParam Long teamId,
            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(taskService.searchTasks(q, teamId, limit));
    }

    /**
     * 所需权限：需登录（跨团队按当前用户聚合）。
     * 功能：获取「我的任务」（当前登录用户在所有团队中的任务）。
     * 关键入参：无。
     */
    @GetMapping("/tasks/my")
    @Operation(summary = "我的任务（跨团队，按当前登录用户聚合）")
    public Result<List<Map<String, Object>>> myTasks() {
        return Result.ok(taskService.getMyTasks());
    }

    /**
     * 所需权限：登录且任务须归属当前团队（X-Team-ID，由服务层校验）。
     * 功能：获取任务的关联标签列表。
     * 关键入参：id（路径，任务 ID）。
     */
    @GetMapping("/tasks/{id}/tags")
    @Operation(summary = "获取任务标签")
    public Result<List<Tag>> getTaskTags(@PathVariable Long id) {
        return Result.ok(taskService.getTaskTags(id));
    }

    /**
     * 所需权限：登录；批内任务须归属当前团队（X-Team-ID，由服务层校验）。
     * 功能：批量更新任务（需求 R-019），对一批任务应用相同字段变更。
     * 关键入参：BatchTaskRequest.taskIds（任务 ID 列表）；BatchTaskRequest.updates（更新字段）。
     */
    @PatchMapping("/tasks/batch")
    @Operation(summary = "批量更新任务（R-019）")
    public Result<Map<String, Object>> batchUpdate(@RequestBody BatchTaskRequest req) {
        return Result.ok(Map.of("updated", taskService.batchUpdate(req.getTaskIds(), req.getUpdates())));
    }

    /**
     * 所需权限：登录且任务须归属当前团队（X-Team-ID，由服务层校验）。
     * 功能：为任务关联（添加）一个标签。
     * 关键入参：id（路径，任务 ID）；tagId（路径，标签 ID）。
     */
    @PostMapping("/tasks/{id}/tags/{tagId}")
    @Operation(summary = "添加标签")
    public Result<String> addTaskTag(@PathVariable Long id, @PathVariable Long tagId) {
        taskService.addTaskTag(id, tagId);
        return Result.ok("已关联");
    }

    /**
     * 所需权限：登录且任务须归属当前团队（X-Team-ID，由服务层校验）。
     * 功能：移除任务与标签的关联。
     * 关键入参：id（路径，任务 ID）；tagId（路径，标签 ID）。
     */
    @DeleteMapping("/tasks/{id}/tags/{tagId}")
    @Operation(summary = "移除标签")
    public Result<String> removeTaskTag(@PathVariable Long id, @PathVariable Long tagId) {
        taskService.removeTaskTag(id, tagId);
        return Result.ok("已移除标签关联");
    }
}
