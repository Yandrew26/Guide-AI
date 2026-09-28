package com.andrewproject.agent.service;


import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;

import java.util.List;

public interface UploadService {
    List<Document> pdfUpload(Resource resource, String bookTitle);
    List<Document> search(String query, Integer topK);
}
