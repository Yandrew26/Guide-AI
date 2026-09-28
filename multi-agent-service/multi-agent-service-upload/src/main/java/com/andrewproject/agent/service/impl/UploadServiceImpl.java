package com.andrewproject.agent.service.impl;

import com.andrewproject.agent.entity.Ebook;
import com.andrewproject.agent.repository.AuthorRepository;
import com.andrewproject.agent.repository.EbookRepository;
import com.andrewproject.agent.service.AuthorExtractionService;
import com.andrewproject.agent.service.UploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@Slf4j
public class UploadServiceImpl implements UploadService {

    private final VectorStore vectorStore;
    private final ChatClient.Builder chatClientBuilder;

    @jakarta.annotation.Resource
    private AuthorExtractionService authorExtractionService;

    @jakarta.annotation.Resource
    private AuthorRepository authorRepository;

    @jakarta.annotation.Resource
    private EbookRepository ebookRepository;

    // Configuration constants - change these to adjust filtering behavior
    private static final int   MAX_PAGES_AFTER_FILTERING = 50; // Set to 0 or negative to disable
    private static final int MIN_WORD_COUNT_PER_PAGE = 50;

    // Patterns to detect non-content pages
    private static final List<Pattern> NON_CONTENT_PATTERNS = List.of(
            Pattern.compile("(?i)^\\s*(table\\s+of\\s+contents|contents)\\s*$"),
            Pattern.compile("(?i)^\\s*copyright\\s*(©|\\(c\\)|page)?"),
            Pattern.compile("(?i)^\\s*(references|bibliography|works\\s+cited)\\s*$"),
            Pattern.compile("(?i)^\\s*(index|glossary|appendix)\\s*$"),
            Pattern.compile("(?i)^\\s*(acknowledgements?|preface|foreword)\\s*$"),
            Pattern.compile("(?i)^\\s*(about\\s+the\\s+author|author\\s+bio)"),
            Pattern.compile("(?i)^\\s*(dedication|also\\s+by)"),
            Pattern.compile("(?i)all\\s+rights\\s+reserved"),
            Pattern.compile("(?i)isbn[:\\s-]*\\d"),
            Pattern.compile("(?i)published\\s+by"),
            Pattern.compile("(?i)^\\s*endnotes?\\s*$")
    );

    public UploadServiceImpl(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClientBuilder = chatClientBuilder;
    }

    @Override
    public List<Document> pdfUpload(Resource resource, String bookTitle) {
        log.info("book: {} - start read pdf file", bookTitle);
        PagePdfDocumentReader reader = new PagePdfDocumentReader(resource);
        List<Document> pages = reader.read();
        int pageCount = pages.size();
        log.info("PDF page count: {}", pageCount);

        // Extract authors from first 3 pages
        StringBuilder headerText = new StringBuilder();
        for (int i = 0; i < Math.min(3, pages.size()); i++) {
            headerText.append(pages.get(i).getText()).append("\n");
        }
        Set<String> authors = authorExtractionService.extractAuthors(headerText.toString());
        List<String> authorList = new ArrayList<>(authors);
        log.info("Extracted {} author(s): {}", authorList.size(), authors);

        // Filter pages - remove non-content sections
        List<Document> filteredPages = filterPages(pages);
        log.info("Filtered from {} to {} pages (removed non-content sections)", pageCount, filteredPages.size());

        // Apply page limit if configured
        filteredPages = applyPageLimit(filteredPages);
        log.info("After page limit: {} pages", filteredPages.size());

        // Add metadata to all filtered pages
        for (Document page : filteredPages) {
            page.getMetadata().put("authors", authorList);
            page.getMetadata().put("book_title", bookTitle);
            page.getMetadata().put("total_pages", pageCount);
        }

        // token text splitter
        TokenTextSplitter tokenTextSplitter = TokenTextSplitter.builder()
                .withChunkSize(1500)
                .withMinChunkSizeChars(500)
                .withMinChunkLengthToEmbed(500)
                .withMaxNumChunks(4000)
                .withKeepSeparator(true)
                .build();
        log.info("Starting token text splitter");
        List<Document> chunks = tokenTextSplitter.split(filteredPages);
        log.info("Created {} chunks from {} pages", chunks.size(), filteredPages.size());
        if (chunks.isEmpty()) {
            return chunks; // nothing to index: don't put an unsearchable book on the shelf
        }

        // Add authors and book to Neo4j
        ebookRepository.createEbookIfNotExists(bookTitle);
        log.info("Neo4j - Created {} Ebook node", bookTitle);
        addAuthorToNeo4j(authorList, bookTitle);

        return chunks;
    }

