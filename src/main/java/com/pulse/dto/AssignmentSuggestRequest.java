package com.pulse.dto;

import lombok.Data;

/**
 * 智能分派建议请求：可传 taskId（复用其信息并校验归属），
 * 或传 title/priority/storyPoints/description 直接描述待分配任务。
 */
@Data
public class AssignmentSuggestRequest {
    /** 可选：待分派任务 ID（提供时后端校验其团队归属并补全 brief） */
    private Long taskId;
    /** 可选：约束分派范围到该项目（负载计算将按项目过滤） */
    private Long projectId;
    private String title;
    private String description;
    private String priority;
    private Integer storyPoints;
}
