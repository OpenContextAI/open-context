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

        // Title을 기준으로 청크를 나누기 위한 변수들
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

            // Title을 만나면 이전 청크를 저장하고 새로운 청크 시작
            // Unstructured API에서 title_depth=1로 설정했으므로 # 헤더만 Title로 올 것임
            boolean isUnstructuredTitle = "Title".equals(elementType);
            
            // 추가로 텍스트에서 # 헤더를 직접 확인 (백업용)
            boolean isH1Header = isH1MarkdownHeader(text);
            
            if (isUnstructuredTitle || isH1Header) {
                log.debug("🏷️ [CHUNKING] Detected H1 title/header: text='{}', elementType='{}', isUnstructuredTitle={}, isH1Header={}", 
                        text, elementType, isUnstructuredTitle, isH1Header);
                // 이전 청크가 있으면 저장
                if (currentChunkContent.length() > 0 && currentTitle != null) {
                    String chunkContent = currentChunkContent.toString().trim();
                    // 청크 분할하지 않고 하나의 청크로 생성
                    StructuredChunk chunk = createTitleBasedChunk(documentId, chunkIndex++, currentTitle, 
                            chunkContent, currentTitleLevel, currentChunkElements);
                    chunks.add(chunk);
                    
                    // 생성된 청크 정보 로깅
                    int tokenCount = estimateTokenCount(chunkContent);
                    log.info("📦 [CHUNK CREATED] Title: '{}', Length: {}, Tokens: ~{}, Level: {}, ChunkId: {}", 
                            chunk.getTitle(), chunk.getContent().length(), tokenCount, chunk.getHierarchyLevel(), chunk.getChunkId());
                    log.debug("📄 [CHUNK CONTENT] Content preview: {}", 
                            chunkContent.length() > 200 ? chunkContent.substring(0, 200) + "..." : chunkContent);
                }
                
                // 새로운 청크 시작
                currentTitle = isH1Header ? extractMarkdownHeaderText(text) : text;
                currentChunkContent = new StringBuilder();
                currentChunkElements = new ArrayList<>();
                
                // Title 레벨 결정 (H1이므로 레벨 1)
                currentTitleLevel = 1;
                
                log.info("🏷️ [CHUNKING] Starting new H1 chunk with title: '{}' (level: {})", 
                        currentTitle, currentTitleLevel);
            } else {
                // Title이 아닌 요소들은 현재 청크에 추가
                if (currentTitle != null) {
                    // 요소 타입에 따라 적절한 구분자 추가
                    if (currentChunkContent.length() > 0) {
                        currentChunkContent.append("\n\n");
                    }
                    
                    // 요소 타입별로 적절한 포맷팅
                    switch (elementType) {
                        case "Header" -> {
                            // 헤더의 레벨에 따라 마크다운 형식 적용
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
                    // Title이 없는 요소들은 첫 번째 요소로 간주하여 currentChunkContent에 추가
                    log.debug("🔸 [CHUNKING] Adding element without title to content: type={}", elementType);
                    if (currentChunkContent.length() > 0) {
                        currentChunkContent.append("\n\n");
                    }
                    currentChunkContent.append(text);
                    currentChunkElements.add(element);
                    
                    // 첫 번째 요소가 Title이 아닌 경우 기본 제목 설정
                    if (currentTitle == null && !currentChunkElements.isEmpty()) {
                        currentTitle = "문서 시작 부분";
                        currentTitleLevel = 1;
                        log.info("🏷️ [CHUNKING] Setting default title for document start");
                    }
                }
            }
        }
        
        // 마지막 청크 처리
        if (currentChunkContent.length() > 0) {
            String chunkContent = currentChunkContent.toString().trim();
            // 제목이 없으면 기본 제목 설정
            if (currentTitle == null) {
                currentTitle = "문서 내용";
                currentTitleLevel = 1;
            }
            
            // 청크 분할하지 않고 하나의 청크로 생성
            StructuredChunk finalChunk = createTitleBasedChunk(documentId, chunkIndex++, currentTitle, 
                    chunkContent, currentTitleLevel, currentChunkElements);
            chunks.add(finalChunk);
            
            // 마지막 청크 정보 로깅
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
        
        // 청크 통계 로깅
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
            
            // 각 청크의 요약 정보 로깅
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
                
                // 내용이 긴 경우 미리보기만 표시
                String contentPreview = chunk.getContent().length() > 500 ? 
                    chunk.getContent().substring(0, 500) + "..." : chunk.getContent();
                log.info("     Content Preview: {}", contentPreview);
            }
        }
        
        return chunks;
    }

    /**
     * Title 기반 청크를 생성합니다.
     */
    private StructuredChunk createTitleBasedChunk(UUID documentId, int chunkIndex, String title, 
                                                 String content, int titleLevel, List<Map<String, Object>> elements) {
        return StructuredChunk.builder()
                .documentId(documentId.toString())
                .chunkId(generateChunkId(documentId, chunkIndex))
                .content(content)
                .title(title)
                .hierarchyLevel(titleLevel)
                .parentChunkId(null) // Title 기반 청크는 독립적
                .elementType("TitleBasedChunk")
                .metadata(createTitleBasedMetadata(title, titleLevel, elements))
                .build();
    }

    /**
     * Title 기반 청크의 메타데이터를 생성합니다.
     */
    private Map<String, Object> createTitleBasedMetadata(String title, int titleLevel, List<Map<String, Object>> elements) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("chunk_type", "title_based");
        metadata.put("title_level", titleLevel);
        metadata.put("element_count", elements.size());
        
        // 요소 타입별 개수 집계
        Map<String, Long> elementTypeCounts = elements.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        e -> (String) e.get("type"),
                        java.util.stream.Collectors.counting()
                ));
        metadata.put("element_type_counts", elementTypeCounts);
        
        // 첫 번째와 마지막 요소의 메타데이터 정보 보존
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
     * H1 마크다운 헤더인지 확인합니다 (# 하나만).
     * title_depth=1 설정으로 인해 #만 Title로 올 것이므로 간단한 확인만 필요
     */
    private boolean isH1MarkdownHeader(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        String trimmed = text.trim();
        
        // # 하나로 시작하고 공백이 오는 패턴만 확인
        if (trimmed.matches("^#\\s+.+")) {
            String headerText = trimmed.replaceFirst("^#\\s*", "").trim();
            
            // JavaScript 키워드나 console.log 등으로 시작하는 경우 제외
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
     * 마크다운 헤더에서 텍스트를 추출합니다.
     */
    private String extractMarkdownHeaderText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        String trimmed = text.trim();
        // # 기호들과 공백을 제거하여 실제 제목 텍스트만 추출
        return trimmed.replaceFirst("^#+\\s*", "").trim();
    }

    /**
     * 텍스트의 토큰 수를 추정합니다.
     * GPT 계열 토크나이저를 기준으로 한 근사치 계산
     * (정확한 토큰 계산을 위해서는 실제 토크나이저 라이브러리를 사용해야 함)
     */
    private int estimateTokenCount(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        
        // 간단한 토큰 추정 알고리즘:
        // 1. 공백으로 단어 분리
        // 2. 평균적으로 영어 단어 1개 = 1.3 토큰, 한글 1글자 = 1.5 토큰으로 계산
        // 3. 구두점, 특수문자 등도 고려
        
        String cleanText = text.trim();
        
        // 공백 기준 단어 수
        String[] words = cleanText.split("\\s+");
        int wordCount = words.length;
        
        // 한글 문자 수
        long koreanCharCount = cleanText.chars()
                .filter(c -> Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HANGUL_SYLLABLES ||
                           Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HANGUL_JAMO ||
                           Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO)
                .count();
        
        // 영문자 수
        long englishCharCount = cleanText.chars()
                .filter(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))
                .count();
        
        // 숫자 및 특수문자 수
        long otherCharCount = cleanText.length() - koreanCharCount - englishCharCount;
        
        // 토큰 수 추정
        // - 영어 단어: 평균 1.3 토큰
        // - 한글 문자: 1.5 토큰
        // - 기타 문자: 0.8 토큰
        double estimatedTokens = (wordCount * 1.3) + (koreanCharCount * 1.5) + (otherCharCount * 0.8);
        
        // 최소 1 토큰은 보장
        return Math.max(1, (int) Math.round(estimatedTokens));
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
}