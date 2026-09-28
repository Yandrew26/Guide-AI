package com.andrewproject.agent.service.Impl;

import com.andrewproject.agent.dto.RagAuthBaseDTO;
import com.andrewproject.agent.entity.RagAuthBase;
import com.andrewproject.agent.mapper.RagAuthBaseMapper;
import com.andrewproject.agent.service.RagAuthBaseService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class RagAuthBaseServiceImpl extends ServiceImpl<RagAuthBaseMapper, RagAuthBase> implements RagAuthBaseService {

    @Override
    public RagAuthBaseDTO getByAuthId(String authId) {
        RagAuthBase ragAuthBase = this.lambdaQuery()
                .eq(RagAuthBase::getAuthId, authId)
                .eq(RagAuthBase::getEnabledStatus, true)
                .one();
        if (ragAuthBase == null) {
            return null;
        }
        return new RagAuthBaseDTO(ragAuthBase);
    }
}
