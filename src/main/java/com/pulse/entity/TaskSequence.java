package com.pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 每项目任务号序列：用一行 + 行锁（FOR UPDATE）生成任务号，
 * 彻底消除「selectMax + insert」在并发下的竞态（P2 根治）。
 */
@Data
@TableName("task_sequences")
public class TaskSequence {
    @TableId(type = IdType.INPUT)
    private Long projectId;
    private Integer nextVal;
}
