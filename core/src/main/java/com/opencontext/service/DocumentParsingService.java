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

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for parsing documents using the Unstructured API.
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

    @Value("${app.unstructured.api.url:http://unstructured-api:8000}")
    private String unstructuredApiUrl;

    /**
     * Parses a document and returns a list of structured elements.
     * 
     * @param documentId Document ID to parse
     * @return List of parsed elements (Unstructured API response)
     */
    public List<Map<String, Object>> parseDocument(UUID documentId) {
        long startTime = System.currentTimeMillis();
        log.info("[PARSING] Starting document parsing: documentId={}", documentId);

        log.debug("📖 [PARSING] Step 1/3: Retrieving document metadata: documentId={}", documentId);
        SourceDocument document = fileStorageService.getDocument(documentId);
        String filename = document.getOriginalFilename();
        String fileType = document.getFileType();
        long fileSize = document.getFileSize();
        
        log.info("[PARSING] Document metadata retrieved: filename={}, fileType={}, size={} bytes", 
                filename, fileType, fileSize);

        try {
            // Step 2: Download file from MinIO
            log.debug("[PARSING] Step 2/3: Downloading file from MinIO: path={}", document.getFileStoragePath());
            InputStream fileStream = fileStorageService.downloadFile(document.getFileStoragePath());
            log.info("[PARSING] File downloaded from storage: filename={}, path={}", 
                    filename, document.getFileStoragePath());
            
            // Step 3: Call Unstructured API
            log.debug("🤖 [PARSING] Step 3/3: Calling Unstructured API: filename={}, fileType={}", filename, fileType);
            List<Map<String, Object>> parsedElements = callUnstructuredApi(
                    fileStream, 
                    filename,
                    fileType
            );

            long duration = System.currentTimeMillis() - startTime;
            log.info("🎉 [PARSING] Document parsing completed successfully: documentId={}, filename={}, elements={}, duration={}ms", 
                    documentId, filename, parsedElements.size(), duration);
            
            return parsedElements;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[PARSING] Document parsing failed: documentId={}, filename={}, duration={}ms, error={}", 
                    documentId, filename, duration, e.getMessage(), e);
            throw new BusinessException(ErrorCode.DOCUMENT_PARSING_FAILED, 
                    "Document parsing failed: " + e.getMessage());
        }
    }

    /**
     * Calls the Unstructured API to parse the document.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> callUnstructuredApi(InputStream fileStream, 
                                                          String filename, 
                                                          String fileType) {
        long apiStartTime = System.currentTimeMillis();
        log.debug("🤖 [UNSTRUCTURED-API] Starting API call: filename={}, fileType={}, url={}", 
                filename, fileType, unstructuredApiUrl);
        
        try {
            // Set HTTP headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            // Create multipart request
            log.debug("📦 [UNSTRUCTURED-API] Preparing multipart request: filename={}", filename);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("files", new InputStreamResource(fileStream, filename));
            
            // Set parsing options
            body.add("strategy", "auto");
            body.add("coordinates", "true");
            body.add("extract_images_in_pdf", "false");
            body.add("infer_table_structure", "true");
            
            log.debug("[UNSTRUCTURED-API] Request parameters: strategy=auto, coordinates=true, extract_images_in_pdf=false, infer_table_structure=true");

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // Call API
            log.debug("[UNSTRUCTURED-API] Sending HTTP request: {}/general/v0/general", unstructuredApiUrl);
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    unstructuredApiUrl + "/general/v0/general",
                    HttpMethod.POST,
                    requestEntity,
                    (Class<List<Map<String, Object>>>) (Class<?>) List.class
            );

            long apiDuration = System.currentTimeMillis() - apiStartTime;
            
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.error("[UNSTRUCTURED-API] API returned unexpected response: filename={}, status={}, duration={}ms", 
                        filename, response.getStatusCode(), apiDuration);
                throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, 
                        "Unstructured API returned unexpected response");
            }

            List<Map<String, Object>> elements = response.getBody();
            int elementCount = elements.size();
            
            log.info("[UNSTRUCTURED-API] API call successful: filename={}, elements={}, duration={}ms, status={}", 
                    filename, elementCount, apiDuration, response.getStatusCode());
            
            if (elementCount > 0) {
                log.debug("📊 [UNSTRUCTURED-API] Element types found: {}", 
                        elements.stream()
                                .map(e -> e.get("type"))
                                .distinct()
                                .collect(java.util.stream.Collectors.toList()));
            }

            return elements;

        } catch (Exception e) {
            long apiDuration = System.currentTimeMillis() - apiStartTime;
            log.error("[UNSTRUCTURED-API] API call failed: filename={}, duration={}ms, error={}", 
                    filename, apiDuration, e.getMessage(), e);
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, 
                    "Failed to call Unstructured API: " + e.getMessage());
        }
    }

    /**
     * Helper class that wraps InputStream as Spring's Resource
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
            return -1; // Indicates unknown
        }
    }
}
