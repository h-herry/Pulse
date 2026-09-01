package com.pulse.controller;

import com.pulse.common.BusinessException;
import com.pulse.common.Result;
import com.pulse.common.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 通用文件上传端点（路径 /api/v1/upload）。
 * 职责：接收 multipart 文件并落盘至 uploads/ 目录，返回可访问 URL；仅允许图片与 PDF 类型。
 * 安全约束：SEC-03 双重白名单校验（扩展名 + Content-Type）；落盘前做路径规范化（normalize）防目录遍历；
 * 需登录（经 JwtAuthFilter 装配认证）后方可上传。
 */
@RestController
@RequestMapping("/api/v1")
@io.swagger.v3.oas.annotations.tags.Tag(name = "文件", description = "文件上传")
public class UploadController {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".pdf");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf");

    /**
     * 所需权限：登录（经 JwtAuthFilter 装配认证）。
     * 功能：接收 multipart 文件并落盘至 uploads/ 目录，返回可访问 URL；仅允许图片与 PDF 类型。
     * 关键入参：file（multipart 文件，必填）；HttpServletRequest（用于构造访问 URL）。
     * 安全约束：SEC-03 双重白名单（扩展名 + Content-Type），落盘前路径规范化防目录遍历。
     */
    @PostMapping("/upload")
    @Operation(summary = "上传文件")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file,
                                              HttpServletRequest request) throws IOException {
        // 写入前校验团队上下文（等价校验：本端点无 taskId，团队归属由 X-Team-ID 解析保证；
        // 系统管理员上帝视角放行，普通用户必须处于有效团队上下文）
        if (TenantContext.getTenantId() == null && !TenantContext.isSystemAdmin()) {
            throw new BusinessException("缺少团队上下文");
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.'));
        }

        // SEC-03: 文件类型白名单校验 — 扩展名与 Content-Type 双重验证
        String extLower = ext.toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extLower)) {
            throw new BusinessException("不支持的文件类型");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("不支持的文件类型");
        }

        String filename = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8) + ext;

        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }

        Path target = dir.resolve(filename);
        file.transferTo(target);

        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        String base = scheme + "://" + host + ":" + port;
        String url = base + "/uploads/" + filename;

        return Result.ok(Map.of("url", url, "filename", filename));
    }
}
