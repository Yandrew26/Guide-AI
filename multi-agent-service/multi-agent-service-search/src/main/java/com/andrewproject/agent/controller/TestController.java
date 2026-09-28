package com.andrewproject.agent.controller;

import com.andrewproject.agent.dto.SystemUserDTO;
import com.andrewproject.agent.entity.Author;
import com.andrewproject.agent.repository.AuthorRepository;
import com.andrewproject.agent.repository.SystemUserRepository;
import com.andrewproject.agent.service.SystemUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Controller
@Slf4j
@RequestMapping("/test")
public class TestController {

    private final AuthorRepository authorRepository;

    @Resource
    private SystemUserService systemUserService;

    @Resource
    private SystemUserRepository systemUserRepository;

    private final Neo4jClient neo4jClient;

    public TestController(AuthorRepository authorRepository, Neo4jClient neo4jClient) {
        this.authorRepository = authorRepository;
        this.neo4jClient = neo4jClient;
    }

    @GetMapping("/books")
    public ResponseEntity<Collection<Map<String, Object>>> books() {
        return ResponseEntity.ok(neo4jClient.query(
                "MATCH (b:Ebook) OPTIONAL MATCH (a:Author)-[:WROTE]->(b) " +
                "RETURN b.title AS title, collect(a.name) AS authors ORDER BY title").fetch().all());
    }

    @GetMapping("/books/byAuthor")
    public ResponseEntity<List<String>> getBooksByAuthorName(@RequestParam("name") String name) {
        List<String> ebooks = authorRepository.findEbookTitlesByAuthorName(name);
        if (ebooks == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ebooks);
    }

    @GetMapping("/books/sameAuthor")
    public ResponseEntity<List<String>> getBooksBySameAuthor(@RequestParam("bookTitle") String bookTitle) {
        List<String> ebooks = authorRepository.findEbookTitlesWithSameAuthor(bookTitle);
        if (ebooks == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ebooks);
    }

    @PostMapping("/addUser")
    public ResponseEntity<String> addUser(@RequestBody SystemUserDTO systemUserDTO) {
        systemUserService.addUser(systemUserDTO);
        return ResponseEntity.ok("User added successfully");
    }

    @GetMapping("/syncUsers")
    public ResponseEntity<String> syncUsers() {
        systemUserService.getAllUsers().forEach(user -> {
            systemUserRepository.createUserIfNotExist(user.getUserName(), user.getUserId());
            log.info("Neo4j - {} User node", user.getUserName());
        });
        return ResponseEntity.ok("User added successfully");
    }
}
