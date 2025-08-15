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
 * 청크 콘텐츠 조회 및 토큰 제한 처리 서비스
 * PRD 명세에 따라 tiktoken-cl100k_base 토크나이저 기준으로 토큰 제한 적용
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
     * 단일 청크의 전체 콘텐츠를 조회하고 토큰 제한을 적용
     * 
     * @param chunkId 조회할 청크 ID
     * @param maxTokens 최대 토큰 수 (null인 경우 기본값 사용)
     * @return 토큰 제한이 적용된 콘텐츠와 토큰 정보
     */
    public GetContentResponse getContent(String chunkId, Integer maxTokens) {
        long startTime = System.currentTimeMillis();
        
        int effectiveMaxTokens = maxTokens != null ? maxTokens : defaultMaxTokens;
        
        log.info("청크 콘텐츠 조회 시작: chunkId={}, maxTokens={}", chunkId, effectiveMaxTokens);

        try {
            // 1단계: Elasticsearch에서 청크 내용 조회
            String content = fetchChunkContent(chunkId);
            
            // 2단계: 토큰 제한 적용
            GetContentResponse response = applyTokenLimit(content, effectiveMaxTokens);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("청크 콘텐츠 조회 완료: chunkId={}, 원본길이={}, 토큰수={}, 소요시간={}ms",
                    chunkId, content.length(), response.getTokenInfo().getActualTokens(), duration);
            
            return response;

        } catch (BusinessException e) {
            throw e; // 비즈니스 예외는 그대로 전파
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("청크 콘텐츠 조회 실패: chunkId={}, 소요시간={}ms, 오류={}", 
                    chunkId, duration, e.getMessage(), e);
            throw new BusinessException(ErrorCode.ELASTICSEARCH_ERROR, 
                    "Content retrieval failed: " + e.getMessage());
        }
    }

    /**
     * Elasticsearch에서 특정 청크의 콘텐츠 조회
     */
    private String fetchChunkContent(String chunkId) {
        log.debug("Elasticsearch에서 청크 조회: chunkId={}", chunkId);
        
        try {
            String getUrl = elasticsearchUrl + "/" + indexName + "/_doc/" + chunkId;
            
            // _source 필터를 사용하여 content 필드만 조회 (성능 최적화)
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
            
            // _source에서 content 추출
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
            
            log.debug("청크 콘텐츠 조회 성공: chunkId={}, 길이={}", chunkId, content.length());
            return content;
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Elasticsearch 청크 조회 실패: chunkId={}, 오류={}", chunkId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.ELASTICSEARCH_ERROR, 
                    "Failed to fetch chunk from Elasticsearch: " + e.getMessage());
        }
    }

    /**
     * 콘텐츠에 토큰 제한을 적용하고 응답 DTO 생성
     * PRD 정책: maxTokens 초과 시 텍스트 끝부분을 잘라냄 (앞부분 우선 보존)
     */
    private GetContentResponse applyTokenLimit(String content, int maxTokens) {
        log.debug("토큰 제한 적용: 원본길이={}, maxTokens={}", content.length(), maxTokens);
        
        try {
            // 현재 콘텐츠의 토큰 수 계산
            int currentTokens = calculateTokenCount(content);
            
            String finalContent = content;
            int actualTokens = currentTokens;
            
            // 토큰 수가 제한을 초과하는 경우 텍스트 끝부분을 잘라냄
            if (currentTokens > maxTokens) {
                log.debug("토큰 제한 초과, 텍스트 자르기: 현재토큰={}, 제한토큰={}", currentTokens, maxTokens);
                
                finalContent = truncateContentByTokens(content, maxTokens);
                actualTokens = calculateTokenCount(finalContent);
                
                log.debug("텍스트 자르기 완료: 최종길이={}, 최종토큰={}", finalContent.length(), actualTokens);
            }
            
            // 토큰 정보 생성
            TokenInfo tokenInfo = TokenInfo.builder()
                    .tokenizer(tokenizerName)
                    .actualTokens(actualTokens)
                    .build();
            
            // 응답 DTO 생성
            return GetContentResponse.builder()
                    .content(finalContent)
                    .tokenInfo(tokenInfo)
                    .build();
            
        } catch (Exception e) {
            log.error("토큰 제한 적용 실패: 오류={}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.ELASTICSEARCH_ERROR, 
                    "Token limit processing failed: " + e.getMessage());
        }
    }

    /**
     * tiktoken-cl100k_base 토크나이저 기준으로 토큰 수 계산
     * 간단한 근사치 계산 (정확한 구현을 위해서는 실제 tiktoken 라이브러리 필요)
     */
    private int calculateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        // 간단한 토큰 수 근사 계산
        // 실제로는 tiktoken Java 바인딩이나 외부 API를 사용해야 함
        // 현재는 영어 기준 평균 4글자 = 1토큰, 한글 기준 1.5글자 = 1토큰으로 근사
        
        int englishChars = 0;
        int koreanChars = 0;
        int otherChars = 0;
        
        for (char c : text.toCharArray()) {
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == ' ') {
                englishChars++;
            } else if (c >= 0xAC00 && c <= 0xD7AF) { // 한글 범위
                koreanChars++;
            } else {
                otherChars++;
            }
        }
        
        int estimatedTokens = (int) Math.ceil(englishChars / 4.0) + 
                             (int) Math.ceil(koreanChars / 1.5) + 
                             (int) Math.ceil(otherChars / 2.0);
        
        return Math.max(estimatedTokens, 1); // 최소 1토큰
    }

    /**
     * 토큰 수 기준으로 텍스트를 잘라냄 (앞부분 우선 보존)
     * PRD 정책: 텍스트 끝부분부터 제거하여 앞부분의 중요한 내용 보존
     */
    private String truncateContentByTokens(String content, int maxTokens) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        // 이진 탐색을 사용하여 적절한 자르기 지점 찾기
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