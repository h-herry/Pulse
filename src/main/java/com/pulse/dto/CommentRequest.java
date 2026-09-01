package com.pulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 任务评论创建请求。
 *
 * <p>{@code content} 评论正文，{@code mentions} 被 @ 提及的用户 ID 列表（前端选择后提交）。
 */
public class CommentRequest {
    @NotBlank
    @Size(max = 20000)
    private String content;
    private List<Long> mentions;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<Long> getMentions() { return mentions; }
    public void setMentions(List<Long> mentions) { this.mentions = mentions; }
}
