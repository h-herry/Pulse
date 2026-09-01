package com.pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pulse.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * {@link com.pulse.entity.User} 的 MyBatis-Plus Mapper，对应表 {@code users}。
 * 继承 {@code BaseMapper<User>}，提供用户账户（身份、全局角色、启用态等）的增删改查与分页能力。
 */
public interface UserMapper extends BaseMapper<User> {
}
