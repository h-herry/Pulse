package com.pulse.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 保存的筛选视图（R-020）
 *
 * 每个用户可为项目保存一组筛选条件（标签 / 关键词 / 负责人等），
 * filters 与 sortConfig 以 JSON 文本存储。
 */
@Data
@TableName("saved_views")
public class SavedView {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long teamId;
    private Long userId;
    private String name;
    /** 筛选条件 JSON：{ assigneeId, tagIds, keyword, ... } */
    private String filters;
    /** 排序配置 JSON：{ field, order } */
    private String sortConfig;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
