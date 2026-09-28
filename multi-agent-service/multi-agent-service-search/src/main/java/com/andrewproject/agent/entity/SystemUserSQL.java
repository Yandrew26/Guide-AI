package com.andrewproject.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("system_user")
public class SystemUserSQL {

    @TableId(
            type = IdType.ASSIGN_ID
    )
    private Long id;

    private String userId;

    private String userName;

    private String email;

    private String mobile;

    private LocalDateTime createdDate;
}
