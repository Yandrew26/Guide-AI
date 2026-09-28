package com.andrewproject.agent.controller;

import com.andrewproject.agent.dto.SystemUserDTO;
import com.andrewproject.agent.repository.SystemUserRepository;
import com.andrewproject.agent.service.SystemUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@RequestMapping("/user")
public class SystemUserController {

    @Resource
    private SystemUserService systemUserService;

    @Resource
    private SystemUserRepository systemUserRepository;

    @PostMapping("/addUser")
    public ResponseEntity<String> addUser(@RequestBody SystemUserDTO systemUserDTO) {
        systemUserService.addUser(systemUserDTO);
        systemUserRepository.createUserIfNotExist(systemUserDTO.getUserName(), systemUserDTO.getUserId());
        log.info("Neo4j - {} User node created", systemUserDTO.getUserName());
        return ResponseEntity.ok("User added successfully");
    }

    @GetMapping("/list")
    public ResponseEntity<List<SystemUserDTO>> list() {
        return ResponseEntity.ok(systemUserService.getAllUsers());
    }

    @GetMapping("/syncUsers")
    public ResponseEntity<String> syncUsers() {
        systemUserService.getAllUsers().forEach(user -> {
            systemUserRepository.createUserIfNotExist(user.getUserName(), user.getUserId());
            log.info("Neo4j - {} User node created", user.getUserName());
        });
        return ResponseEntity.ok("Users synced successfully");
    }
}
