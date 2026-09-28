package com.andrewproject.agent.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import com.andrewproject.agent.repository.EbookRepository;
import com.andrewproject.agent.service.UploadService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.autoconfigure.ElasticsearchVectorStoreProperties;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/upload")
public class UploadController {

    private static final String textField = "content";

    private static final String vectorField = "embedding";

    @jakarta.annotation.Resource
    private UploadService uploadService;

    @jakarta.annotation.Resource
    private EbookRepository ebookRepository;

    private final ElasticsearchVectorStore selfElasticsearchVectorStore;

    private final ElasticsearchClient elasticsearchClient;

    private final ElasticsearchVectorStoreProperties options;

    public UploadController(ElasticsearchVectorStore selfElasticsearchVectorStore, ElasticsearchVectorStoreProperties options,
                            ElasticsearchClient elasticsearchClient) {
        this.selfElasticsearchVectorStore = selfElasticsearchVectorStore;
        this.options = options;
        this.elasticsearchClient = elasticsearchClient;
    }

    @PostMapping("/pdf")
    public ResponseEntity<String> pdfUpload(@RequestPart("file") MultipartFile file) {
        // "Dr. Seuss.pdf" used to become "Dr"; a missing name became a null title
        String name = StringUtils.defaultString(file.getOriginalFilename()).strip();
        String bookTitle = StringUtils.removeEndIgnoreCase(name, ".pdf").strip();
        if (bookTitle.isEmpty() || !name.toLowerCase().endsWith(".pdf")) {
            return ResponseEntity.badRequest().body("Only named .pdf files can be uploaded.");
        }
        Resource resource = file.getResource();
        if (ebookRepository.findByTitle(bookTitle) != null) {
            return ResponseEntity.ok("File already exists in the database.");
        }
        List<Document> pdfDocuments = uploadService.pdfUpload(resource, bookTitle);
        if (pdfDocuments.isEmpty()) {
            return ResponseEntity.unprocessableEntity().body("No readable text found in this PDF. Scanned PDFs need OCR first.");
        }
        createIndexIfNotExists();
        selfElasticsearchVectorStore.add(pdfDocuments);
        return ResponseEntity.ok("File uploaded and processed successfully.");
    }

    @GetMapping("/search")
    public List<Document> search(@RequestParam("query") String query,
                                 @RequestParam("topK") Integer topK) {
        log.info("search begin");
        return uploadService.search(query, topK);
    }

    @GetMapping("/search/author")
    public List<String> searchAuthor(@RequestParam("bookList") List<String> bookList) {
        log.info("author search begin");
        int topK = bookList.size();
        // built, not string-spliced: a quote in a title can't rewrite the filter
        var requestBuilder = SearchRequest.builder()
                .query("who is the author of these documents?")
                .filterExpression(new FilterExpressionBuilder().in("book_title", bookList.toArray()).build())
                .topK(topK)
                .build();
        List<Document> search = selfElasticsearchVectorStore.similaritySearch(requestBuilder);
        return search.stream().map(Document::getText).toList();
    }

    private void createIndexIfNotExists() {
        try {
            String indexName = options.getIndexName();
            Integer dimsLength = options.getDimensions();

            if (StringUtils.isBlank(indexName)) {
                throw new IllegalArgumentException("Elastic search index name must be provided");
            }

            boolean exists = selfElasticsearchVectorStore.indexExists();
            if (exists) {
                log.debug("Index {} already exists. Skipping creation.", indexName);
                return;
            }

            CreateIndexResponse indexResponse = elasticsearchClient.indices()
                    .create(createIndexBuilder -> createIndexBuilder.index(indexName));

            if (!indexResponse.acknowledged()) {
                throw new RuntimeException("failed to create index");
            }

            log.info("create elasticsearch index {} successfully", indexName);
        }
        catch (IOException e) {
            log.error("failed to create index", e);
            throw new RuntimeException(e);
        }
    }
}
