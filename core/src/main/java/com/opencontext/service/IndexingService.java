package com.opencontext.service;

import com.opencontext.dto.StructuredChunk;
import com.opencontext.entity.DocumentChunk;
import com.opencontext.entity.SourceDocument;
import com.opencontext.enums.ErrorCode;
import com.opencontext.exception.BusinessException;
import com.opencontext.repository.DocumentChunkRepository;
import com.opencontext.repository.SourceDocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Arrays;

/**
 * Service for storing embedded chunks in Elasticsearch and PostgreSQL.
 * 
 * Stores vectors and metadata in Elasticsearch for search,
 * and hierarchical structure information in PostgreSQL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class IndexingService {

    private final DocumentChunkRepository documentChunkRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.elasticsearch.url:http://localhost:9200}")
    private String elasticsearchUrl;

    @Value("${app.elasticsearch.index:document-chunks}")
    private String indexName;

    /**
     * Stores embedded chunks in Elasticsearch and PostgreSQL.
     * 
     * @param documentId Document ID
     * @param embeddedChunks List of embedded chunks to store
     */
    public void indexChunks(UUID documentId, List<StructuredChunk> embeddedChunks) {
        long startTime = System.currentTimeMillis();
        int totalChunks = embeddedChunks.size();
        
        log.info("📎 [INDEXING] Starting chunk indexing: documentId={}, chunks={}", documentId, totalChunks);

        try {
            // Step 1: Store vector data in Elasticsearch
            log.debug("🔍 [INDEXING] Step 1/2: Indexing to Elasticsearch: chunks={}", totalChunks);
            bulkIndexToElasticsearch(embeddedChunks);
            log.info("✅ [INDEXING] Elasticsearch indexing completed: documentId={}, chunks={}", documentId, totalChunks);
            
            // Step 2: Store hierarchical structure information in PostgreSQL
            log.debug("💾 [INDEXING] Step 2/2: Saving hierarchy to PostgreSQL: chunks={}", totalChunks);
            int savedChunks = saveChunkHierarchyToPostgreSQL(documentId, embeddedChunks);
            log.info("✅ [INDEXING] PostgreSQL hierarchy saved: documentId={}, savedChunks={}", documentId, savedChunks);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("🎉 [INDEXING] Chunk indexing completed successfully: documentId={}, chunks={}, duration={}ms", 
                    documentId, totalChunks, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("❌ [INDEXING] Chunk indexing failed: documentId={}, chunks={}, duration={}ms, error={}", 
                    documentId, totalChunks, duration, e.getMessage(), e);
            throw new BusinessException(ErrorCode.INDEXING_FAILED, 
                    "Failed to index chunks: " + e.getMessage());
        }
    }

    /**
     * Bulk index chunks to Elasticsearch.
     */
    private void bulkIndexToElasticsearch(List<StructuredChunk> chunks) {
        long esStartTime = System.currentTimeMillis();
        int totalChunks = chunks.size();
        
        log.debug("🔍 [ELASTICSEARCH] Starting bulk indexing: chunks={}, index={}", totalChunks, indexName);

        try {
            // Create bulk request body
            log.debug("📦 [ELASTICSEARCH] Building bulk request body: chunks={}", totalChunks);
            StringBuilder bulkBody = new StringBuilder();
            
            for (StructuredChunk chunk : chunks) {
                // Index metadata
                Map<String, Object> indexMeta = Map.of(
                        "index", Map.of("_id", chunk.getChunkId())
                );
                try {
                    bulkBody.append(objectMapper.writeValueAsString(indexMeta)).append("\n");
                    
                    // Document data
                    Map<String, Object> doc = createElasticsearchDocumentPRD(chunk);
                    bulkBody.append(objectMapper.writeValueAsString(doc)).append("\n");
                } catch (Exception jsonException) {
                    log.error("JSON serialization failed, skipping chunk: chunkId={}, error={}", 
                            chunk.getChunkId(), jsonException.getMessage());
                    continue; // Skip this chunk and continue processing
                }
            }

            // Set HTTP headers (specify UTF-8)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("application", "x-ndjson", StandardCharsets.UTF_8));

            // Send body as UTF-8 bytes to prevent Korean characters from being replaced with '?'
            byte[] requestBytes = bulkBody.toString().getBytes(StandardCharsets.UTF_8);
            HttpEntity<byte[]> requestEntity = new HttpEntity<>(requestBytes, headers);

            // Call bulk API
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    elasticsearchUrl + "/" + indexName + "/_bulk",
                    HttpMethod.POST,
                    requestEntity,
                    (Class<Map<String, Object>>) (Class<?>) Map.class
            );

            if (response.getStatusCode() != HttpStatus.OK) {
                throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, 
                        "Elasticsearch bulk indexing failed");
            }

            // Check for errors in bulk response
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && Boolean.TRUE.equals(responseBody.get("errors"))) {
                // Extract detailed information from the first error
                List<Map<String, Object>> items = (List<Map<String, Object>>) responseBody.get("items");
                if (items != null && !items.isEmpty()) {
                    Map<String, Object> firstItem = items.get(0);
                    Map<String, Object> indexResult = (Map<String, Object>) firstItem.get("index");
                    if (indexResult != null && indexResult.containsKey("error")) {
                        Map<String, Object> error = (Map<String, Object>) indexResult.get("error");
                        String reason = (String) error.get("reason");
                        log.error("Elasticsearch bulk indexing error: {}", reason);
                        throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, 
                                "Elasticsearch bulk indexing failed: " + reason);
                    }
                }
                throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, 
                        "Elasticsearch bulk indexing failed with errors");
            }

            long totalDuration = System.currentTimeMillis() - esStartTime;
            log.info("✅ [ELASTICSEARCH] Bulk indexing completed successfully: chunks={}, duration={}ms", 
                    totalChunks, totalDuration);

        } catch (Exception e) {
            long totalDuration = System.currentTimeMillis() - esStartTime;
            log.error("❌ [ELASTICSEARCH] Bulk indexing failed: chunks={}, duration={}ms, error={}", 
                    totalChunks, totalDuration, e.getMessage(), e);
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, 
                    "Failed to index to Elasticsearch: " + e.getMessage());
        }
    }

    /**
     * Save chunk hierarchy information to PostgreSQL.
     */
    private int saveChunkHierarchyToPostgreSQL(UUID documentId, List<StructuredChunk> chunks) {
        long pgStartTime = System.currentTimeMillis();
        int totalChunks = chunks.size();
        
        log.debug("💾 [POSTGRESQL] Starting to save chunk hierarchy: documentId={}, chunks={}", 
                documentId, totalChunks);

        try {
            // ✅ Get SourceDocument entity to set up JPA relationship
            SourceDocument sourceDocument = sourceDocumentRepository.findById(documentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.DATABASE_ERROR, 
                            "SourceDocument not found: " + documentId));

            List<DocumentChunk> documentChunks = new ArrayList<>();

            for (StructuredChunk chunk : chunks) {
                UUID parentChunkUuid = null;
                if (chunk.getParentChunkId() != null && !chunk.getParentChunkId().trim().isEmpty()) {
                    try {
                        parentChunkUuid = UUID.fromString(chunk.getParentChunkId());
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid parent_chunk_id format: {}, skipping parent relationship for chunk: {}", 
                                chunk.getParentChunkId(), chunk.getChunkId());
                        parentChunkUuid = null;
                    }
                }

                DocumentChunk documentChunk = DocumentChunk.builder()
                        .chunkId(chunk.getChunkId())
                        .sourceDocument(sourceDocument)
                        .sourceDocumentId(documentId)
                        .title(chunk.getTitle())
                        .hierarchyLevel(chunk.getHierarchyLevel())
                        .parentChunkId(parentChunkUuid)
                        .elementType(chunk.getElementType())
                        .sequenceInDocument(0) // Set default value, should be set according to actual order
                        .build();

                documentChunks.add(documentChunk);
            }

            // Batch save
            long saveStartTime = System.currentTimeMillis();
            List<DocumentChunk> savedChunks = documentChunkRepository.saveAll(documentChunks);
            long saveDuration = System.currentTimeMillis() - saveStartTime;
            
            long totalDuration = System.currentTimeMillis() - pgStartTime;
            log.info("✅ [POSTGRESQL] Chunk hierarchy saved successfully: documentId={}, chunks={}, duration={}ms, saveTime={}ms", 
                    documentId, savedChunks.size(), totalDuration, saveDuration);
            
            return savedChunks.size();

        } catch (Exception e) {
            long totalDuration = System.currentTimeMillis() - pgStartTime;
            log.error("❌ [POSTGRESQL] Failed to save chunk hierarchy: documentId={}, chunks={}, duration={}ms, error={}", 
                    documentId, totalChunks, totalDuration, e.getMessage(), e);
            throw new BusinessException(ErrorCode.DATABASE_ERROR, 
                    "Failed to save chunk hierarchy: " + e.getMessage());
        }
    }

    /**
     * Creates Elasticsearch document
     */
    private Map<String, Object> createElasticsearchDocumentPRD(StructuredChunk chunk) {
        Map<String, Object> doc = new HashMap<>();
        
        // Root fields (camelCase)
        doc.put("chunkId", chunk.getChunkId());
        doc.put("sourceDocumentId", chunk.getDocumentId());
        doc.put("content", sanitizeContent(chunk.getContent()));
        doc.put("embedding", chunk.getEmbedding());
        doc.put("indexedAt", java.time.Instant.now().toString()); // ISO string
        
        // metadata structure
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", chunk.getTitle());
        metadata.put("hierarchyLevel", chunk.getHierarchyLevel());
        metadata.put("sequenceInDocument", 0); // default value
        metadata.put("language", "ko"); // Korean default value

        // Reflect actual file type by querying from SourceDocument 
        String resolvedFileType = "UNKNOWN";
        try {
            UUID srcId = UUID.fromString(chunk.getDocumentId());
            resolvedFileType = sourceDocumentRepository.findById(srcId)
                    .map(SourceDocument::getFileType)
                    .orElse("UNKNOWN");
        } catch (Exception e) {
            log.warn("Failed to resolve fileType for documentId={}, defaulting to UNKNOWN", chunk.getDocumentId());
        }
        metadata.put("fileType", resolvedFileType);
        
        // Handle breadcrumbs (default: empty array)
        metadata.put("breadcrumbs", Arrays.asList()); // empty array default value
        
        doc.put("metadata", metadata);
        
        return doc;
    }

    /**
     * Cleans up Java code or special characters in content field to prevent JSON parsing errors.
     */
    private String sanitizeContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        
        return content
            // Java code related cleanup
            .replaceAll("\\{[^}]*\\}", "")           // Remove curly brace content
            .replaceAll("\\[.*?\\]", "")             // Remove square bracket content
            .replaceAll("\\b(int|String|void|public|private|static|final|class|interface|extends|implements)\\b", "")  // Remove Java keywords
            .replaceAll("\\b(if|else|for|while|switch|case|break|continue|return|throw|try|catch|finally)\\b", "")  // Remove Java control statements
            .replaceAll("\\b(new|this|super|null|true|false)\\b", "")  // Remove Java literals
            
            // Special character cleanup
            .replaceAll("\\s+", " ")                 // Replace consecutive spaces with single space
            .replaceAll("[\\r\\n\\t]+", " ")         // Replace newlines and tabs with space
            .replaceAll("\\s*[;=+\\-*/%<>!&|^~]\\s*", " ")  // Clean up spaces around operators
            
            // Other cleanup
            .replaceAll("\\s+", " ")                 // Clean up consecutive spaces again
            .trim();
    }

}
