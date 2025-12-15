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
 * 임베딩된 청크를 Elasticsearch와 PostgreSQL에 저장하는 서비스.
 * 
 * Elasticsearch에는 검색을 위한 벡터와 메타데이터를,
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
                try {
                    bulkBody.append(objectMapper.writeValueAsString(indexMeta)).append("\n");
                    
                    // 문서 데이터
                    Map<String, Object> doc = createElasticsearchDocumentPRD(chunk);
                    bulkBody.append(objectMapper.writeValueAsString(doc)).append("\n");
                } catch (Exception jsonException) {
                    log.error("JSON 직렬화 실패, 청크 건너뛰기: chunkId={}, error={}", 
                            chunk.getChunkId(), jsonException.getMessage());
                    continue; // 해당 청크는 건너뛰고 계속 진행
                }
            }

            // HTTP 헤더 설정 (UTF-8 명시)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("application", "x-ndjson", StandardCharsets.UTF_8));

            // 본문을 UTF-8 바이트로 전송하여 한글이 '?'로 치환되는 문제 방지
            byte[] requestBytes = bulkBody.toString().getBytes(StandardCharsets.UTF_8);
            HttpEntity<byte[]> requestEntity = new HttpEntity<>(requestBytes, headers);

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

            // 벌크 응답에서 오류 확인 
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && Boolean.TRUE.equals(responseBody.get("errors"))) {
                // 첫 번째 에러의 상세 정보 추출
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
     * Elasticsearch 문서 생성
     */
    private Map<String, Object> createElasticsearchDocumentPRD(StructuredChunk chunk) {
        Map<String, Object> doc = new HashMap<>();
        
        // 루트 필드 (camelCase)
        doc.put("chunkId", chunk.getChunkId());
        doc.put("sourceDocumentId", chunk.getDocumentId());
        doc.put("content", sanitizeContent(chunk.getContent()));
        doc.put("embedding", chunk.getEmbedding());
        doc.put("indexedAt", java.time.Instant.now().toString()); // ISO 문자열
        
        // metadata 구조
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", chunk.getTitle());
        metadata.put("hierarchyLevel", chunk.getHierarchyLevel());
        metadata.put("sequenceInDocument", 0); // 기본값
        metadata.put("language", "ko"); // 한국어 기본값

        // 실제 파일 타입과 원본 파일명을 SourceDocument에서 조회하여 반영
        String resolvedFileType = "UNKNOWN";
        String originalFilename = "";
        try {
            UUID srcId = UUID.fromString(chunk.getDocumentId());
            SourceDocument sourceDoc = sourceDocumentRepository.findById(srcId).orElse(null);
            if (sourceDoc != null) {
                resolvedFileType = sourceDoc.getFileType();
                originalFilename = sourceDoc.getOriginalFilename();
            }
        } catch (Exception e) {
            log.warn("Failed to resolve fileType and originalFilename for documentId={}, using defaults", chunk.getDocumentId());
        }
        metadata.put("fileType", resolvedFileType);
        metadata.put("originalFilename", originalFilename);
        
        // breadcrumbs 처리 (기본값: 빈 배열)
        metadata.put("breadcrumbs", Arrays.asList()); // 빈 배열 기본값
        
        doc.put("metadata", metadata);
        
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

}
