package com.opencontext.service;

import com.opencontext.dto.StructuredChunk;
import com.opencontext.entity.DocumentChunk;
import com.opencontext.entity.SourceDocument;
import com.opencontext.enums.ErrorCode;
import com.opencontext.exception.BusinessException;
import com.opencontext.repository.DocumentChunkRepository;
import com.opencontext.repository.SourceDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 임베딩된 청크를 Elasticsearch와 PostgreSQL에 저장하는 서비스.
 * 
 * Elasticsearch에는 검색을 위한 벡터와 메타데이터를,
 * PostgreSQL에는 계층 구조 정보를 저장합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class IndexingService {

    private final DocumentChunkRepository documentChunkRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final RestTemplate restTemplate;

    @Value("${app.elasticsearch.url:http://localhost:9200}")
    private String elasticsearchUrl;

    @Value("${app.elasticsearch.index:document-chunks}")
    private String indexName;

    /**
     * 임베딩된 청크들을 Elasticsearch와 PostgreSQL에 저장합니다.
     * 
     * @param documentId 문서 ID
     * @param embeddedChunks 저장할 임베딩된 청크 목록
     */
    public void indexChunks(UUID documentId, List<StructuredChunk> embeddedChunks) {
        long startTime = System.currentTimeMillis();
        int totalChunks = embeddedChunks.size();
        
        log.info("📎 [INDEXING] Starting chunk indexing: documentId={}, chunks={}", documentId, totalChunks);

        try {
            // Step 1: Elasticsearch에 벡터 데이터 저장
            log.debug("🔍 [INDEXING] Step 1/2: Indexing to Elasticsearch: chunks={}", totalChunks);
            bulkIndexToElasticsearch(embeddedChunks);
            log.info("✅ [INDEXING] Elasticsearch indexing completed: documentId={}, chunks={}", documentId, totalChunks);
            
            // Step 2: PostgreSQL에 계층 구조 정보 저장
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
     * Elasticsearch에 청크들을 벌크 인덱싱합니다.
     */
    private void bulkIndexToElasticsearch(List<StructuredChunk> chunks) {
        long esStartTime = System.currentTimeMillis();
        int totalChunks = chunks.size();
        
        log.debug("🔍 [ELASTICSEARCH] Starting bulk indexing: chunks={}, index={}", totalChunks, indexName);

        try {
            // 벌크 요청 바디 생성
            log.debug("📦 [ELASTICSEARCH] Building bulk request body: chunks={}", totalChunks);
            StringBuilder bulkBody = new StringBuilder();
            
            for (StructuredChunk chunk : chunks) {
                // 인덱스 메타데이터
                Map<String, Object> indexMeta = Map.of(
                        "index", Map.of("_id", chunk.getChunkId())
                );
                bulkBody.append(toJsonString(indexMeta)).append("\n");
                
                // 문서 데이터
                Map<String, Object> doc = createElasticsearchDocument(chunk);
                bulkBody.append(toJsonString(doc)).append("\n");
            }

            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.valueOf("application/x-ndjson"));

            HttpEntity<String> requestEntity = new HttpEntity<>(bulkBody.toString(), headers);

            // 벌크 API 호출
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

            // 응답에서 오류 확인
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && Boolean.TRUE.equals(responseBody.get("errors"))) {
                log.warn("Some documents failed to index in Elasticsearch: {}", responseBody);
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
     * PostgreSQL에 청크 계층 구조 정보를 저장합니다.
     */
    private int saveChunkHierarchyToPostgreSQL(UUID documentId, List<StructuredChunk> chunks) {
        long pgStartTime = System.currentTimeMillis();
        int totalChunks = chunks.size();
        
        log.debug("💾 [POSTGRESQL] Starting to save chunk hierarchy: documentId={}, chunks={}", 
                documentId, totalChunks);

        try {
            // ✅ SourceDocument 엔티티를 가져와서 JPA 관계 설정
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
                        .sequenceInDocument(0) // 기본값 설정, 실제로는 순서에 따라 설정해야 함
                        .build();

                documentChunks.add(documentChunk);
            }

            // 배치 저장
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
     * Elasticsearch 문서를 생성합니다.
     */
    private Map<String, Object> createElasticsearchDocument(StructuredChunk chunk) {
        Map<String, Object> doc = new HashMap<>();
        
        doc.put("document_id", chunk.getDocumentId());
        doc.put("chunk_id", chunk.getChunkId());
        
        // ✅ content 필드 정리하여 저장 (JSON 파싱 에러 방지)
        String cleanContent = sanitizeContent(chunk.getContent());
        doc.put("content", cleanContent);
        
        doc.put("title", chunk.getTitle());
        doc.put("hierarchy_level", chunk.getHierarchyLevel());
        doc.put("parent_chunk_id", chunk.getParentChunkId());
        doc.put("element_type", chunk.getElementType());
        doc.put("embedding", chunk.getEmbedding());
        doc.put("metadata", chunk.getMetadata());
        doc.put("indexed_at", new Date());

        return doc;
    }

    /**
     * content 필드의 Java 코드나 특수문자를 정리하여 JSON 파싱 에러를 방지합니다.
     */
    private String sanitizeContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        
        return content
            // Java 코드 관련 정리
            .replaceAll("\\{[^}]*\\}", "")           // 중괄호 내용 제거
            .replaceAll("\\[.*?\\]", "")             // 대괄호 내용 제거
            .replaceAll("\\b(int|String|void|public|private|static|final|class|interface|extends|implements)\\b", "")  // Java 키워드 제거
            .replaceAll("\\b(if|else|for|while|switch|case|break|continue|return|throw|try|catch|finally)\\b", "")  // Java 제어문 제거
            .replaceAll("\\b(new|this|super|null|true|false)\\b", "")  // Java 리터럴 제거
            
            // 특수문자 정리
            .replaceAll("\\s+", " ")                 // 연속 공백을 단일 공백으로
            .replaceAll("[\\r\\n\\t]+", " ")         // 줄바꿈, 탭을 공백으로
            .replaceAll("\\s*[;=+\\-*/%<>!&|^~]\\s*", " ")  // 연산자 주변 공백 정리
            
            // 기타 정리
            .replaceAll("\\s+", " ")                 // 다시 연속 공백 정리
            .trim();
    }

    /**
     * 객체를 JSON 문자열로 변환합니다.
     * 실제 구현에서는 Jackson ObjectMapper를 사용해야 합니다.
     */
    private String toJsonString(Object obj) {
        // 간단한 구현 - 실제로는 ObjectMapper를 사용해야 함
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(entry.getKey()).append("\":");
                if (entry.getValue() instanceof String) {
                    json.append("\"").append(entry.getValue()).append("\"");
                } else if (entry.getValue() instanceof Map) {
                    json.append(toJsonString(entry.getValue()));
                } else {
                    json.append(entry.getValue());
                }
                first = false;
            }
            json.append("}");
            return json.toString();
        }
        return obj.toString();
    }
}
