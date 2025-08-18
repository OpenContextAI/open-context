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
 * Elasticsearch 하이브리드 검색 서비스
 * BM25 키워드 검색과 벡터 유사도 검색을 결합하여 최적의 검색 결과 제공
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
     * 하이브리드 검색 실행 - 키워드 검색과 의미 검색을 결합
     * 
     * @param query 검색어
     * @param topK 반환할 최대 결과 수
     * @return 관련도 순으로 정렬된 검색 결과 목록
     */
    public List<SearchResultItem> search(String query, int topK) {
        long startTime = System.currentTimeMillis();
        
        log.info("하이브리드 검색 시작: query='{}', topK={}", query, topK);

        try {
            // 1단계: 검색어를 임베딩 벡터로 변환 (float 타입으로 ES 호환성 확보)
            List<Float> queryEmbedding = generateQueryEmbedding(query);
            
            // 2단계: Elasticsearch 하이브리드 쿼리 실행
            Map<String, Object> searchResponse = executeElasticsearchQuery(query, queryEmbedding, topK);
            
            // 3단계: 검색 결과를 DTO로 변환
            List<SearchResultItem> results = parseSearchResults(searchResponse); 
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("하이브리드 검색 완료: query='{}', 결과수={}, 소요시간={}ms", 
                    query, results.size(), duration);
            
            return results;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("하이브리드 검색 실패: query='{}', 소요시간={}ms, 오류={}", 
                    query, duration, e.getMessage(), e);
            throw new BusinessException(ErrorCode.ELASTICSEARCH_ERROR, 
                    "Search operation failed: " + e.getMessage());
        }
    }

    /**
     * 검색어를 임베딩 벡터로 변환
     * ES cosineSimilarity 함수 호환을 위해 List<Float> 타입 사용
     */
    private List<Float> generateQueryEmbedding(String query) {
        log.debug("쿼리 임베딩 생성: query='{}'", query);
        
        try {
            TextSegment textSegment = TextSegment.from(query);
            Embedding embedding = embeddingModel.embed(textSegment).content();
            
            // float 배열을 List<Float>로 변환 (ES 호환성)
            List<Float> embeddingVector = new ArrayList<>();
            float[] vector = embedding.vector();
            for (float value : vector) {
                embeddingVector.add(value);
            }
            
            log.debug("쿼리 임베딩 생성 완료: 차원수={}", embedding.dimension());
            return embeddingVector;
            
        } catch (Exception e) {
            log.error("쿼리 임베딩 생성 실패: query='{}', 오류={}", query, e.getMessage(), e);
            throw new BusinessException(ErrorCode.EMBEDDING_GENERATION_FAILED, 
                    "Failed to generate query embedding: " + e.getMessage());
        }
    }

    /**
     * Elasticsearch에 하이브리드 검색 쿼리 실행
     */
    private Map<String, Object> executeElasticsearchQuery(String query, List<Float> queryEmbedding, int topK) {
        log.debug("Elasticsearch 쿼리 실행: topK={}", topK);
        
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
            
            log.debug("검색 쿼리 실행 성공");
            return responseBody;
            
        } catch (Exception e) {
            log.error("검색 쿼리 실행 실패: 오류={}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.ELASTICSEARCH_ERROR, 
                    "Elasticsearch query execution failed: " + e.getMessage());
        }
    }

    /**
     * BM25 키워드 검색과 벡터 유사도 검색을 결합한 하이브리드 쿼리 구성
     * 각 쿼리를 나란히 배치하여 점수 정상 반영
     */
    private Map<String, Object> buildHybridSearchQuery(String query, List<Float> queryEmbedding, int topK) {
        
        // BM25 키워드 검색 쿼리 
        Map<String, Object> bm25Query = Map.of(
            "multi_match", Map.of(
                "query", query,
                "fields", Arrays.asList("content^2", "metadata.title^1.5"),
                "type", "best_fields",
                "fuzziness", "AUTO",
                "boost", bm25Weight
            )
        );
        
        // 벡터 유사도 검색 쿼리 (가중치를 스크립트 내부에서 적용)
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
        
        // 하이브리드 쿼리 (bool.should에 두 쿼리를 나란히 배치)
        Map<String, Object> hybridQuery = Map.of(
            "bool", Map.of(
                "should", Arrays.asList(bm25Query, vectorQuery)
            )
        );
        
        // 최종 검색 쿼리 
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
     * Elasticsearch 응답을 SearchResultItem 목록으로 변환
     * 응답 내 최대 점수 대비 상대적 정규화 적용
     */
    private List<SearchResultItem> parseSearchResults(Map<String, Object> response) {
        log.debug("검색 결과 파싱 중");
        
        try {
            Map<String, Object> hits = (Map<String, Object>) response.get("hits");
            if (hits == null) {
                log.warn("Elasticsearch 응답에 'hits' 필드가 없음");
                return Collections.emptyList();
            }
            
            List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");
            if (hitList == null || hitList.isEmpty()) {
                log.info("검색 결과가 없음");
                return Collections.emptyList();
            }
            
            // 응답 내 최대 점수 계산 (상대적 정규화를 위함)
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
                    log.warn("검색 결과 파싱 실패, 건너뛰기: {}", e.getMessage());
                    // 개별 hit 파싱 실패는 전체 검색을 중단하지 않음
                }
            }
            
            log.debug("검색 결과 파싱 완료: 결과수={}", results.size());
            return results;
            
        } catch (Exception e) {
            log.error("검색 결과 파싱 실패: 오류={}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.ELASTICSEARCH_ERROR, 
                    "Failed to parse search results: " + e.getMessage());
        }
    }

    /**
     * 개별 검색 결과를 SearchResultItem으로 변환
     */
    private SearchResultItem parseSearchHit(Map<String, Object> hit, double maxScore) {
        Map<String, Object> source = (Map<String, Object>) hit.get("_source");
        if (source == null) {
            return null;
        }
        
        // PRD 스키마에 따른 필드 추출
        String chunkId = (String) source.get("chunkId");
        String content = (String) source.get("content");
        Double score = ((Number) hit.get("_score")).doubleValue();
        
        // PRD 스키마: title은 metadata.title에 위치
        String title = extractTitle(source);
        
        // PRD 정책에 따른 스니펫 생성
        String snippet = generateSnippet(content);
        
        // 응답 내 최대 점수 대비 상대적 정규화
        double relevanceScore = normalizeScore(score, maxScore);
        
        return SearchResultItem.builder()
                .chunkId(chunkId)
                .title(title != null ? title : "제목 없음")
                .snippet(snippet)
                .relevanceScore(relevanceScore)
                .build();
    }

    /**
     * 스키마에서 제목 추출 (metadata.title)
     */
    private String extractTitle(Map<String, Object> source) {
        Map<String, Object> metadata = (Map<String, Object>) source.get("metadata");
        if (metadata != null) {
            return (String) metadata.get("title");
        }
        return null;
    }

    /**
     * 스니펫 생성
     * - 기본 길이: 50자
     * - 50자 초과 시: 앞 50자 + "..." (항상 추가)
     * - 50자 미만 시: 원본 그대로 (... 생략)
     */
    private String generateSnippet(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "내용이 없습니다";
        }
        
        String cleanContent = content.trim();
        
        if (cleanContent.length() <= snippetMaxLength) {
            return cleanContent;
        }
        
        return cleanContent.substring(0, snippetMaxLength) + "...";
    }

    /**
     * 점수 정규화 - 응답 내 최대 점수 대비 상대적 비율로 계산
     */
    private double normalizeScore(double score, double maxScore) {
        if (maxScore <= 0) {
            return 0.0;
        }
        return score / maxScore;
    }
}