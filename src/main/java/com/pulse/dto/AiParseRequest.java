package com.pulse.dto;

/**
 * AI 解析自然语言为任务结构的请求。
 *
 * <p>{@code text} 为用户输入的自然语言描述（可能含多任务/多人），
 * {@code projectId} 指定解析结果归属的项目，便于回填团队上下文与字段校验。
 */
public class AiParseRequest {
    private String text;
    private Long projectId;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
}
