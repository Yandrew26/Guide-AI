package com.andrewproject.agent.service;

import com.andrewproject.agent.dto.SystemUserDTO;
import com.andrewproject.agent.entity.SystemUserSQL;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

public interface SystemUserService extends IService<SystemUserSQL> {

    String getUserNameByUserId(String userId);
    void addUser(SystemUserDTO systemUserDTO);
    List<SystemUserDTO> getAllUsers();
}
