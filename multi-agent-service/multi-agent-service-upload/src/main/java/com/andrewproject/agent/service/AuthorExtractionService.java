package com.andrewproject.agent.service;

import java.util.Set;

public interface AuthorExtractionService {

    Set<String> extractAuthors(String text);
}
