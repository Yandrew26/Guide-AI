package com.andrewproject.agent.service.impl;

import com.andrewproject.agent.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class SearchServiceImpl implements SearchService {

    private static final Logger logger = LoggerFactory.getLogger(SearchServiceImpl.class);

    private final VectorStore vectorStore;

    private final ChatClient.Builder chatClientBuilder;

    public SearchServiceImpl(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClientBuilder = chatClientBuilder;
    }

    @Override
    public List<Document> search(String query, Integer topK) {
        String userQuery = expandQuery(query);
        logger.info("search begin");
        return vectorStore.similaritySearch(SearchRequest
                .builder()
                .query(userQuery)
                .topK(topK)
                .similarityThreshold(0.4f)
                .build());
    }

    private String expandQuery(String userQuery) {
        log.info("Expanding user's query...");
        if (userQuery == null || userQuery.trim().isEmpty()) {
            return userQuery;
        }

        if (userQuery.length() > 30) {
            return userQuery;
        }

        String expandedQuery = chatClientBuilder.build()
                .prompt("Based on the user's query to search for books and articles about the following, help expand the query so it is better understood. Do not change the meaning and topic of the query and only return the result of a expanded prompt for similarity search: " + userQuery)
                .call()
                .content();

        log.info("User's query has been expanded: {}", expandedQuery);

        return expandedQuery;
    }
}

