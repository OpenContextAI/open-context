package com.opencontext.service;

import com.opencontext.dto.GetContentResponse;
import com.opencontext.dto.TokenInfo;
import com.opencontext.enums.ErrorCode;
import com.opencontext.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Service for retrieving chunk content and applying token limits
 * Applies token limits based on tiktoken-cl100k_base tokenizer according to PRD specifications
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentRetrievalService {

    private final RestTemplate restTemplate;

    @Value("${app.elasticsearch.url:http://localhost:9200}")
    private String elasticsearchUrl;

    @Value("${app.elasticsearch.index:document_chunks_index}")
    private String indexName;

    @Value("${app.content.default-max-tokens:25000}")
    private int defaultMaxTokens;

    @Value("${app.content.tokenizer:tiktoken-cl100k_base}")
    private String tokenizerName;

    /**
     * Retrieves the complete content of a single chunk and applies token limits
     * 
     * @param chunkId Chunk ID to retrieve
     * @param maxTokens Maximum token count (uses default if null)
     * @return Content with token limits applied and token information
     */
    public GetContentResponse getContent(String chunkId, Integer maxTokens) {
        long startTime = System.currentTimeMillis();
        
        int effectiveMaxTokens = maxTokens != null ? maxTokens : defaultMaxTokens;
        
        log.info("Starting chunk content retrieval: chunkId={}, maxTokens={}", chunkId, effectiveMaxTokens);

        try {
            // Step 1: Retrieve chunk content from Elasticsearch
            String content = fetchChunkContent(chunkId);
            
            // Step 2: Apply token limits
            GetContentResponse response = applyTokenLimit(content, effectiveMaxTokens);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Chunk content retrieval completed: chunkId={}, originalLength={}, tokenCount={}, duration={}ms",
                    chunkId, content.length(), response.getTokenInfo().getActualTokens(), duration);
            
            return response;

        } catch (BusinessException e) {
            throw e; // Propagate business exceptions as-is
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Chunk content retrieval failed: chunkId={}, duration={}ms, error={}", 
                    chunkId, duration, e.getMessage(), e);
            throw new BusinessException(ErrorCode.ELASTICSEARCH_ERROR, 
                    "Content retrieval failed: " + e.getMessage());
        }
    }

    /**
     * Retrieves content of a specific chunk from Elasticsearch
     */
    private String fetchChunkContent(String chunkId) {
        log.debug("Retrieving chunk from Elasticsearch: chunkId={}", chunkId);
        
        try {
            String getUrl = elasticsearchUrl + "/" + indexName + "/_doc/" + chunkId;
            
            // Use _source filter to retrieve only content field (performance optimization)
            String getUrlWithSource = getUrl + "?_source=content";
            
            ResponseEntity<Map> response = restTemplate.getForEntity(getUrlWithSource, Map.class);
            
            if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new BusinessException(ErrorCode.CHUNK_NOT_FOUND, 
                        "Chunk not found: " + chunkId);
            }
            
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new BusinessException(ErrorCode.ELASTICSEARCH_ERROR, 
                        "Failed to fetch chunk with status: " + response.getStatusCode());
            }
            
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null || !Boolean.TRUE.equals(responseBody.get("found"))) {
                throw new BusinessException(ErrorCode.CHUNK_NOT_FOUND, 
                        "Chunk not found: " + chunkId);
            }
            
            // Extract content from _source
            Map<String, Object> source = (Map<String, Object>) responseBody.get("_source");
            if (source == null) {
                throw new BusinessException(ErrorCode.ELASTICSEARCH_ERROR, 
                        "Content source is null for chunk: " + chunkId);
            }
            
            String content = (String) source.get("content");
            if (content == null) {
                throw new BusinessException(ErrorCode.ELASTICSEARCH_ERROR, 
                        "Content field is null for chunk: " + chunkId);
            }
            
            log.debug("Chunk content retrieval successful: chunkId={}, length={}", chunkId, content.length());
            return content;
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Elasticsearch chunk retrieval failed: chunkId={}, error={}", chunkId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.ELASTICSEARCH_ERROR, 
                    "Failed to fetch chunk from Elasticsearch: " + e.getMessage());
        }
    }

    /**
     * Applies token limits to content and creates response DTO
     * PRD policy: Truncate text from the end when maxTokens is exceeded (preserve beginning priority)
     */
    private GetContentResponse applyTokenLimit(String content, int maxTokens) {
        log.debug("Applying token limit: originalLength={}, maxTokens={}", content.length(), maxTokens);
        
        try {
            // Calculate current token count of content
            int currentTokens = calculateTokenCount(content);
            
            String finalContent = content;
            int actualTokens = currentTokens;
            
            // Truncate text from the end if token count exceeds limit
            if (currentTokens > maxTokens) {
                log.debug("Token limit exceeded, truncating text: currentTokens={}, limitTokens={}", currentTokens, maxTokens);
                
                finalContent = truncateContentByTokens(content, maxTokens);
                actualTokens = calculateTokenCount(finalContent);
                
                log.debug("Text truncation completed: finalLength={}, finalTokens={}", finalContent.length(), actualTokens);
            }
            
            // Create token information
            TokenInfo tokenInfo = TokenInfo.builder()
                    .tokenizer(tokenizerName)
                    .actualTokens(actualTokens)
                    .build();
            
            // Create response DTO
            return GetContentResponse.builder()
                    .content(finalContent)
                    .tokenInfo(tokenInfo)
                    .build();
            
        } catch (Exception e) {
            log.error("Token limit application failed: error={}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.ELASTICSEARCH_ERROR, 
                    "Token limit processing failed: " + e.getMessage());
        }
    }

    /**
     * Calculate token count based on tiktoken-cl100k_base tokenizer
     * Simple approximation (actual tiktoken library needed for accurate implementation)
     */
    private int calculateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        // Simple token count approximation
        // Actually need tiktoken Java binding or external API
        // Currently approximates: English average 4 chars = 1 token, Korean 1.5 chars = 1 token
        
        int englishChars = 0;
        int koreanChars = 0;
        int otherChars = 0;
        
        for (char c : text.toCharArray()) {
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == ' ') {
                englishChars++;
            } else if (c >= 0xAC00 && c <= 0xD7AF) { // Korean range
                koreanChars++;
            } else {
                otherChars++;
            }
        }
        
        int estimatedTokens = (int) Math.ceil(englishChars / 4.0) + 
                             (int) Math.ceil(koreanChars / 1.5) + 
                             (int) Math.ceil(otherChars / 2.0);
        
        return Math.max(estimatedTokens, 1); // Minimum 1 token
    }

    /**
     * Truncate text based on token count (preserve beginning priority)
     * PRD policy: Remove from text end to preserve important content at beginning
     */
    private String truncateContentByTokens(String content, int maxTokens) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        // Use binary search to find the appropriate truncation point
        int left = 0;
        int right = content.length();
        String result = content;
        
        while (left < right) {
            int mid = (left + right + 1) / 2;
            String candidate = content.substring(0, mid);
            int candidateTokens = calculateTokenCount(candidate);
            
            if (candidateTokens <= maxTokens) {
                result = candidate;
                left = mid;
            } else {
                right = mid - 1;
            }
        }
        
        return result;
    }
}