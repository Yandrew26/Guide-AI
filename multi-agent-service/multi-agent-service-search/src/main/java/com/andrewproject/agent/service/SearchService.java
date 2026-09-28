package com.andrewproject.agent.service;

import org.springframework.ai.document.Document;

import java.util.List;

public interface SearchService {
    List<Document> search(String query, Integer topK);
}

