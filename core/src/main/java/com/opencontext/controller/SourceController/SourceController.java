package com.opencontext.controller.SourceController;

import com.opencontext.common.CommonResponse;
import com.opencontext.common.PageResponse;
import com.opencontext.dto.SourceDocumentDto;
import com.opencontext.dto.SourceUploadResponse;
import com.opencontext.service.SourceDocumentService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * REST controller implementing source document management APIs.
 * 
 * This controller implements the DocsSourceController interface and provides
 * clean, Lombok-optimized code for document ingestion pipeline management.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sources")
@RequiredArgsConstructor
public class SourceController implements DocsSourceController {

    private final SourceDocumentService sourceDocumentService;

    @Override
    public ResponseEntity<CommonResponse<SourceUploadResponse>> uploadFile(@NotNull MultipartFile file) {
        log.info("Source document upload request: filename={}, size={}, contentType={}", 
                file.getOriginalFilename(), file.getSize(), file.getContentType());

        SourceUploadResponse response = sourceDocumentService.uploadSourceDocument(file);
        
        log.info("Source document upload accepted: id={}, filename={}", 
                response.getSourceDocumentId(), response.getOriginalFilename());

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(CommonResponse.success(response, "요청이 성공적으로 처리되었습니다."));
    }

    @Override
    public ResponseEntity<CommonResponse<PageResponse<SourceDocumentDto>>> getAllSourceDocuments(
            int page, int size, String sort) {
        
        log.debug("Source documents list request: page={}, size={}, sort={}", page, size, sort);

        // Parse sort parameter
        String[] sortParts = sort.split(",");
        String sortProperty = sortParts[0];
        Sort.Direction direction = sortParts.length > 1 && "desc".equals(sortParts[1]) 
                ? Sort.Direction.DESC : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortProperty));
        
        Page<SourceDocumentDto> documents = sourceDocumentService.getAllSourceDocuments(pageable);
        PageResponse<SourceDocumentDto> pageResponse = PageResponse.from(documents);

        return ResponseEntity.ok(CommonResponse.success(pageResponse, "요청이 성공적으로 처리되었습니다."));
    }

    @Override
    public ResponseEntity<CommonResponse<String>> resyncSourceDocument(UUID sourceId) {
        log.info("Source document resync request: id={}", sourceId);

        sourceDocumentService.resyncSourceDocument(sourceId);
        
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(CommonResponse.success(
                        "Document re-ingestion has been queued successfully.",
                        "요청이 성공적으로 처리되었습니다."
                ));
    }

    @Override
    public ResponseEntity<CommonResponse<String>> deleteSourceDocument(UUID sourceId) {
        log.info("Source document deletion request: id={}", sourceId);

        sourceDocumentService.deleteSourceDocument(sourceId);
        
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(CommonResponse.success(
                        "Document deletion has been queued successfully.",
                        "요청이 성공적으로 처리되었습니다."
                ));
    }
}