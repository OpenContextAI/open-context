package com.opencontext.controller;

import com.opencontext.common.CommonResponse;
import com.opencontext.common.PageResponse;
import com.opencontext.dto.SourceDocumentDto;
import com.opencontext.dto.SourceUploadResponse;
import com.opencontext.entity.SourceDocument;
import com.opencontext.enums.ErrorCode;
import com.opencontext.enums.IngestionStatus;
import com.opencontext.exception.BusinessException;
import com.opencontext.repository.DocumentChunkRepository;
import com.opencontext.repository.SourceDocumentRepository;
import com.opencontext.service.ChunkingService;
import com.opencontext.service.DocumentParsingService;
import com.opencontext.service.EmbeddingService;
import com.opencontext.service.FileStorageService;
import com.opencontext.service.IndexingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * 소스 문서 관리를 위한 REST API 컨트롤러.
 * 
 * 파일 업로드, 수집 파이프라인 관리, 문서 목록 조회 등의 기능을 제공합니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sources")
@RequiredArgsConstructor
public class SourceController implements DocsSourceController{

    private final SourceDocumentRepository sourceDocumentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentParsingService documentParsingService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final IndexingService indexingService;
    private final FileStorageService fileStorageService;
    private final RestTemplate restTemplate;

    @Value("${app.elasticsearch.url:http://localhost:9200}")
    private String elasticsearchUrl;

    @Value("${app.elasticsearch.index:document-chunks}")
    private String indexName;

