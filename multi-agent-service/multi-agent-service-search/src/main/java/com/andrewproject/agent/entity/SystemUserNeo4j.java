package com.andrewproject.agent.entity;

import org.springframework.data.neo4j.core.schema.*;
import lombok.Data;

import java.util.List;

@Data
@Node("User")
public class SystemUserNeo4j {

    @Id
    @GeneratedValue
    private Long neo4jId;

    @Property("user_id")
    private String userId;

    @Property("name")
    private String userName;

    @Relationship(type = "READ", direction = Relationship.Direction.OUTGOING)
    private List<Ebook> ebook;
}