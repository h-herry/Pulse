package com.pulse.controller;

import com.pulse.common.BusinessException;
import com.pulse.common.Result;
import com.pulse.common.TenantContext;
import com.pulse.entity.TaskAttachment;
import com.pulse.service.AttachmentService;
import com.pulse.service.TaskService;
import com.pulse.entity.Task;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 任务附件接口（R-027）
 *
 * 上传复用 UploadController 的文件类型白名单逻辑（.jpg/.png/.pdf 等），
 * 保存到 uploads/ 目录，返回附件记录（含 filePath）。
 */
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/attachments")
@Tag(name = "附件", description = "任务附件（R-027）")
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final TaskService taskService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".pdf");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf");

    public AttachmentController(AttachmentService attachmentService, TaskService taskService) {
        this.attachmentService = attachmentService;
        this.taskService = taskService;
    }

    @GetMapping
    @Operation(summary = "获取任务附件列表")
    public Result<List<TaskAttachment>> list(@PathVariable Long taskId) {
        return Result.ok(attachmentService.list(taskId, TenantContext.getTenantId()));
    }

    @PostMapping
    @Operation(summary = "上传任务附件（multipart/form-data）")
    public Result<TaskAttachment> upload(@PathVariable Long taskId,
                                         @RequestParam("file") MultipartFile file,
                                         HttpServletRequest request) throws IOException {
        // 写入前校验任务属于当前团队，防止越权上传（水平越权）
        Task task = taskService.getTask(taskId);
        if (!task.getTeamId().equals(TenantContext.getTenantId())) {
            throw new BusinessException("任务不存在");
        }
        if (file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.'));
        }
        String extLower = ext.toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extLower)) {
            throw new BusinessException("不支持的文件类型");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("不支持的文件类型");
        }

        String filename = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8) + extLower;

        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        Path target = dir.resolve(filename);
        file.transferTo(target);

        TaskAttachment a = attachmentService.create(taskId, file.getOriginalFilename(),
                "uploads/" + filename, file.getSize(), contentType,
                TenantContext.getTenantId(), TenantContext.getUserId());
        return Result.ok(a);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除任务附件")
    public Result<String> delete(@PathVariable Long taskId, @PathVariable Long id) {
        attachmentService.delete(id, TenantContext.getTenantId());
        return Result.ok("已删除附件");
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "下载任务附件")
    public void download(@PathVariable Long taskId, @PathVariable Long id, HttpServletResponse response) throws IOException {
        TaskAttachment a = attachmentService.getAttachment(id, taskId, TenantContext.getTenantId());
        String fileName = a.getFilePath() != null ? a.getFilePath().replace("uploads/", "") : "";
        Path file = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(fileName);
        if (!Files.exists(file)) {
            throw new BusinessException("文件不存在");
        }
        response.setContentType(a.getFileType() != null ? a.getFileType() : "application/octet-stream");
        String safeName = a.getFileName() != null ? a.getFileName() : fileName;
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + new String(safeName.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        java.nio.charset.StandardCharsets.ISO_8859_1) + "\"");
        Files.copy(file, response.getOutputStream());
        response.getOutputStream().flush();
    }
}
