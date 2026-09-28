package com.andrewproject.agent.repository;

import com.andrewproject.agent.entity.Ebook;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

public interface EbookRepository extends Neo4jRepository<Ebook, Long> {
    Ebook findByTitle(String title);

    @Query("MERGE (a:Ebook {title: $title})")
    void createEbookIfNotExists(@Param("title") String title);
}
