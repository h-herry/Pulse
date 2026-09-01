package com.pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pulse.entity.TaskTag;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * {@link com.pulse.entity.TaskTag} 的 MyBatis-Plus Mapper，对应表 {@code task_tags}。
 * 继承 {@code BaseMapper<TaskTag>}，提供任务-标签关联（多对多）的增删改查与分页能力。
 */
public interface TaskTagMapper extends BaseMapper<TaskTag> {
}
