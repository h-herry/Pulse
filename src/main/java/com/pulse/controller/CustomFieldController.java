package com.pulse.controller;

import com.pulse.common.Result;
import com.pulse.common.TenantContext;
import com.pulse.dto.CustomFieldRequest;
import com.pulse.dto.CustomFieldValueRequest;
import com.pulse.entity.CustomField;
import com.pulse.service.CustomFieldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 自定义字段接口（R-028）
 *
 * 字段定义按项目维护；任务取值接口挂在 /tasks/{taskId} 下。
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "自定义字段", description = "任务自定义字段（R-028）")
public class CustomFieldController {

    private final CustomFieldService customFieldService;

    public CustomFieldController(CustomFieldService customFieldService) {
        this.customFieldService = customFieldService;
    }

    @GetMapping("/projects/{pid}/custom-fields")
    @Operation(summary = "获取项目的自定义字段定义")
    public Result<List<CustomField>> listFields(@PathVariable Long pid) {
        return Result.ok(customFieldService.listFields(pid, TenantContext.getTenantId()));
    }

    @PostMapping("/projects/{pid}/custom-fields")
    @Operation(summary = "创建自定义字段定义")
    public Result<CustomField> createField(@PathVariable Long pid, @Valid @RequestBody CustomFieldRequest req) {
        return Result.ok(customFieldService.createField(pid, req, TenantContext.getTenantId()));
    }

    @PutMapping("/projects/{pid}/custom-fields/{id}")
    @Operation(summary = "更新自定义字段定义")
    public Result<CustomField> updateField(@PathVariable Long pid, @PathVariable Long id,
                                           @RequestBody CustomFieldRequest req) {
        return Result.ok(customFieldService.updateField(id, req, TenantContext.getTenantId()));
    }

    @DeleteMapping("/projects/{pid}/custom-fields/{id}")
    @Operation(summary = "删除自定义字段定义（级联删除取值）")
    public Result<String> deleteField(@PathVariable Long pid, @PathVariable Long id) {
        customFieldService.deleteField(id, TenantContext.getTenantId());
        return Result.ok("已删除");
    }

    @GetMapping("/tasks/{taskId}/custom-fields")
    @Operation(summary = "获取任务的自定义字段（含当前取值）")
    public Result<List<Map<String, Object>>> getTaskFields(@PathVariable Long taskId) {
        return Result.ok(customFieldService.getValuesWithFields(taskId, TenantContext.getTenantId()));
    }

    @PutMapping("/tasks/{taskId}/custom-field-values")
    @Operation(summary = "批量更新任务的自定义字段值")
    public Result<Map<String, Object>> updateValues(@PathVariable Long taskId,
                                                     @RequestBody CustomFieldValueRequest req) {
        int updated = customFieldService.updateValues(taskId, req, TenantContext.getTenantId());
        return Result.ok(Map.of("updated", updated));
    }
}
