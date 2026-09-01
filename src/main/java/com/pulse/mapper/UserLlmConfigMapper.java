package com.pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pulse.entity.UserLlmConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * {@link com.pulse.entity.UserLlmConfig} 的 MyBatis-Plus Mapper，对应表 {@code user_llm_config}。
 * 继承 {@code BaseMapper<UserLlmConfig>}，提供用户级 LLM 配置的增删改查与分页能力。
 */
public interface UserLlmConfigMapper extends BaseMapper<UserLlmConfig> {
}
