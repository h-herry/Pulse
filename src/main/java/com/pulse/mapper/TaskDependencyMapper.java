package com.pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pulse.entity.TaskDependency;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * {@link com.pulse.entity.TaskDependency} 的 MyBatis-Plus Mapper，对应表 {@code task_dependencies}。
 * 继承 {@code BaseMapper<TaskDependency>}，提供任务依赖关系的增删改查与分页能力。
 */
public interface TaskDependencyMapper extends BaseMapper<TaskDependency> {
}
