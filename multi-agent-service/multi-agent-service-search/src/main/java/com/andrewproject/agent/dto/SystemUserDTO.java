package com.andrewproject.agent.dto;

import com.andrewproject.agent.entity.SystemUserSQL;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class SystemUserDTO {

    private String userId;

    private String userName;

    private String email;

    private String mobile;

    public SystemUserDTO(SystemUserSQL systemUserSQL) {
        this.userId = systemUserSQL.getUserId();
        this.userName = systemUserSQL.getUserName();
        this.email = systemUserSQL.getEmail();
        this.mobile = systemUserSQL.getMobile();
    }
}
