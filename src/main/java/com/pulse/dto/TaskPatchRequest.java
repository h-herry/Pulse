package com.pulse.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 任务局部更新（PATCH / PUT）请求体。
 *
 * 与 {@link TaskRequest} 的区别：此处所有字段均可为空，
 * 因为前端对单个字段（如仅 status / 仅 priority）做局部更新时，
 * 不会携带 title 等其它字段。若复用 {@link TaskRequest} 的 @NotBlank 校验，
 * 会导致部分字段更新被 400 拒绝、状态变更无法落库（即"报表不同步"的根因）。
 *
 * 业务层 {@code TaskService.updateTask} 已对每个字段做 null 判断，
 * 仅覆盖非空字段，因此无需在此做必填校验。
 */
public class TaskPatchRequest {
    private String title;
    @Size(max = 20000)
    private String description;
    private String status;
    private String priority;
    private Long assigneeId;
    private Long iterationId;
    private Long boardColumnId;
    private Long parentId;
    private Integer storyPoints;
    private LocalDate startDate;
    private LocalDate dueDate;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }
    public Long getIterationId() { return iterationId; }
    public void setIterationId(Long iterationId) { this.iterationId = iterationId; }
    public Long getBoardColumnId() { return boardColumnId; }
    public void setBoardColumnId(Long boardColumnId) { this.boardColumnId = boardColumnId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Integer getStoryPoints() { return storyPoints; }
    public void setStoryPoints(Integer storyPoints) { this.storyPoints = storyPoints; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
}
