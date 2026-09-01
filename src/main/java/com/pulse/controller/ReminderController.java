package com.pulse.controller;

import com.pulse.common.Result;
import com.pulse.scheduler.ReminderScheduler;

import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 智能提醒手动触发端点（日常由调度器定时执行，此处用于即时触发 / 调试）。
 */
@RestController
@RequestMapping("/api/v1/reminders")
@Tag(name = "智能提醒", description = "逾期 / 临期任务提醒")
public class ReminderController {

    private final ReminderScheduler reminderScheduler;

    public ReminderController(ReminderScheduler reminderScheduler) {
        this.reminderScheduler = reminderScheduler;
    }

    @PostMapping("/run")
    @Operation(summary = "立即执行一次智能提醒扫描")
    public Result<Map<String, Object>> run() {
        int pushed = reminderScheduler.runNow();
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("pushed", pushed);
        return Result.ok(data);
    }
}
