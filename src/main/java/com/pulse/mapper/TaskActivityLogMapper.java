package com.pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pulse.entity.TaskActivityLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * {@link com.pulse.entity.TaskActivityLog} 的 MyBatis-Plus Mapper，对应表 {@code task_activity_log}。
 * 继承 {@code BaseMapper<TaskActivityLog>}，提供任务活动日志的增删改查与分页能力。
 */
public interface TaskActivityLogMapper extends BaseMapper<TaskActivityLog> {
}
