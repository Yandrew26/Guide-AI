package com.andrewproject.agent.repository;

import com.andrewproject.agent.entity.Author;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuthorRepository extends Neo4jRepository<Author, Long> {

    @Query("MATCH (a:Author {name: $name})-[:WROTE]->(b:Ebook) RETURN b.title AS title")
    List<String> findEbookTitlesByAuthorName(@Param("name") String name);

    @Query("MATCH (b:Ebook {title: $title})<-[:WROTE]-(a:Author)-[:WROTE]->(r:Ebook) RETURN r.title")
    List<String> findEbookTitlesWithSameAuthor(@Param("title") String title);
}