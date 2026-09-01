package com.pulse.dto;

import lombok.Data;
import java.util.List;

@Data
/**
 * AI 生成项目/迭代进展摘要的请求。
 *
 * <p>{@code tasks} 为待摘要的任务列表（内嵌 {@code TaskBrief} 精简结构），
 * 可选 {@code context} 补充迭代名/项目名等背景信息，提升摘要相关性。
 */
public class AiSummaryRequest {
    private List<TaskBrief> tasks;
    /** 可选补充上下文，如迭代名、项目名等 */
    private String context;

    @Data
    public static class TaskBrief {
        private String title;
        private String status;
        private String priority;
        private String dueDate;
        private String assigneeName;
    }
}
