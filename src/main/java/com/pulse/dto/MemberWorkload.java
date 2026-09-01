package com.pulse.dto;

import lombok.Data;

/**
 * 成员负载快照：用于智能分派时作为 AI 的上下文输入。
 * 仅含任务计数/故事点等业务指标，不含任何密钥明文。
 */
@Data
public class MemberWorkload {
    private Long userId;
    private String displayName;
    /** 进行中任务数（IN_PROGRESS / REVIEW） */
    private int inProgressTasks;
    /** 未完成（非 DONE）任务数 */
    private int openTasks;
    /** 未完成任务累计故事点 */
    private int openStoryPoints;
    /** 逾期（dueDate 已过且未完成）任务数 */
    private int overdueTasks;
}
