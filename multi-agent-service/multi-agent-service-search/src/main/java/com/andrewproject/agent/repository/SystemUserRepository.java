package com.andrewproject.agent.repository;

import com.andrewproject.agent.entity.SystemUserNeo4j;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SystemUserRepository extends Neo4jRepository<SystemUserNeo4j, Long> {

    @Query("MATCH (b:Ebook {title: $title})<-[:READ]-(u:User)-[:READ]->(r:Ebook) WHERE u.user_id <> $userId AND r.title <> $title RETURN DISTINCT r.title")
    List<String> findEbookTitlesWithSameUser(@Param("title") String title, @Param("userId") String userId);

    @Query("MATCH (a:User {user_id: $userId}) MATCH (b:Ebook {title: $title}) MERGE (a)-[:READ]->(b)")
    void createUserEbookRelationshipIfNotExist(@Param("userId") String userId, @Param("title") String title);

    @Query("MERGE (u:User {name: $userName, user_id: $userId})")
    void createUserIfNotExist(@Param("userName") String userName, @Param("userId") String userId);
}
