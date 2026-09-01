package com.pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pulse.entity.Team;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * {@link com.pulse.entity.Team} 的 MyBatis-Plus Mapper，对应表 {@code teams}。
 * 继承 {@code BaseMapper<Team>}，提供团队（多租户一级隔离边界）的增删改查与分页能力。
 */
public interface TeamMapper extends BaseMapper<Team> {
}
