package com.pulse.controller;

import com.pulse.common.Result;
import com.pulse.entity.Notification;
import com.pulse.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户通知端点（路径 /api/v1/notifications）。
 * 职责：查询当前用户通知列表、未读计数，以及标记单条已读/全部已读。
 * 安全约束：数据按当前登录用户隔离（TenantContext 用户维度），无需 X-Team-ID 团队维度；仅返回本人通知。
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "通知", description = "用户通知与活动提醒")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 所需权限：需登录（数据按当前用户隔离）。
     * 功能：获取当前用户的通知列表（限制返回条数 1~100）。
     * 关键入参：limit（可选，默认 30，超出范围自动裁剪）。
     */
    @GetMapping
    @Operation(summary = "当前用户通知列表")
    public Result<List<Notification>> list(@RequestParam(defaultValue = "30") int limit) {
        limit = Math.min(Math.max(limit, 1), 100);
        return Result.ok(notificationService.listForUser(limit));
    }

    /**
     * 所需权限：需登录（数据按当前用户隔离）。
     * 功能：获取当前用户的未读通知数量。
     * 关键入参：无。
     */
    @GetMapping("/unread-count")
    @Operation(summary = "未读数量")
    public Result<Long> unreadCount() {
        return Result.ok(notificationService.unreadCount());
    }

    /**
     * 所需权限：需登录（仅可标记本人通知）。
     * 功能：标记单条通知为已读。
     * 关键入参：id（路径，通知 ID）。
     */
    @PostMapping("/{id}/read")
    @Operation(summary = "标记单条已读")
    public Result<String> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return Result.ok("ok");
    }

    /**
     * 所需权限：需登录（仅影响本人通知）。
     * 功能：将当前用户全部通知标记为已读。
     * 关键入参：无。
     */
    @PostMapping("/read-all")
    @Operation(summary = "全部已读")
    public Result<String> markAllRead() {
        notificationService.markAllRead();
        return Result.ok("ok");
    }
}
