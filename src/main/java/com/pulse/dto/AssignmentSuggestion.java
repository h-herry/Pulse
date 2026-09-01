package com.pulse.dto;

import lombok.Data;

/**
 * AI 智能分派建议：一条针对待分配任务的人选推荐。
 * memberUserId 由后端基于成员名单解析回填，前端可直接据此写库（assigneeId），
 * 避免依赖姓名匹配，也更安全（仅采纳与真实成员精确匹配的建议）。
 */
@Data
public class AssignmentSuggestion {
    private String memberName;
    private Long memberUserId;
    private String reason;
    /** high / medium / low */
    private String confidence;
}
