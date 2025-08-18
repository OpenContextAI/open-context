package com.opencontext.controller;

import com.opencontext.common.CommonResponse;
import com.opencontext.dto.GetContentResponse;
import com.opencontext.dto.GetContentRequest;
import com.opencontext.dto.SearchResultItem;
import com.opencontext.dto.SearchResultsResponse;
import com.opencontext.service.ContentRetrievalService;
import com.opencontext.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MCP 검색 API 컨트롤러
 * find_knowledge와 get_content MCP 도구를 위한 엔드포인트 제공
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SearchController implements DocsSearchController {

    private final SearchService searchService;
    private final ContentRetrievalService contentRetrievalService;

    /**
     * 하이브리드 검색 수행 - find_knowledge MCP 도구
     */
    @Override
    @GetMapping("/search")
    public ResponseEntity<CommonResponse<SearchResultsResponse>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "50") Integer topK) {
        
        log.info("검색 요청: query='{}', topK={}", query, topK);
        
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(CommonResponse.error("Query cannot be empty", "VALIDATION_FAILED"));
        }
        
        List<SearchResultItem> results = searchService.search(query.trim(), topK);
        SearchResultsResponse responseData = SearchResultsResponse.builder()
                .results(results)
                .build();
        return ResponseEntity.ok(CommonResponse.success(responseData, "Search completed successfully"));
    }

    /**
     * 청크 콘텐츠 조회 - get_content MCP 도구
     */
    @Override
    @PostMapping(value = "/get-content", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CommonResponse<GetContentResponse>> getContent(@jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody GetContentRequest request) {
        if (request == null || request.getChunkId() == null || request.getChunkId().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(CommonResponse.error("chunkId is required", "VALIDATION_FAILED"));
        }
        String chunkId = request.getChunkId();
        Integer maxTokens = request.getMaxTokens();
        if (maxTokens != null && maxTokens <= 0) {
            return ResponseEntity.badRequest()
                    .body(CommonResponse.error("maxTokens must be positive", "VALIDATION_FAILED"));
        }
        log.info("콘텐츠 조회 요청: chunkId={}, maxTokens={}", chunkId, maxTokens);
        GetContentResponse response = contentRetrievalService.getContent(chunkId, maxTokens);
        return ResponseEntity.ok(CommonResponse.success(response, "Content retrieved successfully"));
    }
}