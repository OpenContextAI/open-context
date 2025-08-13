package com.opencontext.service;

import com.opencontext.dto.StructuredChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 파싱된 문서 요소를 의미 있는 청크로 분할하는 서비스.
 * 
 * Unstructured API의 결과를 기반으로 계층적 구조를 유지하면서
 * 검색에 최적화된 청크를 생성합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkingService {

    private static final int MAX_CHUNK_SIZE = 1000; // 최대 청크 크기 (문자 수)
    private static final int CHUNK_OVERLAP = 200;   // 청크 간 중복 크기

    /**
     * 파싱된 요소들을 구조화된 청크로 변환합니다.
     * 
     * @param documentId 문서 ID
     * @param parsedElements Unstructured API에서 파싱된 요소 목록
     * @return 구조화된 청크 목록
     */
    public List<StructuredChunk> createChunks(UUID documentId, List<Map<String, Object>> parsedElements) {
        long startTime = System.currentTimeMillis();
        int totalElements = parsedElements.size();
        
        log.info("🧩 [CHUNKING] Starting chunking process: documentId={}, elements={}", documentId, totalElements);

        List<StructuredChunk> chunks = new ArrayList<>();
        Stack<ChunkContext> hierarchyStack = new Stack<>();
        int chunkIndex = 0;
        int processedElements = 0;
        
        log.debug("📄 [CHUNKING] Processing {} elements for hierarchical chunking", totalElements);

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

            // 요소 타입에 따른 처리
            switch (elementType) {
                case "Title" -> {
                    // 새로운 섹션 시작 - 계층 구조 업데이트
                    ChunkContext titleContext = new ChunkContext(text, 1, null);
                    hierarchyStack.clear();
                    hierarchyStack.push(titleContext);
                    
                    // 제목을 별도 청크로 생성
                    chunks.add(createChunk(documentId, chunkIndex++, text, titleContext, element));
                }
                case "Header" -> {
                    // 헤더 레벨 결정 (metadata에서 추출 또는 기본값 사용)
                    int level = determineHeaderLevel(element);
                    
                    // 계층 구조 조정
                    adjustHierarchyStack(hierarchyStack, level);
                    
                    ChunkContext headerContext = new ChunkContext(text, level, 
                            hierarchyStack.isEmpty() ? null : hierarchyStack.peek());
                    hierarchyStack.push(headerContext);
                    
                    // 헤더를 별도 청크로 생성
                    chunks.add(createChunk(documentId, chunkIndex++, text, headerContext, element));
                }
                case "NarrativeText", "ListItem", "Table" -> {
                    // 현재 계층 구조에서 내용 청크 생성
                    ChunkContext currentContext = hierarchyStack.isEmpty() ? null : hierarchyStack.peek();
                    
                    // 긴 텍스트는 여러 청크로 분할
                    List<String> textChunks = splitLongText(text);
                    for (String textChunk : textChunks) {
                        chunks.add(createChunk(documentId, chunkIndex++, textChunk, currentContext, element));
                    }
                }
                default -> {
                    // 기타 요소들은 현재 컨텍스트에서 처리
                    ChunkContext currentContext = hierarchyStack.isEmpty() ? null : hierarchyStack.peek();
                    chunks.add(createChunk(documentId, chunkIndex++, text, currentContext, element));
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        int finalChunkCount = chunks.size();
        
        log.info("🎉 [CHUNKING] Chunking completed successfully: documentId={}, elements={}, chunks={}, duration={}ms", 
                documentId, totalElements, finalChunkCount, duration);
        
        // 청크 통계 로깅
        if (finalChunkCount > 0) {
            double avgChunkLength = chunks.stream()
                    .mapToInt(c -> c.getContent().length())
                    .average()
                    .orElse(0.0);
            
            int maxHierarchyLevel = chunks.stream()
                    .mapToInt(c -> c.getHierarchyLevel() != null ? c.getHierarchyLevel() : 0)
                    .max()
                    .orElse(0);
            
            log.debug("📊 [CHUNKING] Statistics: avgChunkLength={}, maxHierarchyLevel={}, maxChunkSize={}", 
                    Math.round(avgChunkLength), maxHierarchyLevel, MAX_CHUNK_SIZE);
        }
        
        return chunks;
    }

    /**
     * StructuredChunk 객체를 생성합니다.
     */
    private StructuredChunk createChunk(UUID documentId, int chunkIndex, String text, 
                                      ChunkContext context, Map<String, Object> element) {
        return StructuredChunk.builder()
                .documentId(documentId.toString())
                .chunkId(generateChunkId(documentId, chunkIndex))
                .content(text)
                .title(context != null ? context.title : null)
                .hierarchyLevel(context != null ? context.level : 0)
                .parentChunkId(context != null && context.parent != null ? 
                        generateChunkId(documentId, context.parent.chunkIndex) : null)
                .elementType((String) element.get("type"))
                .metadata(extractMetadata(element))
                .build();
    }

    /**
     * 청크 ID를 생성합니다.
     */
    private String generateChunkId(UUID documentId, int chunkIndex) {
        return documentId.toString() + "-chunk-" + chunkIndex;
    }

    /**
     * 헤더의 레벨을 결정합니다.
     */
    private int determineHeaderLevel(Map<String, Object> element) {
        // metadata에서 레벨 정보 추출 시도
                    @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) element.get("metadata");
        if (metadata != null && metadata.containsKey("category_depth")) {
            try {
                return ((Number) metadata.get("category_depth")).intValue();
            } catch (Exception e) {
                log.debug("Failed to extract header level from metadata", e);
            }
        }
        
        // 기본값 반환
        return 2;
    }

    /**
     * 계층 구조 스택을 조정합니다.
     */
    private void adjustHierarchyStack(Stack<ChunkContext> stack, int newLevel) {
        // 새로운 레벨보다 깊거나 같은 레벨의 요소들을 제거
        while (!stack.isEmpty() && stack.peek().level >= newLevel) {
            stack.pop();
        }
    }

    /**
     * 긴 텍스트를 여러 청크로 분할합니다.
     */
    private List<String> splitLongText(String text) {
        if (text.length() <= MAX_CHUNK_SIZE) {
            return List.of(text);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + MAX_CHUNK_SIZE, text.length());
            
            // 단어 경계에서 자르기
            if (end < text.length()) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > start) {
                    end = lastSpace;
                }
            }

            chunks.add(text.substring(start, end).trim());
            start = Math.max(start + 1, end - CHUNK_OVERLAP);
        }

        return chunks;
    }



    /**
     * 요소에서 메타데이터를 추출합니다.
     */
    private Map<String, Object> extractMetadata(Map<String, Object> element) {
        Map<String, Object> metadata = new HashMap<>();
        
        // 기본 메타데이터 복사
        if (element.containsKey("metadata")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> originalMetadata = (Map<String, Object>) element.get("metadata");
            metadata.putAll(originalMetadata);
        }

        // 좌표 정보 추가
        if (element.containsKey("coordinates")) {
            metadata.put("coordinates", element.get("coordinates"));
        }

        return metadata;
    }

    /**
     * 청킹 컨텍스트를 관리하는 내부 클래스
     */
    private static class ChunkContext {
        final String title;
        final int level;
        final ChunkContext parent;
        final int chunkIndex;
        private static int globalChunkIndex = 0;

        ChunkContext(String title, int level, ChunkContext parent) {
            this.title = title;
            this.level = level;
            this.parent = parent;
            this.chunkIndex = globalChunkIndex++;
        }
    }
}
