package com.pulse.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 任务在看板内移动请求。
 *
 * <p>{@code targetColumnId} 目标看板列，{@code afterTaskId} 为移动后置于其后的任务
 * （{@code null} 表示置于该列首位），用于排序定位。
 */
public class TaskMoveRequest {
    @NotNull
    private Long targetColumnId;
    private Long afterTaskId;

    public Long getTargetColumnId() { return targetColumnId; }
    public void setTargetColumnId(Long targetColumnId) { this.targetColumnId = targetColumnId; }
    public Long getAfterTaskId() { return afterTaskId; }
    public void setAfterTaskId(Long afterTaskId) { this.afterTaskId = afterTaskId; }
}
