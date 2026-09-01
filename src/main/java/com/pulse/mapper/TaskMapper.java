package com.pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pulse.entity.Task;
import org.apache.ibatis.annotations.*;

@Mapper
/**
 * {@link com.pulse.entity.Task} 的 MyBatis-Plus Mapper，对应表 {@code tasks}。
 * 继承 {@code BaseMapper<Task>}，除基础增删改查外，额外提供看板列内排序序号自增/自减
 * （{@code incrementSortOrder}/{@code decrementSortOrder}）与项目内最大任务号查询（{@code selectMaxTaskNumber}）。
 */
public interface TaskMapper extends BaseMapper<Task> {
    @Update("UPDATE tasks SET sort_order = sort_order + 1 WHERE board_column_id = #{columnId} AND sort_order >= #{fromOrder}")
    void incrementSortOrder(Long columnId, int fromOrder);

    @Update("UPDATE tasks SET sort_order = sort_order - 1 WHERE board_column_id = #{columnId} AND sort_order > #{fromOrder}")
    void decrementSortOrder(Long columnId, int fromOrder);

    /** 取项目内最大任务号（无任务时返回 0），用于幂等初始化序列行 */
    @Select("SELECT COALESCE(MAX(task_number), 0) FROM tasks WHERE project_id = #{projectId}")
    Integer selectMaxTaskNumber(@Param("projectId") Long projectId);
}
