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
import org.springframework.transaction.annotation.Propagation;

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
 * REST API controller for managing source documents.
 * 
 * REST API controller for managing source documents.
 *
 * Provides functionalities such as file upload, ingestion pipeline management, and document listing.
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
     * File upload and ingestion pipeline start
     * 
     * @param file File to upload (multipart/form-data)
     * @return Upload result and document information
     */
    @Override
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommonResponse<SourceUploadResponse>> uploadFile(
            @RequestParam("file") MultipartFile file) {
        log.info("File upload requested: filename={}, size={}", file.getOriginalFilename(), file.getSize());

        try {
            // File storage and document creation (validation performed in FileStorageService)
            SourceDocument sourceDocument = fileStorageService.uploadFileWithMetadata(file);
            log.info("File saved successfully: documentId={}, filename={}", 
                    sourceDocument.getId(), sourceDocument.getOriginalFilename());

            // Start asynchronous ingestion pipeline
            processIngestionPipeline(sourceDocument.getId());
            
            // Create response
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
                    .body(CommonResponse.error("An error occurred during file upload: " + e.getMessage()));
        }
    }

    /**
     * Retrieve latest status list of all uploaded documents
     * 
     * @param page Page number (starting from 0)
     * @param size Page size
     * @param sort Sorting criteria
     * @return Paginated document list
     */
    @Override
    @GetMapping
    public ResponseEntity<CommonResponse<PageResponse<SourceDocumentDto>>> getAllSourceDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        log.debug("Getting source documents: page={}, size={}, sort={}", page, size, sort);

        try {
            // Parse sorting criteria
            Sort sortObj = parseSort(sort);
            Pageable pageable = PageRequest.of(page, size, sortObj);
            
            // Retrieve document list
            Page<SourceDocument> documentPage = sourceDocumentRepository.findAll(pageable);
            
            // Convert to DTO
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
                    .body(CommonResponse.error("An error occurred while retrieving document list: " + e.getMessage()));
        }
    }

    /**
     * Force re-ingestion of a specific document
     * 
     * @param sourceId Document ID to re-ingest
     * @return Re-ingestion start response
     */
    @Override
    @PostMapping("/{sourceId}/resync")
    public ResponseEntity<CommonResponse<String>> resyncSourceDocument(@PathVariable UUID sourceId) {
        log.info("Document resync requested: sourceId={}", sourceId);

        try {
            // Check if document exists
            SourceDocument sourceDocument = findSourceDocumentById(sourceId);
            
            // Check if document is being processed
            if (sourceDocument.isProcessing()) {
                throw new BusinessException(ErrorCode.RESOURCE_IS_BEING_PROCESSED, 
                        "Document is already being processed.");
            }
            
            // Reset status to PENDING
            sourceDocument.updateIngestionStatus(IngestionStatus.PENDING);
            sourceDocument.clearErrorMessage();
            sourceDocumentRepository.save(sourceDocument);
            
            // Start asynchronous ingestion pipeline
            processIngestionPipeline(sourceId);
            
            log.info("Document resync started successfully: sourceId={}", sourceId);
            
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(CommonResponse.success("Document re-ingestion has started."));
                    
        } catch (BusinessException e) {
            log.error("Document resync failed: sourceId={}, error={}", sourceId, e.getMessage());
            return ResponseEntity.status(getHttpStatusFromErrorCode(e.getErrorCode()))
                    .body(CommonResponse.error(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during document resync: sourceId={}", sourceId, e);
            return ResponseEntity.internalServerError()
                    .body(CommonResponse.error("An error occurred during document re-ingestion: " + e.getMessage()));
        }
    }

    /**
     * Permanently delete a specific document from the system
     * 
     * @param sourceId Document ID to delete
     * @return Deletion start response
     */
    @Override
    @DeleteMapping("/{sourceId}")
    public ResponseEntity<CommonResponse<String>> deleteSourceDocument(@PathVariable UUID sourceId) {
        log.info("Document deletion requested: sourceId={}", sourceId);

        try {
            // Check if document exists
            SourceDocument sourceDocument = findSourceDocumentById(sourceId);
            
            // Check if document is being processed
            if (sourceDocument.isProcessing()) {
                throw new BusinessException(ErrorCode.RESOURCE_IS_BEING_PROCESSED, 
                        "Documents being processed cannot be deleted.");
            }
            
            // Change status to DELETING
            sourceDocument.updateIngestionStatus(IngestionStatus.DELETING);
            sourceDocumentRepository.save(sourceDocument);
            
            // Start asynchronous deletion pipeline
            processDeletionPipeline(sourceId);
            
            log.info("Document deletion started successfully: sourceId={}", sourceId);
            
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(CommonResponse.success("Document deletion has started."));
                    
        } catch (BusinessException e) {
            log.error("Document deletion failed: sourceId={}, error={}", sourceId, e.getMessage());
            return ResponseEntity.status(getHttpStatusFromErrorCode(e.getErrorCode()))
                    .body(CommonResponse.error(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during document deletion: sourceId={}", sourceId, e);
            return ResponseEntity.internalServerError()
                    .body(CommonResponse.error("An error occurred during document deletion: " + e.getMessage()));
        }
    }

    /**
     * Executes the document ingestion pipeline asynchronously.
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
     * Executes the document deletion pipeline asynchronously.
     */
    @Async
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
     * Deletes document-related chunks from Elasticsearch.
     */
    @Transactional
    private void deleteFromElasticsearch(UUID documentId) {
        try {
            // Query by document ID to delete related chunks
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
     * Deletes document chunks from PostgreSQL.
     */
    @Transactional
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

    // ===== Helper Methods =====

    /**
     * Parse sorting criteria
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
     * Convert SourceDocument to DTO
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
     * Find SourceDocument by ID (throws exception if not found)
     */
    private SourceDocument findSourceDocumentById(UUID sourceId) {
        return sourceDocumentRepository.findById(sourceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOURCE_DOCUMENT_NOT_FOUND, 
                        "Document with the specified ID not found: " + sourceId));
    }

    /**
     * Return HTTP status code based on ErrorCode
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
