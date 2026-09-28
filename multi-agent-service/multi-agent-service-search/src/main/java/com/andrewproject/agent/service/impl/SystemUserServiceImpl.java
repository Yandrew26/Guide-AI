package com.andrewproject.agent.service.impl;

import com.andrewproject.agent.dto.SystemUserDTO;
import com.andrewproject.agent.entity.SystemUserNeo4j;
import com.andrewproject.agent.entity.SystemUserSQL;
import com.andrewproject.agent.mapper.SystemUserMapper;
import com.andrewproject.agent.repository.SystemUserRepository;
import com.andrewproject.agent.service.SystemUserService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SystemUserServiceImpl extends ServiceImpl<SystemUserMapper, SystemUserSQL> implements SystemUserService {

    @Resource
    private SystemUserRepository systemUserRepository;

    @Override
    public String getUserNameByUserId(String userId) {
        return super.lambdaQuery()
                .eq(SystemUserSQL::getUserId, userId)
                .oneOpt()
                .map(SystemUserSQL::getUserName)
                .orElse(userId);
    }

    @Override
    public void addUser(SystemUserDTO systemUserDTO) {
        SystemUserNeo4j systemUserNeo4j = new SystemUserNeo4j();
        BeanUtils.copyProperties(systemUserDTO, systemUserNeo4j);
        systemUserRepository.save(systemUserNeo4j);
        SystemUserSQL systemUserSQL = new SystemUserSQL();
        BeanUtils.copyProperties(systemUserDTO, systemUserSQL);
        super.save(systemUserSQL);
    }

    @Override
    public List<SystemUserDTO> getAllUsers() {
        return super.lambdaQuery()
                .list()
                .stream()
                .map(SystemUserDTO::new)
                .toList();
    }
}
