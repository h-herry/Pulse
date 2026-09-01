package com.pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pulse.entity.TaskSequence;
import org.apache.ibatis.annotations.*;

@Mapper
/**
 * {@link com.pulse.entity.TaskSequence} 的 MyBatis-Plus Mapper，对应表 {@code task_sequences}。
 * 继承 {@code BaseMapper<TaskSequence>}，除基础能力外，额外提供行锁取号（{@code selectForUpdate}）、
 * 序号消费（{@code increment}）与幂等初始化（{@code insertSeq}），用于并发安全地分配任务号。
 */
public interface TaskSequenceMapper extends BaseMapper<TaskSequence> {

    /** 锁定项目序列行，事务提交前其余并发事务阻塞于此，保证序号串行分配 */
    @Select("SELECT * FROM task_sequences WHERE project_id = #{pid} FOR UPDATE")
    @Results({
        @Result(column = "project_id", property = "projectId"),
        @Result(column = "next_val", property = "nextVal")
    })
    TaskSequence selectForUpdate(@Param("pid") Long projectId);

    /** 消费一个序号 */
    @Update("UPDATE task_sequences SET next_val = next_val + 1 WHERE project_id = #{pid}")
    int increment(@Param("pid") Long projectId);

    /** 幂等初始化：并发下仅一个成功，其余因主键冲突被忽略 */
    @Insert("INSERT INTO task_sequences (project_id, next_val) VALUES (#{projectId}, #{nextVal})")
    int insertSeq(@Param("projectId") Long projectId, @Param("nextVal") Integer nextVal);
}
