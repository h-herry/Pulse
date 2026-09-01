package com.pulse.controller;

import com.pulse.common.Result;
import com.pulse.dto.IterationRequest;
import com.pulse.service.IterationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 顶层迭代路由（与前端契约一致：/api/v1/iterations/{id}）。
 * 团队上下文由 X-Team-ID 头解析。
 */
@RestController
@RequestMapping("/api/v1/iterations")
@Tag(name = "迭代（顶层）", description = "前端使用的顶层迭代路由")
public class AppIterationController {

    private final IterationService iterationService;

    public AppIterationController(IterationService iterationService) {
        this.iterationService = iterationService;
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新迭代")
    public Result<?> updateIteration(@PathVariable Long id, @Valid @RequestBody IterationRequest req) {
        iterationService.getById(id); // 校验迭代归属当前团队
        return Result.ok(iterationService.updateIteration(id, req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除迭代")
    public Result<String> deleteIteration(@PathVariable Long id) {
        iterationService.getById(id); // 校验迭代归属当前团队
        iterationService.deleteIteration(id); // 内部已校验团队并 requireTeamLead
        return Result.ok("已删除");
    }
}
