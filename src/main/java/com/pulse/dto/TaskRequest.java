package com.pulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class TaskRequest {
    @NotBlank @Size(max = 200)
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
