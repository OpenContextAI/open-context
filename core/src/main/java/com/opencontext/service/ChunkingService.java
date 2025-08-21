package com.opencontext.service;

import com.opencontext.dto.StructuredChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkingService {

    /**
     * Converts parsed elements into structured chunks.
     * 
     * @param documentId Document ID
     * @param parsedElements List of elements parsed by the Unstructured API
     * @return List of structured chunks
     */
    public List<StructuredChunk> createChunks(UUID documentId, List<Map<String, Object>> parsedElements) {
        long startTime = System.currentTimeMillis();
        int totalElements = parsedElements.size();
        
        log.info("🧩 [CHUNKING] Starting chunking process: documentId={}, elements={}", documentId, totalElements);

        List<StructuredChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;
        int processedElements = 0;
        
        log.debug("📄 [CHUNKING] Processing {} elements for Title-based chunking", totalElements);

        // Variables for dividing chunks based on Title
        String currentTitle = null;
        StringBuilder currentChunkContent = new StringBuilder();
        List<Map<String, Object>> currentChunkElements = new ArrayList<>();
        int currentTitleLevel = 1;

        for (Map<String, Object> element : parsedElements) {
            processedElements++;
            String elementType = (String) element.get("type");
            String text = (String) element.get("text");
            
            if (processedElements % 50 == 0 || processedElements == totalElements) {
                log.debug("📊 [CHUNKING] Progress: {}/{} elements processed", processedElements, totalElements);
            }
            
            if (text == null || text.trim().isEmpty()) {
                log.debug("⚠️ [CHUNKING] Skipping empty element: type={}", elementType);
                continue;
            }

            // When Title is encountered, save the previous chunk and start a new chunk
            // Since title_depth=1 is set in Unstructured API, only # headers will come as Title
            boolean isUnstructuredTitle = "Title".equals(elementType);
            
            // Additionally check for # headers directly in text (as backup)
            boolean isH1Header = isH1MarkdownHeader(text);
            
            if (isUnstructuredTitle || isH1Header) {
                log.debug("🏷️ [CHUNKING] Detected H1 title/header: text='{}', elementType='{}', isUnstructuredTitle={}, isH1Header={}", 
                        text, elementType, isUnstructuredTitle, isH1Header);
                // Save previous chunk if exists
                if (currentChunkContent.length() > 0 && currentTitle != null) {
                    String chunkContent = currentChunkContent.toString().trim();
                    // Create as one chunk without splitting
                    StructuredChunk chunk = createTitleBasedChunk(documentId, chunkIndex++, currentTitle, 
                            chunkContent, currentTitleLevel, currentChunkElements);
                    chunks.add(chunk);
                    
                    // Log created chunk information
                    int tokenCount = estimateTokenCount(chunkContent);
                    log.info("📦 [CHUNK CREATED] Title: '{}', Length: {}, Tokens: ~{}, Level: {}, ChunkId: {}", 
                            chunk.getTitle(), chunk.getContent().length(), tokenCount, chunk.getHierarchyLevel(), chunk.getChunkId());
                    log.debug("📄 [CHUNK CONTENT] Content preview: {}", 
                            chunkContent.length() > 200 ? chunkContent.substring(0, 200) + "..." : chunkContent);
                }
                
                // Start new chunk
                currentTitle = isH1Header ? extractMarkdownHeaderText(text) : text;
                currentChunkContent = new StringBuilder();
                currentChunkElements = new ArrayList<>();
                
                // Determine Title level (H1 is level 1)
                currentTitleLevel = 1;
                
                log.info("🏷️ [CHUNKING] Starting new H1 chunk with title: '{}' (level: {})", 
                        currentTitle, currentTitleLevel);
            } else {
                // Elements without Title are considered as first elements and added to currentChunkContent
                if (currentTitle != null) {
                    // Add appropriate separator based on element type
                    if (currentChunkContent.length() > 0) {
                        currentChunkContent.append("\n\n");
                    }
                    
                    // Format based on element type
                    switch (elementType) {
                        case "Header" -> {
                            // Apply markdown formatting based on header level
                            int headerLevel = determineHeaderLevel(element);
                            currentChunkContent.append("#".repeat(Math.max(1, headerLevel))).append(" ").append(text);
                        }
                        case "BlockQuote" -> {
                            currentChunkContent.append("> ").append(text);
                        }
                        case "Code" -> {
                            currentChunkContent.append("```\n").append(text).append("\n```");
                        }
                        case "ListItem" -> {
                            currentChunkContent.append("• ").append(text);
                        }
                        case "HorizontalRule" -> {
                            currentChunkContent.append("---");
                        }
                        case "Table" -> {
                            currentChunkContent.append("[Table]\n").append(text);
                        }
                        default -> {
                            currentChunkContent.append(text);
                        }
                    }
                    
                    currentChunkElements.add(element);
                } else {
                    // Elements without Title are considered as first elements and added to currentChunkContent
                    log.debug("🔸 [CHUNKING] Adding element without title to content: type={}", elementType);
                    if (currentChunkContent.length() > 0) {
                        currentChunkContent.append("\n\n");
                    }
                    currentChunkContent.append(text);
                    currentChunkElements.add(element);
                    
                    // Set default title if first element is not Title
                    if (currentTitle == null && !currentChunkElements.isEmpty()) {
                        currentTitle = "Document Start Section";
                        currentTitleLevel = 1;
                        log.info("🏷️ [CHUNKING] Setting default title for document start");
                    }
                }
            }
        }
        
        // Process final chunk
        if (currentChunkContent.length() > 0) {
            String chunkContent = currentChunkContent.toString().trim();
            // Set default title if none exists
            if (currentTitle == null) {
                currentTitle = "Document Content";
                currentTitleLevel = 1;
            }
            
            // Create as one chunk without splitting
            StructuredChunk finalChunk = createTitleBasedChunk(documentId, chunkIndex++, currentTitle, 
                    chunkContent, currentTitleLevel, currentChunkElements);
            chunks.add(finalChunk);
            
            // Log final chunk information
            int finalTokenCount = estimateTokenCount(chunkContent);
            log.info("📦 [FINAL CHUNK] Title: '{}', Length: {}, Tokens: ~{}, Level: {}, ChunkId: {}", 
                    finalChunk.getTitle(), finalChunk.getContent().length(), finalTokenCount, finalChunk.getHierarchyLevel(), finalChunk.getChunkId());
            log.debug("📄 [FINAL CONTENT] Content preview: {}", 
                    chunkContent.length() > 200 ? chunkContent.substring(0, 200) + "..." : chunkContent);
        }

        long duration = System.currentTimeMillis() - startTime;
        int finalChunkCount = chunks.size();
        
        log.info("🎉 [CHUNKING] Title-based chunking completed successfully: documentId={}, elements={}, chunks={}, duration={}ms", 
                documentId, totalElements, finalChunkCount, duration);
        
        // Log chunk statistics
        if (finalChunkCount > 0) {
            double avgChunkLength = chunks.stream()
                    .mapToInt(c -> c.getContent().length())
                    .average()
                    .orElse(0.0);
            
            double avgTokenCount = chunks.stream()
                    .mapToInt(c -> estimateTokenCount(c.getContent()))
                    .average()
                    .orElse(0.0);
            
            log.info("📊 [CHUNKING STATS] avgChunkLength={}, avgTokens={}, totalChunks={}", 
                    Math.round(avgChunkLength), Math.round(avgTokenCount), finalChunkCount);
            
            // Log summary information for each chunk
            log.info("📋 [CHUNK SUMMARY] Generated chunks:");
            for (int i = 0; i < chunks.size(); i++) {
                StructuredChunk chunk = chunks.get(i);
                int chunkTokens = estimateTokenCount(chunk.getContent());
                log.info("  {}. Title: '{}' | Type: {} | Length: {} | Tokens: ~{} | Level: {}", 
                        i + 1, 
                        chunk.getTitle() != null ? chunk.getTitle() : "N/A", 
                        chunk.getElementType(), 
                        chunk.getContent().length(), 
                        chunkTokens,
                        chunk.getHierarchyLevel());
                
                // Display preview if content is long
                String contentPreview = chunk.getContent().length() > 500 ? 
                    chunk.getContent().substring(0, 500) + "..." : chunk.getContent();
                log.info("     Content Preview: {}", contentPreview);
            }
        }
        
        return chunks;
    }

    /**
     * Creates a Title-based chunk.
     */
    private StructuredChunk createTitleBasedChunk(UUID documentId, int chunkIndex, String title, 
                                                 String content, int titleLevel, List<Map<String, Object>> elements) {
        return StructuredChunk.builder()
                .documentId(documentId.toString())
                .chunkId(generateChunkId(documentId, chunkIndex))
                .content(content)
                .title(title)
                .hierarchyLevel(titleLevel)
                .parentChunkId(null) // Title-based chunks are independent
                .elementType("TitleBasedChunk")
                .metadata(createTitleBasedMetadata(title, titleLevel, elements))
                .build();
    }

    /**
     * Creates metadata for Title-based chunks.
     */
    private Map<String, Object> createTitleBasedMetadata(String title, int titleLevel, List<Map<String, Object>> elements) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("chunk_type", "title_based");
        metadata.put("title_level", titleLevel);
        metadata.put("element_count", elements.size());
        
        // Count elements by type
        Map<String, Long> elementTypeCounts = elements.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        e -> (String) e.get("type"),
                        java.util.stream.Collectors.counting()
                ));
        metadata.put("element_type_counts", elementTypeCounts);
        
        // Preserve metadata information for the first and last elements
        if (!elements.isEmpty()) {
            Map<String, Object> firstElement = elements.get(0);
            Map<String, Object> lastElement = elements.get(elements.size() - 1);
            
            if (firstElement.containsKey("metadata")) {
                metadata.put("first_element_metadata", firstElement.get("metadata"));
            }
            if (lastElement.containsKey("metadata")) {
                metadata.put("last_element_metadata", lastElement.get("metadata"));
            }
        }
        
        return metadata;
    }

    /**
     * Checks if it's an H1 markdown header (single #).
     * Since title_depth=1 is set, only # will come as Title, so simple check is sufficient
     */
    private boolean isH1MarkdownHeader(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        String trimmed = text.trim();
        
        // Check for pattern starting with single # followed by space
        if (trimmed.matches("^#\\s+.+")) {
            String headerText = trimmed.replaceFirst("^#\\s*", "").trim();
            
            // Exclude JavaScript keywords or console.log etc.
            if (headerText.matches("^(console\\.|function\\s|var\\s|let\\s|const\\s|if\\s*\\(|for\\s*\\(|while\\s*\\(|switch\\s*\\(|class\\s|return\\s|break\\s*;|continue\\s*;).*")) {
                log.debug("❌ [CHUNKING] Excluding JavaScript code from H1 header: '{}'", headerText);
                return false;
            }
            
            log.debug("✅ [CHUNKING] Valid H1 header detected: '{}'", headerText);
            return true;
        }
        
        return false;
    }

    /**
     * Extracts text from markdown header.
     */
    private String extractMarkdownHeaderText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        String trimmed = text.trim();
        // Remove # symbols and spaces to extract only the actual title text
        return trimmed.replaceFirst("^#+\\s*", "").trim();
    }

    /**
     * Estimates the token count of text.
     * Approximate calculation based on GPT series tokenizer
     * (For accurate token calculation, actual tokenizer library should be used)
     */
    private int estimateTokenCount(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        
        // Simple token estimation algorithm:
        // 1. Split by spaces into words
        // 2. On average: English word = 1.3 tokens, Korean character = 1.5 tokens
        // 3. Consider punctuation and special characters
        
        String cleanText = text.trim();
        
        // Word count based on spaces
        String[] words = cleanText.split("\\s+");
        int wordCount = words.length;
        
        // Korean character count
        long koreanCharCount = cleanText.chars()
                .filter(c -> Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HANGUL_SYLLABLES ||
                           Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HANGUL_JAMO ||
                           Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO)
                .count();
        
        // English character count
        long englishCharCount = cleanText.chars()
                .filter(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))
                .count();
        
        // Number and special character count
        long otherCharCount = cleanText.length() - koreanCharCount - englishCharCount;
        
        // Token count estimation
        // - English words: average 1.3 tokens
        // - Korean characters: 1.5 tokens
        // - Other characters: 0.8 tokens
        double estimatedTokens = (wordCount * 1.3) + (koreanCharCount * 1.5) + (otherCharCount * 0.8);
        
        // Ensure minimum 1 token
        return Math.max(1, (int) Math.round(estimatedTokens));
    }

    /**
     * Generates chunk ID.
     */
    private String generateChunkId(UUID documentId, int chunkIndex) {
        return documentId.toString() + "-chunk-" + chunkIndex;
    }

    /**
     * Determines header level.
     */
    private int determineHeaderLevel(Map<String, Object> element) {
        // Attempt to extract level information from metadata
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) element.get("metadata");
        if (metadata != null && metadata.containsKey("category_depth")) {
            try {
                return ((Number) metadata.get("category_depth")).intValue();
            } catch (Exception e) {
                log.debug("Failed to extract header level from metadata", e);
            }
        }
        
        // Return default value
        return 2;
    }
}