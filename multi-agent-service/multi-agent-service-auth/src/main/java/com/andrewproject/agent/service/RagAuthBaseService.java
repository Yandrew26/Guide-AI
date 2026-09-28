package com.andrewproject.agent.service;

import com.andrewproject.agent.dto.RagAuthBaseDTO;
import com.andrewproject.agent.entity.RagAuthBase;
import com.baomidou.mybatisplus.spring.service.IService;

public interface RagAuthBaseService extends IService<RagAuthBase> {

    RagAuthBaseDTO getByAuthId(String authId);
}
