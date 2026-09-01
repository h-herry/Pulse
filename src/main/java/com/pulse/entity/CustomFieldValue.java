package com.pulse.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 自定义字段值（R-028）
 *
 * 每个任务在每个自定义字段上最多一个取值（唯一约束 task_id + custom_field_id）。
 */
@Data
@TableName("custom_field_values")
public class CustomFieldValue {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long customFieldId;
    private Long teamId;
    /** 字段值（select/multiselect 存 JSON 数组文本） */
    private String value;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