    /**
     * Filter pages - remove non-content pages and pages with insufficient content
     */
    private List<Document> filterPages(List<Document> pages) {
        List<Document> filtered = new ArrayList<>();

        for (int i = 0; i < pages.size(); i++) {
            Document page = pages.get(i);
            String text = page.getText().trim();

            // Check if page appears to be a non-content page
            if (isNonContentPage(text)) {
                log.debug("Skipping non-content page {} (detected as TOC, copyright, references, etc.)", i + 1);
                continue;
            }

            // Check word count
            int wordCount = text.split("\\s+").length;
            if (wordCount < MIN_WORD_COUNT_PER_PAGE) {
                log.debug("Skipping page {} - only {} words", i + 1, wordCount);
                continue;
            }

            filtered.add(page);
        }

        return filtered;
    }

    /**
     * Check if a page is likely a non-content page (TOC, copyright, references, etc.)
     */
    private boolean isNonContentPage(String text) {
        if (text == null || text.trim().isEmpty()) {
            return true;
        }

        // Get first 500 characters for pattern matching (usually headers are at the top)
        String header = text.length() > 500 ? text.substring(0, 500) : text;

        // Check against known non-content patterns
        for (Pattern pattern : NON_CONTENT_PATTERNS) {
            if (pattern.matcher(header).find()) {
                return true;
            }
        }

        // Check if page is mostly numbers and dots (likely TOC). Ignore whitespace: the layout-preserving
        // reader pads lines with spaces, which made ordinary prose pages look "mostly empty".
        String visible = text.replaceAll("\\s+", "");
        String cleaned = visible.replaceAll("[\\d.]+", "");
        if (cleaned.length() < visible.length() * 0.3) {
            return true;
        }

        return false;
    }

    /**
     * Apply page limit based on configuration
     */
    private List<Document> applyPageLimit(List<Document> pages) {
        // If MAX_PAGES_AFTER_FILTERING is 0 or negative, return all pages
        if (MAX_PAGES_AFTER_FILTERING <= 0) {
            log.info("No page limit applied");
            return pages;
        }

        // If we have fewer pages than the limit, return all
        if (pages.size() <= MAX_PAGES_AFTER_FILTERING) {
            log.info("Page count ({}) is within limit ({})", pages.size(), MAX_PAGES_AFTER_FILTERING);
            return pages;
        }

        log.info("Applying page limit: taking first {} pages out of {}", MAX_PAGES_AFTER_FILTERING, pages.size());
        return pages.subList(0, MAX_PAGES_AFTER_FILTERING);
    }

    private void addAuthorToNeo4j(List<String> authorList, String bookTitle) {
        if (authorList.isEmpty()) {
            log.info("no authors found, skipping Neo4j");
            return;
        }
        for (String authorName : authorList) {
            authorRepository.createAuthorIfNotExists(authorName);
            authorRepository.createAuthorEbookRelationship(authorName, bookTitle);
            log.info("Neo4j - {} Author node, Author({})->Ebook({}) relationship", authorName, authorName, bookTitle);
        }
    }

    @Override
    public List<Document> search(String query, Integer topK) {
        String userQuery = expandQuery(query);
        log.info("search begin");
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
