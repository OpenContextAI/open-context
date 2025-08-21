package com.opencontext.service;

import com.opencontext.entity.SourceDocument;
import com.opencontext.enums.ErrorCode;
import com.opencontext.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Unstructured API를 사용하여 문서를 파싱하는 서비스.
 * 
 * Service for parsing documents using the Unstructured API.
 * 
 * Converts PDF, Markdown, and text files into structured elements.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParsingService {

    private final FileStorageService fileStorageService;
    private final RestTemplate restTemplate;

    @Value("${app.unstructured.api.url:http://localhost:8000}")
    private String unstructuredApiUrl;

    /**
     * 문서를 파싱하여 구조화된 요소 목록을 반환합니다.
     * 
     * @param documentId 파싱할 문서 ID
     * @return 파싱된 요소 목록 (Unstructured API 응답)
     */
    public List<Map<String, Object>> parseDocument(UUID documentId) {
        long startTime = System.currentTimeMillis();
        log.info("📝 [PARSING] Starting document parsing: documentId={}", documentId);

        log.debug("📖 [PARSING] Step 1/3: Retrieving document metadata: documentId={}", documentId);
        SourceDocument document = fileStorageService.getDocument(documentId);
        String filename = document.getOriginalFilename();
        String fileType = document.getFileType();
        long fileSize = document.getFileSize();
        
        log.info("✅ [PARSING] Document metadata retrieved: filename={}, fileType={}, size={} bytes", 
                filename, fileType, fileSize);

        try {
            // Step 2: MinIO에서 파일 다운로드
            log.debug("☁️ [PARSING] Step 2/3: Downloading file from MinIO: path={}", document.getFileStoragePath());
            InputStream fileStream = fileStorageService.downloadFile(document.getFileStoragePath());
            log.info("✅ [PARSING] File downloaded from storage: filename={}, path={}", 
                    filename, document.getFileStoragePath());
            
            List<Map<String, Object>> parsedElements;
            
            // 마크다운 파일인 경우 특별 처리
            if (isMarkdownFile(filename, fileType)) {
                log.info("📝 [PARSING] Detected markdown file, using hybrid parsing approach: {}", filename);
                parsedElements = parseMarkdownDocument(fileStream, filename, fileType);
            } else {
                // Step 3: Unstructured API 호출
                log.debug("🤖 [PARSING] Step 3/3: Calling Unstructured API: filename={}, fileType={}", filename, fileType);
                parsedElements = callUnstructuredApi(fileStream, filename, fileType);
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("🎉 [PARSING] Document parsing completed successfully: documentId={}, filename={}, elements={}, duration={}ms", 
                    documentId, filename, parsedElements.size(), duration);
            
            return parsedElements;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("❌ [PARSING] Document parsing failed: documentId={}, filename={}, duration={}ms, error={}", 
                    documentId, filename, duration, e.getMessage(), e);
            throw new BusinessException(ErrorCode.DOCUMENT_PARSING_FAILED, 
                    "Document parsing failed: " + e.getMessage());
        }
    }

    /**
     * Unstructured API를 호출하여 문서를 파싱합니다.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> callUnstructuredApi(InputStream fileStream, 
                                                          String filename, 
                                                          String fileType) {
        long apiStartTime = System.currentTimeMillis();
        log.debug("🤖 [UNSTRUCTURED-API] Starting API call: filename={}, fileType={}, url={}", 
                filename, fileType, unstructuredApiUrl);
        
        try {
            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            // 멀티파트 요청 생성
            log.debug("📦 [UNSTRUCTURED-API] Preparing multipart request: filename={}", filename);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("files", new InputStreamResource(fileStream, filename));
            
            // 파일 타입별 파싱 옵션 설정
            String strategy = determineOptimalStrategy(fileType, filename);
            body.add("strategy", strategy);
            body.add("coordinates", "true");
            body.add("extract_images_in_pdf", "false");
            body.add("infer_table_structure", "true");
            
            // 마크다운 파일의 경우 추가 파라미터 설정
            if (isMarkdownFile(filename, fileType)) {
                // 마크다운 파싱을 위한 특별한 설정
                body.add("include_page_breaks", "false");
                body.add("split_pdf_page", "false");
                body.add("split_pdf_allow_failed", "true");
                // 마크다운 헤더 인식 개선을 위한 설정
                body.add("skip_infer_table_types", "[]");
                body.add("languages", "ko,en");  // 한국어, 영어 지원
                body.add("detect_language_per_element", "true");
                // # (h1)만 title로 인식하도록 설정
                body.add("title_depth", "1");  // 1로 설정하면 #만 title로 인식
                body.add("heading_detection_strategy", "markdown");  // 마크다운 헤더 감지 전략
                log.debug("📝 [UNSTRUCTURED-API] Markdown-specific parameters applied with title_depth=1 (only # headers as titles)");
            }
            
            log.debug("⚙️ [UNSTRUCTURED-API] Request parameters: strategy={}, coordinates=true, extract_images_in_pdf=false, infer_table_structure=true", strategy);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // API 호출
            log.debug("🚀 [UNSTRUCTURED-API] Sending HTTP request: {}/general/v0/general", unstructuredApiUrl);
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    unstructuredApiUrl + "/general/v0/general",
                    HttpMethod.POST,
                    requestEntity,
                    (Class<List<Map<String, Object>>>) (Class<?>) List.class
            );

            long apiDuration = System.currentTimeMillis() - apiStartTime;
            
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.error("❌ [UNSTRUCTURED-API] API returned unexpected response: filename={}, status={}, duration={}ms", 
                        filename, response.getStatusCode(), apiDuration);
                throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, 
                        "Unstructured API returned unexpected response");
            }

            List<Map<String, Object>> elements = response.getBody();
            int elementCount = elements.size();
            
            log.info("✅ [UNSTRUCTURED-API] API call successful: filename={}, elements={}, duration={}ms, status={}", 
                    filename, elementCount, apiDuration, response.getStatusCode());
            
            if (elementCount > 0) {
                log.debug("📊 [UNSTRUCTURED-API] Element types found: {}", 
                        elements.stream()
                                .map(e -> e.get("type"))
                                .distinct()
                                .collect(java.util.stream.Collectors.toList()));
                
                // 첫 번째 몇 개 요소의 상세 정보 로깅
                log.info("📋 [UNSTRUCTURED-API] First 10 elements details:");
                for (int i = 0; i < Math.min(10, elementCount); i++) {
                    Map<String, Object> element = elements.get(i);
                    String type = (String) element.get("type");
                    String text = (String) element.get("text");
                    
                    // 텍스트가 너무 길면 잘라서 표시
                    String displayText = text != null && text.length() > 100 ? 
                        text.substring(0, 100) + "..." : text;
                    
                    log.info("  {}. Type: '{}' | Text: '{}'", i + 1, type, displayText);
                }
                
                // 마크다운 헤더를 찾아서 로깅
                log.info("🔍 [UNSTRUCTURED-API] Searching for markdown headers in parsed elements:");
                int headerCount = 0;
                for (int i = 0; i < elementCount; i++) {
                    Map<String, Object> element = elements.get(i);
                    String text = (String) element.get("text");
                    if (text != null && text.trim().matches("^#+\\s+.+")) {
                        headerCount++;
                        log.info("  Found potential header #{}: '{}'", headerCount, text.trim());
                    }
                }
                if (headerCount == 0) {
                    log.warn("⚠️ [UNSTRUCTURED-API] No markdown headers found in parsed elements");
                }
            }

            return elements;

        } catch (Exception e) {
            long apiDuration = System.currentTimeMillis() - apiStartTime;
            log.error("❌ [UNSTRUCTURED-API] API call failed: filename={}, duration={}ms, error={}", 
                    filename, apiDuration, e.getMessage(), e);
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, 
                    "Failed to call Unstructured API: " + e.getMessage());
        }
    }

    /**
     * 파일 타입에 따라 최적의 파싱 전략을 결정합니다.
     */
    private String determineOptimalStrategy(String fileType, String filename) {
        if (isMarkdownFile(filename, fileType)) {
            return "fast";  // 마크다운 파일은 fast 전략이 헤더 인식에 더 적합
        } else if (isPdfFile(filename, fileType)) {
            return "hi_res";  // PDF는 레이아웃 분석이 중요
        } else {
            return "auto";  // 기타 파일은 자동 탐지
        }
    }

    /**
     * 마크다운 파일인지 확인합니다.
     */
    private boolean isMarkdownFile(String filename, String fileType) {
        if (filename != null) {
            String lowerFilename = filename.toLowerCase();
            if (lowerFilename.endsWith(".md") || lowerFilename.endsWith(".markdown")) {
                return true;
            }
        }
        if (fileType != null) {
            String lowerFileType = fileType.toLowerCase();
            return lowerFileType.contains("markdown") || lowerFileType.equals("text/markdown");
        }
        return false;
    }

    /**
     * PDF 파일인지 확인합니다.
     */
    private boolean isPdfFile(String filename, String fileType) {
        if (filename != null && filename.toLowerCase().endsWith(".pdf")) {
            return true;
        }
        if (fileType != null) {
            return fileType.toLowerCase().contains("pdf");
        }
        return false;
    }

    /**
     * InputStream을 Spring의 Resource로 래핑하는 헬퍼 클래스
     */
    private static class InputStreamResource extends org.springframework.core.io.InputStreamResource {
        private final String filename;
 
        public InputStreamResource(InputStream inputStream, String filename) {
            super(inputStream);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return this.filename;
        }

        @Override
        public long contentLength() {
            return -1; // 알 수 없음을 나타냄
        }
    }

    /**
     * 마크다운 파일을 위한 하이브리드 파싱 메서드
     * 직접 파싱과 Unstructured API를 비교하여 더 나은 결과를 선택합니다.
     */
    private List<Map<String, Object>> parseMarkdownDocument(InputStream fileStream, String filename, String fileType) {
        // 스트림을 두 번 사용하기 위해 바이트 배열로 변환
        try {
            byte[] fileContent = fileStream.readAllBytes();
            
            // 먼저 직접 파싱 시도
            try (InputStream directStream = new ByteArrayInputStream(fileContent)) {
                List<Map<String, Object>> directResult = processMarkdownDirectly(directStream, filename);
                
                // 헤더가 제대로 인식되었는지 확인
                long headerCount = directResult.stream()
                        .filter(e -> "Title".equals(e.get("type")))
                        .map(e -> (String) e.get("text"))
                        .filter(text -> text != null && text.trim().matches("^#+\\s+.+"))
                        .count();
                        
                if (headerCount > 0) {
                    log.info("✅ [MARKDOWN-DECISION] Using direct processing: {} valid headers found", headerCount);
                    return directResult;
                }
            }
            
            // 직접 파싱이 실패했다면 Unstructured API 사용
            log.info("🔄 [MARKDOWN-DECISION] Direct parsing failed, falling back to Unstructured API");
            try (InputStream apiStream = new ByteArrayInputStream(fileContent)) {
                List<Map<String, Object>> apiResult = callUnstructuredApi(apiStream, filename, fileType);
                
                // API 결과에서 마크다운 후처리 적용
                return postProcessMarkdownElements(apiResult);
            }
            
        } catch (IOException e) {
            log.error("❌ [MARKDOWN-DECISION] Failed to process markdown: {}", filename, e);
            throw new BusinessException(ErrorCode.DOCUMENT_PARSING_FAILED, 
                    "Failed to process markdown file: " + e.getMessage());
        }
    }

    /**
     * 마크다운 파일을 위한 전용 직접 처리 메서드
     */
    private List<Map<String, Object>> processMarkdownDirectly(InputStream fileStream, String filename) {
        log.debug("📝 [MARKDOWN-PROCESSOR] Processing markdown file directly: {}", filename);
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(fileStream, StandardCharsets.UTF_8))) {
            List<Map<String, Object>> elements = new ArrayList<>();
            StringBuilder currentContent = new StringBuilder();
            String currentType = null;
            int elementIndex = 0;
            boolean inCodeBlock = false;
            String codeLanguage = null;
            
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();
                
                // 코드 블록 처리
                if (trimmedLine.startsWith("```")) {
                    if (!inCodeBlock) {
                        // 코드 블록 시작
                        if (currentContent.length() > 0) {
                            addElement(elements, currentType, currentContent.toString(), filename, elementIndex++);
                            currentContent.setLength(0);
                        }
                        inCodeBlock = true;
                        codeLanguage = trimmedLine.substring(3).trim();
                        currentType = "CodeBlock";
                    } else {
                        // 코드 블록 종료
                        addElement(elements, "CodeBlock", currentContent.toString(), filename, elementIndex++, codeLanguage);
                        currentContent.setLength(0);
                        inCodeBlock = false;
                        codeLanguage = null;
                        currentType = null;
                    }
                    continue;
                }
                
                if (inCodeBlock) {
                    if (currentContent.length() > 0) {
                        currentContent.append("\n");
                    }
                    currentContent.append(line);
                    continue;
                }
                
                // 헤더 처리 (# ~ ######)
                if (trimmedLine.matches("^#{1,6}\\s+.+")) {
                    if (currentContent.length() > 0) {
                        addElement(elements, currentType, currentContent.toString(), filename, elementIndex++);
                        currentContent.setLength(0);
                    }
                    
                    addElement(elements, "Title", trimmedLine, filename, elementIndex++);
                    currentType = null;
                    continue;
                }
                
                // 빈 줄 처리
                if (trimmedLine.isEmpty()) {
                    if (currentContent.length() > 0 && currentType != null) {
                        addElement(elements, currentType, currentContent.toString(), filename, elementIndex++);
                        currentContent.setLength(0);
                        currentType = null;
                    }
                    continue;
                }
                
                // 목록 처리
                if (trimmedLine.matches("^[-*+]\\s+.+") || trimmedLine.matches("^\\d+\\.\\s+.+")) {
                    if (currentType != null && !currentType.equals("ListItem")) {
                        if (currentContent.length() > 0) {
                            addElement(elements, currentType, currentContent.toString(), filename, elementIndex++);
                            currentContent.setLength(0);
                        }
                    }
                    
                    if (currentType == null || !currentType.equals("ListItem")) {
                        currentType = "ListItem";
                    }
                    
                    if (currentContent.length() > 0) {
                        currentContent.append("\n");
                    }
                    currentContent.append(line);
                    continue;
                }
                
                // 인용문 처리
                if (trimmedLine.startsWith(">")) {
                    if (currentType != null && !currentType.equals("Quote")) {
                        if (currentContent.length() > 0) {
                            addElement(elements, currentType, currentContent.toString(), filename, elementIndex++);
                            currentContent.setLength(0);
                        }
                    }
                    currentType = "Quote";
                    if (currentContent.length() > 0) {
                        currentContent.append("\n");
                    }
                    currentContent.append(line);
                    continue;
                }
                
                // 일반 텍스트 처리
                if (currentType == null) {
                    currentType = "NarrativeText";
                }
                
                if (currentContent.length() > 0) {
                    currentContent.append("\n");
                }
                currentContent.append(line);
            }
            
            // 마지막 요소 처리
            if (currentContent.length() > 0) {
                addElement(elements, currentType != null ? currentType : "NarrativeText", 
                          currentContent.toString(), filename, elementIndex++, codeLanguage);
            }
            
            log.info("✅ [MARKDOWN-PROCESSOR] Processed {} elements from markdown file: {}", 
                    elements.size(), filename);
            
            return elements;
            
        } catch (IOException e) {
            log.error("❌ [MARKDOWN-PROCESSOR] Failed to process markdown file: {}", filename, e);
            throw new BusinessException(ErrorCode.DOCUMENT_PARSING_FAILED, 
                    "Failed to process markdown file: " + e.getMessage());
        }
    }

    /**
     * 요소를 리스트에 추가하는 헬퍼 메서드
     */
    private void addElement(List<Map<String, Object>> elements, String type, String content, 
                           String filename, int index, String... additionalInfo) {
        Map<String, Object> element = new HashMap<>();
        element.put("type", type);
        element.put("text", content.trim());
        element.put("element_id", UUID.randomUUID().toString().replace("-", ""));
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("filename", filename);
        metadata.put("filetype", "text/markdown");
        metadata.put("languages", List.of("eng"));
        metadata.put("element_index", index);
        
        if (additionalInfo.length > 0 && additionalInfo[0] != null) {
            metadata.put("code_language", additionalInfo[0]);
        }
        
        element.put("metadata", metadata);
        elements.add(element);
    }

    /**
     * 마크다운 파싱 결과를 후처리하여 구조를 개선합니다.
     */
    private List<Map<String, Object>> postProcessMarkdownElements(List<Map<String, Object>> elements) {
        List<Map<String, Object>> processedElements = new ArrayList<>();
        
        for (Map<String, Object> element : elements) {
            String type = (String) element.get("type");
            String text = (String) element.get("text");
            
            if (text != null && text.trim().length() > 0) {
                String trimmedText = text.trim();
                
                // 마크다운 헤더 패턴 감지 및 수정
                if (trimmedText.matches("^#+\\s+.+")) {
                    // 헤더로 인식되어야 하는데 다른 타입으로 분류된 경우
                    Map<String, Object> correctedElement = new HashMap<>(element);
                    correctedElement.put("type", "Title");
                    processedElements.add(correctedElement);
                    log.debug("🔧 [POST-PROCESS] Corrected header: '{}' -> Title", trimmedText);
                } else if (trimmedText.startsWith("```") || trimmedText.endsWith("```")) {
                    // 코드 블록 시작/끝 마커는 제거
                    continue;
                } else if ("Title".equals(type) && !trimmedText.matches("^#+\\s+.+") && 
                          !trimmedText.startsWith("**") && trimmedText.length() > 100) {
                    // Title로 잘못 분류된 긴 텍스트를 NarrativeText로 수정
                    Map<String, Object> correctedElement = new HashMap<>(element);
                    correctedElement.put("type", "NarrativeText");
                    processedElements.add(correctedElement);
                    log.debug("🔧 [POST-PROCESS] Corrected long title to narrative: '{}'...", 
                             trimmedText.substring(0, Math.min(50, trimmedText.length())));
                } else if ("Title".equals(type) && (trimmedText.startsWith("console.log") || 
                          trimmedText.startsWith("//") || trimmedText.contains("function ") ||
                          trimmedText.contains("const ") || trimmedText.contains("let ") ||
                          trimmedText.contains("var "))) {
                    // JavaScript 코드가 Title로 잘못 분류된 경우
                    Map<String, Object> correctedElement = new HashMap<>(element);
                    correctedElement.put("type", "CodeBlock");
                    processedElements.add(correctedElement);
                    log.debug("🔧 [POST-PROCESS] Corrected JS code title to code block: '{}'...", 
                             trimmedText.substring(0, Math.min(30, trimmedText.length())));
                } else {
                    processedElements.add(element);
                }
            } else {
                processedElements.add(element);
            }
        }
        
        log.info("🔧 [POST-PROCESS] Post-processing completed: {} -> {} elements", 
                elements.size(), processedElements.size());
        
        return processedElements;
    }
}