    /**
     * 파일 업로드 및 수집 파이프라인 시작
     * 
     * @param file 업로드할 파일 (multipart/form-data)
     * @return 업로드 결과 및 문서 정보
     */
    @Override
    @PostMapping("/upload")
    public ResponseEntity<CommonResponse<SourceUploadResponse>> uploadFile(
            @RequestParam("file") MultipartFile file) {
        log.info("File upload requested: filename={}, size={}", file.getOriginalFilename(), file.getSize());

        try {
            // 파일 저장 및 문서 생성 (FileStorageService에서 검증 수행)
            SourceDocument sourceDocument = fileStorageService.uploadFileWithMetadata(file);
            log.info("File saved successfully: documentId={}, filename={}", 
                    sourceDocument.getId(), sourceDocument.getOriginalFilename());

            // 비동기 수집 파이프라인 시작
            processIngestionPipeline(sourceDocument.getId());
            
            // 응답 생성
            SourceUploadResponse response = SourceUploadResponse.success(
                    sourceDocument.getId().toString(), 
                    sourceDocument.getOriginalFilename()
            );
            
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(CommonResponse.success(response));
                    
        } catch (BusinessException e) {
            log.error("File upload failed: {}", e.getMessage());
            return ResponseEntity.status(getHttpStatusFromErrorCode(e.getErrorCode()))
                    .body(CommonResponse.error(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during file upload", e);
            return ResponseEntity.internalServerError()
                    .body(CommonResponse.error("파일 업로드 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 업로드된 모든 문서의 최신 상태 목록 조회
     * 
     * @param page 페이지 번호 (0부터 시작)
     * @param size 페이지 크기
     * @param sort 정렬 조건
     * @return 페이지네이션된 문서 목록
     */
    @Override
    @GetMapping
    public ResponseEntity<CommonResponse<PageResponse<SourceDocumentDto>>> getAllSourceDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        log.debug("Getting source documents: page={}, size={}, sort={}", page, size, sort);

        try {
            // 정렬 조건 파싱
            Sort sortObj = parseSort(sort);
            Pageable pageable = PageRequest.of(page, size, sortObj);
            
            // 문서 목록 조회
            Page<SourceDocument> documentPage = sourceDocumentRepository.findAll(pageable);
            
            // DTO 변환
            List<SourceDocumentDto> documentDtos = documentPage.getContent().stream()
                    .map(this::convertToDto)
                    .toList();
            
            PageResponse<SourceDocumentDto> pageResponse = PageResponse.<SourceDocumentDto>builder()
                    .content(documentDtos)
                    .page(documentPage.getNumber())
                    .size(documentPage.getSize())
                    .totalElements(documentPage.getTotalElements())
                    .totalPages(documentPage.getTotalPages())
                    .first(documentPage.isFirst())
                    .last(documentPage.isLast())
                    .hasNext(documentPage.hasNext())
                    .hasPrevious(documentPage.hasPrevious())
                    .build();
            
            return ResponseEntity.ok(CommonResponse.success(pageResponse));
            
        } catch (Exception e) {
            log.error("Failed to get source documents", e);
            return ResponseEntity.internalServerError()
                    .body(CommonResponse.error("문서 목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 특정 문서를 강제로 재수집
     * 
     * @param sourceId 재수집할 문서 ID
     * @return 재수집 시작 응답
     */
    @Override
    @PostMapping("/{sourceId}/resync")
    public ResponseEntity<CommonResponse<String>> resyncSourceDocument(@PathVariable UUID sourceId) {
        log.info("Document resync requested: sourceId={}", sourceId);

        try {
            // 문서 존재 확인
            SourceDocument sourceDocument = findSourceDocumentById(sourceId);
            
            // 처리 중인 문서인지 확인
            if (sourceDocument.isProcessing()) {
                throw new BusinessException(ErrorCode.RESOURCE_IS_BEING_PROCESSED, 
                        "문서가 이미 처리 중입니다.");
            }
            
            // 상태를 PENDING으로 초기화
            sourceDocument.updateIngestionStatus(IngestionStatus.PENDING);
            sourceDocument.clearErrorMessage();
            sourceDocumentRepository.save(sourceDocument);
            
            // 비동기 수집 파이프라인 시작
            processIngestionPipeline(sourceId);
            
            log.info("Document resync started successfully: sourceId={}", sourceId);
            
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(CommonResponse.success("문서 재수집이 시작되었습니다."));
                    
        } catch (BusinessException e) {
            log.error("Document resync failed: sourceId={}, error={}", sourceId, e.getMessage());
            return ResponseEntity.status(getHttpStatusFromErrorCode(e.getErrorCode()))
                    .body(CommonResponse.error(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during document resync: sourceId={}", sourceId, e);
            return ResponseEntity.internalServerError()
                    .body(CommonResponse.error("문서 재수집 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 특정 문서를 시스템에서 영구적으로 삭제
     * 
     * @param sourceId 삭제할 문서 ID
     * @return 삭제 시작 응답
     */
    @Override
    @DeleteMapping("/{sourceId}")
    public ResponseEntity<CommonResponse<String>> deleteSourceDocument(@PathVariable UUID sourceId) {
        log.info("Document deletion requested: sourceId={}", sourceId);

        try {
            // 문서 존재 확인
            SourceDocument sourceDocument = findSourceDocumentById(sourceId);
            
            // 처리 중인 문서인지 확인
            if (sourceDocument.isProcessing()) {
                throw new BusinessException(ErrorCode.RESOURCE_IS_BEING_PROCESSED, 
                        "처리 중인 문서는 삭제할 수 없습니다.");
            }
            
            // 삭제 상태로 변경
            sourceDocument.updateIngestionStatus(IngestionStatus.DELETING);
            sourceDocumentRepository.save(sourceDocument);
            
            // 비동기 삭제 파이프라인 시작
            processDeletionPipeline(sourceId);
            
            log.info("Document deletion started successfully: sourceId={}", sourceId);
            
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(CommonResponse.success("문서 삭제가 시작되었습니다."));
                    
        } catch (BusinessException e) {
            log.error("Document deletion failed: sourceId={}, error={}", sourceId, e.getMessage());
            return ResponseEntity.status(getHttpStatusFromErrorCode(e.getErrorCode()))
                    .body(CommonResponse.error(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during document deletion: sourceId={}", sourceId, e);
            return ResponseEntity.internalServerError()
                    .body(CommonResponse.error("문서 삭제 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 문서 수집 파이프라인을 비동기적으로 실행합니다.
     */
    @Async
    @Transactional
    public void processIngestionPipeline(UUID documentId) {
        log.info("Starting ingestion pipeline processing: documentId={}", documentId);

        try {
            // 1. Update status to PARSING
            fileStorageService.updateDocumentStatus(documentId, IngestionStatus.PARSING);
            
            // 2. Parse document using Unstructured API
            var parsedElements = documentParsingService.parseDocument(documentId);
            log.info("Document parsing completed: id={}, elements={}", documentId, parsedElements.size());

            // 3. Update status to CHUNKING
            fileStorageService.updateDocumentStatus(documentId, IngestionStatus.CHUNKING);
            
            // 4. Split into chunks
            var structuredChunks = chunkingService.createChunks(documentId, parsedElements);
            log.info("Document chunking completed: id={}, chunks={}", documentId, structuredChunks.size());

            // 5. Update status to EMBEDDING
            fileStorageService.updateDocumentStatus(documentId, IngestionStatus.EMBEDDING);
            
            // 6. Generate embeddings
            var embeddedChunks = embeddingService.generateEmbeddings(documentId, structuredChunks);
            log.info("Embedding generation completed: id={}, embedded_chunks={}", documentId, embeddedChunks.size());

            // 7. Update status to INDEXING
            fileStorageService.updateDocumentStatus(documentId, IngestionStatus.INDEXING);
            
            // 8. Store in Elasticsearch and PostgreSQL
            indexingService.indexChunks(documentId, embeddedChunks);
            log.info("Document indexing completed: id={}", documentId);

            // 9. Update status to COMPLETED
            fileStorageService.updateDocumentStatusToCompleted(documentId);
            
            log.info("Ingestion pipeline completed successfully: documentId={}", documentId);

        } catch (Exception e) {
            log.error("Ingestion pipeline failed: documentId={}", documentId, e);
            fileStorageService.updateDocumentStatusToError(documentId, e.getMessage());
            throw new BusinessException(ErrorCode.INGESTION_PIPELINE_FAILED, 
                    "Ingestion pipeline failed: " + e.getMessage());
        }
    }

    /**
     * 문서 삭제 파이프라인을 비동기적으로 실행합니다.
     */
    @Async
    @Transactional
    public void processDeletionPipeline(UUID documentId) {
        log.info("Starting deletion pipeline processing: documentId={}", documentId);

        try {
            // Get document storage path before deletion
            String fileStoragePath = fileStorageService.getDocumentStoragePath(documentId);

            // 1. Delete from Elasticsearch
            deleteFromElasticsearch(documentId);
            log.info("Deleted document from Elasticsearch: id={}", documentId);

            // 2. Delete chunks from PostgreSQL
            deleteChunksFromPostgreSQL(documentId);
            log.info("Deleted chunks from PostgreSQL: id={}", documentId);

            // 3. Delete document and file using FileStorageService
            fileStorageService.deleteDocument(documentId);
            log.info("Deleted document and file: id={}, path={}", documentId, fileStoragePath);

            log.info("Deletion pipeline completed successfully: documentId={}", documentId);

        } catch (Exception e) {
            log.error("Deletion pipeline failed: documentId={}", documentId, e);
            throw new BusinessException(ErrorCode.DELETION_PIPELINE_FAILED, 
                    "Deletion pipeline failed: " + e.getMessage());
        }
    }



    /**
     * Elasticsearch에서 문서 관련 청크들을 삭제합니다.
     */
    private void deleteFromElasticsearch(UUID documentId) {
        try {
            // 문서 ID로 쿼리하여 관련 청크들을 삭제
            String query = String.format("""
                {
                    "query": {
                        "term": {
                            "document_id": "%s"
                        }
                    }
                }
                """, documentId.toString());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> requestEntity = new HttpEntity<>(query, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    elasticsearchUrl + "/" + indexName + "/_delete_by_query",
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            log.debug("Elasticsearch deletion response: {}", response.getBody());

        } catch (Exception e) {
            log.error("Failed to delete from Elasticsearch: documentId={}", documentId, e);
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, 
                    "Failed to delete from Elasticsearch: " + e.getMessage());
        }
    }

    /**
     * PostgreSQL에서 문서 청크들을 삭제합니다.
     */
    private void deleteChunksFromPostgreSQL(UUID documentId) {
        try {
            int deletedCount = documentChunkRepository.deleteBySourceDocumentId(documentId);
            log.debug("Deleted {} chunks from PostgreSQL for document: {}", deletedCount, documentId);

        } catch (Exception e) {
            log.error("Failed to delete chunks from PostgreSQL: documentId={}", documentId, e);
            throw new BusinessException(ErrorCode.DATABASE_ERROR, 
                    "Failed to delete chunks from PostgreSQL: " + e.getMessage());
        }
    }

    // ===== 헬퍼 메소드들 =====

    /**
     * 정렬 조건 파싱
     */
    private Sort parseSort(String sortParam) {
        try {
            String[] parts = sortParam.split(",");
            if (parts.length == 2) {
                String property = parts[0].trim();
                String direction = parts[1].trim();
                return "desc".equalsIgnoreCase(direction) 
                        ? Sort.by(property).descending() 
                        : Sort.by(property).ascending();
            }
            return Sort.by(parts[0].trim()).descending();
        } catch (Exception e) {
            log.warn("Invalid sort parameter: {}, using default", sortParam);
            return Sort.by("createdAt").descending();
        }
    }

    /**
     * SourceDocument를 DTO로 변환
     */
    private SourceDocumentDto convertToDto(SourceDocument document) {
        return SourceDocumentDto.builder()
                .id(document.getId().toString())
                .originalFilename(document.getOriginalFilename())
                .fileType(document.getFileType())
                .fileSize(document.getFileSize())
                .ingestionStatus(document.getIngestionStatus().name())
                .errorMessage(document.getErrorMessage())
                .lastIngestedAt(document.getLastIngestedAt())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }

    /**
     * ID로 SourceDocument 조회 (없으면 예외 발생)
     */
    private SourceDocument findSourceDocumentById(UUID sourceId) {
        return sourceDocumentRepository.findById(sourceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOURCE_DOCUMENT_NOT_FOUND, 
                        "해당 ID의 문서를 찾을 수 없습니다: " + sourceId));
    }

    /**
     * ErrorCode에 따른 HTTP 상태 코드 반환
     */
    private HttpStatus getHttpStatusFromErrorCode(ErrorCode errorCode) {
        return switch (errorCode) {
            case VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
            case INSUFFICIENT_PERMISSION -> HttpStatus.FORBIDDEN;
            case SOURCE_DOCUMENT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DUPLICATE_FILE_UPLOADED -> HttpStatus.CONFLICT;
            case RESOURCE_IS_BEING_PROCESSED -> HttpStatus.CONFLICT;
            case PAYLOAD_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

}
