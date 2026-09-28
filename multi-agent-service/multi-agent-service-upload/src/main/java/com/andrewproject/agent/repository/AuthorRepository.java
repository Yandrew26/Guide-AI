package com.andrewproject.agent.repository;

import com.andrewproject.agent.entity.Author;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuthorRepository extends Neo4jRepository<Author, Long> {

    @Query("MATCH (a:Author {name: $name}) MATCH (b:Ebook {title: $bookTitle}) MERGE (a)-[:WROTE]->(b)")
    void createAuthorEbookRelationship(@Param("name") String name, @Param("bookTitle") String bookTitle);

    @Query("MERGE (a:Author {name: $name})")
    void createAuthorIfNotExists(@Param("name") String name);
}
