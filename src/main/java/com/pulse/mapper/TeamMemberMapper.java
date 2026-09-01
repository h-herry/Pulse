package com.pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pulse.entity.TeamMember;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * {@link com.pulse.entity.TeamMember} 的 MyBatis-Plus Mapper，对应表 {@code team_members}。
 * 继承 {@code BaseMapper<TeamMember>}，提供团队-成员关联（含团队内角色）的增删改查与分页能力。
 */
public interface TeamMemberMapper extends BaseMapper<TeamMember> {
}
