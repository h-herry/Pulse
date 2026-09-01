package com.pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pulse.entity.Project;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * {@link com.pulse.entity.Project} 的 MyBatis-Plus Mapper，对应表 {@code projects}。
 * 继承 {@code BaseMapper<Project>}，提供项目的增删改查与分页能力（含软删除状态过滤）。
 */
public interface ProjectMapper extends BaseMapper<Project> {
}
