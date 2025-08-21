package com.opencontext.service;

import com.opencontext.dto.SearchResultItem;
import com.opencontext.enums.ErrorCode;
import com.opencontext.exception.BusinessException;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Elasticsearch hybrid search service
 * Combines BM25 keyword search and vector similarity search to provide optimal search results
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final EmbeddingModel embeddingModel;
    private final RestTemplate restTemplate;

    @Value("${app.elasticsearch.url:http://localhost:9200}")
    private String elasticsearchUrl;

    @Value("${app.elasticsearch.index:document_chunks_index}")
    private String indexName;

    @Value("${app.search.snippet-max-length:50}")
    private int snippetMaxLength;

    @Value("${app.search.bm25-weight:0.3}")
    private double bm25Weight;

    @Value("${app.search.vector-weight:0.7}")
    private double vectorWeight;

    /**
     * Execute hybrid search - combines keyword search and semantic search
     * 
     * @param query Search query
     * @param topK Maximum number of results to return
     * @return List of search results sorted by relevance
     */
    public List<SearchResultItem> search(String query, int topK) {
        long startTime = System.currentTimeMillis();
        
        log.info("Starting hybrid search: query='{}', topK={}", query, topK);

        try {
            // Step 1: Convert search query to embedding vector (float type for ES compatibility)
            List<Float> queryEmbedding = generateQueryEmbedding(query);
            
            // Step 2: Execute Elasticsearch hybrid query
            Map<String, Object> searchResponse = executeElasticsearchQuery(query, queryEmbedding, topK);
            
            // Step 3: Convert search results to DTO
            List<SearchResultItem> results = parseSearchResults(searchResponse); 
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Hybrid search completed: query='{}', resultCount={}, duration={}ms", 
                    query, results.size(), duration);
            
            return results;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Hybrid search failed: query='{}', duration={}ms, error={}", 
                    query, duration, e.getMessage(), e);
            throw new BusinessException(ErrorCode.ELASTICSEARCH_ERROR, 
                    "Search operation failed: " + e.getMessage());
        }
    }

    /**
     * Convert search query to embedding vector
     * Uses List<Float> type for ES cosineSimilarity function compatibility
     */
    private List<Float> generateQueryEmbedding(String query) {
        log.debug("Generating query embedding: query='{}'", query);
        
        try {
            TextSegment textSegment = TextSegment.from(query);
            Embedding embedding = embeddingModel.embed(textSegment).content();
            
            // Convert float array to List<Float> (ES compatibility)
            List<Float> embeddingVector = new ArrayList<>();
            float[] vector = embedding.vector();
            for (float value : vector) {
                embeddingVector.add(value);
            }
            
            log.debug("Query embedding generation completed: dimensions={}", embedding.dimension());
            return embeddingVector;
            
        } catch (Exception e) {
            log.error("Query embedding generation failed: query='{}', error={}", query, e.getMessage(), e);
            throw new BusinessException(ErrorCode.EMBEDDING_GENERATION_FAILED, 
                    "Failed to generate query embedding: " + e.getMessage());
        }
    }

    /**
     * Execute hybrid search query on Elasticsearch
     */
    private Map<String, Object> executeElasticsearchQuery(String query, List<Float> queryEmbedding, int topK) {
        log.debug("Executing Elasticsearch query: topK={}", topK);
        
        try {
            Map<String, Object> searchQuery = buildHybridSearchQuery(query, queryEmbedding, topK);
            String searchUrl = elasticsearchUrl + "/" + indexName + "/_search";
            
            ResponseEntity<Map<String, Object>> response = restTemplate.postForEntity(
                    searchUrl, searchQuery, (Class<Map<String, Object>>) (Class<?>) Map.class);
            
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new BusinessException(ErrorCode.ELASTICSEARCH_ERROR, 
                        "Elasticsearch search failed with status: " + response.getStatusCode());
            }
            
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new BusinessException(ErrorCode.ELASTICSEARCH_ERROR, 
                        "Empty response from Elasticsearch");
            }
            
            log.debug("Search query execution successful");
            return responseBody;
            
        } catch (Exception e) {
            log.error("Search query execution failed: error={}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.ELASTICSEARCH_ERROR, 
                    "Elasticsearch query execution failed: " + e.getMessage());
        }
    }

    /**
     * Build hybrid query combining BM25 keyword search and vector similarity search
     * Place queries side by side to properly reflect scores
     */
    private Map<String, Object> buildHybridSearchQuery(String query, List<Float> queryEmbedding, int topK) {
        
        // BM25 keyword search query 
        Map<String, Object> bm25Query = Map.of(
            "multi_match", Map.of(
                "query", query,
                "fields", Arrays.asList("content^2", "metadata.title^1.5"),
                "type", "best_fields",
                "fuzziness", "AUTO",
                "boost", bm25Weight
            )
        );
        
        // Vector similarity search query (apply weight inside script)
        Map<String, Object> vectorQuery = Map.of(
            "script_score", Map.of(
                "query", Map.of("match_all", Map.of()),
                "script", Map.of(
                    "source", "(cosineSimilarity(params.query_vector, 'embedding') + 1.0) * params.vector_weight",
                    "params", Map.of(
                        "query_vector", queryEmbedding,
                        "vector_weight", vectorWeight
                    )
                )
            )
        );
        
        // Hybrid query (place two queries side by side in bool.should)
        Map<String, Object> hybridQuery = Map.of(
            "bool", Map.of(
                "should", Arrays.asList(bm25Query, vectorQuery)
            )
        );
        
        // Final search query 
        return Map.of(
            "size", topK,
            "query", hybridQuery,
            "_source", Arrays.asList(
                "chunkId", "metadata.title", "content", "metadata.hierarchyLevel", 
                "sourceDocumentId", "metadata.fileType", "metadata"
            ),
            "sort", Arrays.asList(
                Map.of("_score", Map.of("order", "desc"))
            )
        );
    }

    /**
     * Convert Elasticsearch response to SearchResultItem list
     * Apply relative normalization against maximum score in response
     */
    private List<SearchResultItem> parseSearchResults(Map<String, Object> response) {
        log.debug("Parsing search results");
        
        try {
            Map<String, Object> hits = (Map<String, Object>) response.get("hits");
            if (hits == null) {
                log.warn("Elasticsearch response missing 'hits' field");
                return Collections.emptyList();
            }
            
            List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");
            if (hitList == null || hitList.isEmpty()) {
                log.info("No search results found");
                return Collections.emptyList();
            }
            
            // Calculate maximum score in response (for relative normalization)
            double maxScore = hitList.stream()
                    .mapToDouble(hit -> ((Number) hit.get("_score")).doubleValue())
                    .max()
                    .orElse(1.0);
            
            List<SearchResultItem> results = new ArrayList<>();
            
            for (Map<String, Object> hit : hitList) {
                try {
                    SearchResultItem item = parseSearchHit(hit, maxScore);
                    if (item != null) {
                        results.add(item);
                    }
                } catch (Exception e) {
                    log.warn("Search result parsing failed, skipping: {}", e.getMessage());
                    // Individual hit parsing failure does not stop the entire search
                }
            }
            
            log.debug("Search result parsing completed: resultCount={}", results.size());
            return results;
            
        } catch (Exception e) {
            log.error("Search result parsing failed: error={}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.ELASTICSEARCH_ERROR, 
                    "Failed to parse search results: " + e.getMessage());
        }
    }

    /**
     * Convert individual search result to SearchResultItem
     */
    private SearchResultItem parseSearchHit(Map<String, Object> hit, double maxScore) {
        Map<String, Object> source = (Map<String, Object>) hit.get("_source");
        if (source == null) {
            return null;
        }
        
        // Extract fields according to PRD schema
        String chunkId = (String) source.get("chunkId");
        String content = (String) source.get("content");
        Double score = ((Number) hit.get("_score")).doubleValue();
        
        // PRD schema: title is located in metadata.title
        String title = extractTitle(source);
        
        // Generate snippet according to PRD policy
        String snippet = generateSnippet(content);
        
        // Relative normalization against maximum score in response
        double relevanceScore = normalizeScore(score, maxScore);
        
        return SearchResultItem.builder()
                .chunkId(chunkId)
                .title(title != null ? title : "No Title")
                .snippet(snippet)
                .relevanceScore(relevanceScore)
                .build();
    }

    /**
     * Extract title from schema (metadata.title)
     */
    private String extractTitle(Map<String, Object> source) {
        Map<String, Object> metadata = (Map<String, Object>) source.get("metadata");
        if (metadata != null) {
            return (String) metadata.get("title");
        }
        return null;
    }

    /**
     * Generate snippet
     * - Default length: 50 characters
     * - If over 50 characters: first 50 characters + "..." (always added)
     * - If under 50 characters: original as-is (no ...)
     */
    private String generateSnippet(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "No content available";
        }
        
        String cleanContent = content.trim();
        
        if (cleanContent.length() <= snippetMaxLength) {
            return cleanContent;
        }
        
        return cleanContent.substring(0, snippetMaxLength) + "...";
    }

    /**
     * Score normalization - calculate relative ratio against maximum score in response
     */
    private double normalizeScore(double score, double maxScore) {
        if (maxScore <= 0) {
            return 0.0;
        }
        return score / maxScore;
    }
}