package com.andrewproject.agent.mapper;

import com.andrewproject.agent.entity.SystemUserSQL;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SystemUserMapper extends BaseMapper<SystemUserSQL> {
}
