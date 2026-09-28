package com.andrewproject.agent.service.impl;

import com.andrewproject.agent.service.AuthorExtractionService;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
@Lazy
public class AuthorExtractionServiceImpl implements AuthorExtractionService {
    private final StanfordCoreNLP pipeline;

    // Maximum number of authors to extract
    private static final int MAX_AUTHORS = 3;

    // Patterns to identify non-author roles that should be excluded
    private static final List<Pattern> NON_AUTHOR_PATTERNS = List.of(
            Pattern.compile("(?i)illustrat(ed|or|ion)"),
            Pattern.compile("(?i)edit(ed|or)"),
            Pattern.compile("(?i)translat(ed|or|ion)"),
            Pattern.compile("(?i)foreword\\s+by"),
            Pattern.compile("(?i)introduction\\s+by"),
            Pattern.compile("(?i)preface\\s+by"),
            Pattern.compile("(?i)afterword\\s+by"),
            Pattern.compile("(?i)narrat(ed|or)"),
            Pattern.compile("(?i)contribut(ed|or)"),
            Pattern.compile("(?i)compil(ed|er)")
    );

    public AuthorExtractionServiceImpl() {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
        props.setProperty("ner.useSUTime", "false");
        props.setProperty("ner.applyNumericClassifiers", "false");
        this.pipeline = new StanfordCoreNLP(props);
    }

    @Override
    public Set<String> extractAuthors(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new LinkedHashSet<>();
        }

        Annotation doc = new Annotation(text);
        pipeline.annotate(doc);

        // Use LinkedHashSet to maintain insertion order and prevent duplicates
        Set<String> authors = new LinkedHashSet<>();

        for (CoreMap sentence : doc.get(CoreAnnotations.SentencesAnnotation.class)) {
            // Stop if we already have enough authors
            if (authors.size() >= MAX_AUTHORS) {
                break;
            }

            String sentenceText = sentence.toString();

            // Skip sentences that contain non-author role indicators
            if (containsNonAuthorRole(sentenceText)) {
                continue;
            }

            StringBuilder current = new StringBuilder();

            for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                String word = token.word();
                String ner = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);

                if ("PERSON".equals(ner)) {
                    current.append(word).append(" ");
                } else {
                    if (!current.toString().isBlank()) {
                        String name = current.toString().trim();
                        // Only keep names with at least 2 words (full names)
                        if (name.split("\\s+").length >= 2 && !isDuplicate(authors, name)) {
                            authors.add(name);
                            // Stop if we've reached the limit
                            if (authors.size() >= MAX_AUTHORS) {
                                return authors;
                            }
                        }
                        current = new StringBuilder();
                    }
                }
            }

            if (!current.toString().isBlank()) {
                String name = current.toString().trim();
                // Only keep names with at least 2 words (full names)
                if (name.split("\\s+").length >= 2 && !isDuplicate(authors, name)) {
                    authors.add(name);
                    // Stop if we've reached the limit
                    if (authors.size() >= MAX_AUTHORS) {
                        return authors;
                    }
                }
            }
        }

        return authors;
    }

    /**
     * Check if the sentence contains indicators of non-author roles
     */
    private boolean containsNonAuthorRole(String text) {
        for (Pattern pattern : NON_AUTHOR_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a name is a duplicate (case-insensitive and handles slight variations)
     */
    private boolean isDuplicate(Set<String> existingNames, String newName) {
        String normalizedNew = normalizeName(newName);
        for (String existing : existingNames) {
            String normalizedExisting = normalizeName(existing);
            // Check if names match or if one contains the other (e.g., "John Smith" vs "John A. Smith")
            if (normalizedExisting.equals(normalizedNew) ||
                    normalizedExisting.contains(normalizedNew) ||
                    normalizedNew.contains(normalizedExisting)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Normalize a name for comparison (remove extra spaces, convert to lowercase)
     */
    private String normalizeName(String name) {
        return name.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
