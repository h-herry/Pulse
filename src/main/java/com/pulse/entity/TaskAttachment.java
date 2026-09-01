package com.pulse.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 任务附件（R-027）
 *
 * 文件保存在 uploads/ 目录下，filePath 存储相对路径（如 uploads/xxx.png），
 * 可通过 /uploads/{filename} 静态资源或下载接口访问。
 */
@Data
@TableName("task_attachments")
public class TaskAttachment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    /** 上传者 */
    private Long userId;
    private Long teamId;
    private String fileName;
    /** 存储相对路径（uploads/ 下） */
    private String filePath;
    /** 文件大小（字节） */
    private Long fileSize;
    /** MIME 类型 */
    private String fileType;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
