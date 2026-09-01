package com.pulse.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 自定义字段定义（R-028）
 *
 * 字段类型：text / number / date / select / multiselect。
 * options 以 JSON 数组存储（select / multiselect 时使用）。
 */
@Data
@TableName("custom_fields")
public class CustomField {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long teamId;
    private String name;
    /** 类型：text/number/date/select/multiselect */
    private String fieldType;
    /** 选项 JSON 数组（select/multiselect 使用） */
    private String options;
    private Integer sortOrder;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
