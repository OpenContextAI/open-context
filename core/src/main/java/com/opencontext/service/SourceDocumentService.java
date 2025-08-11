package com.opencontext.service;

import com.opencontext.dto.SourceDocumentDto;
import com.opencontext.dto.SourceUploadResponse;
import com.opencontext.entity.SourceDocument;
import com.opencontext.enums.ErrorCode;
import com.opencontext.enums.IngestionStatus;
import com.opencontext.exception.BusinessException;
import com.opencontext.repository.SourceDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing source document ingestion and lifecycle.
 * 
 * This service handles the complete document ingestion pipeline from upload
 * to indexing, integrating with MinIO storage and the async processing pipeline.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SourceDocumentService {

    private final SourceDocumentRepository sourceDocumentRepository;
    private final FileStorageService fileStorageService;

    // Supported file types for document processing
    private static final List<String> SUPPORTED_CONTENT_TYPES = List.of(
            "application/pdf",
            "text/markdown",
            "text/plain"
    );

    /**
     * Uploads a source document and starts the ingestion pipeline.
     * 
     * @param file the multipart file to upload
     * @return SourceUploadResponse containing the created document information
     */
    public SourceUploadResponse uploadSourceDocument(MultipartFile file) {
        log.info("Starting source document upload: filename={}, size={}, contentType={}", 
                file.getOriginalFilename(), file.getSize(), file.getContentType());

        // Validate file
        validateFile(file);

        // Calculate file checksum to prevent duplicates
        String fileChecksum = calculateFileChecksum(file);
        
        // Check for duplicate files
        if (sourceDocumentRepository.existsByFileChecksum(fileChecksum)) {
            log.warn("Duplicate file upload attempt: checksum={}", fileChecksum);
            throw new BusinessException(ErrorCode.DUPLICATE_FILE_UPLOADED, 
                    "A file with identical content already exists.");
        }

        try {
            // Upload file to MinIO
            String objectKey = fileStorageService.uploadFile(file);

            // Create SourceDocument entity
            SourceDocument sourceDocument = SourceDocument.builder()
                    .originalFilename(file.getOriginalFilename())
                    .fileStoragePath(objectKey)
                    .fileType(determineFileType(file.getContentType()))
                    .fileSize(file.getSize())
                    .fileChecksum(fileChecksum)
                    .ingestionStatus(IngestionStatus.PENDING)
                    .build();

            // Save to database
            SourceDocument savedDocument = sourceDocumentRepository.save(sourceDocument);
            
            log.info("Source document created successfully: id={}, filename={}", 
                    savedDocument.getId(), savedDocument.getOriginalFilename());

            // Start async ingestion pipeline
            startIngestionPipeline(savedDocument.getId());

            // Return response
            return SourceUploadResponse.success(
                    savedDocument.getId().toString(),
                    savedDocument.getOriginalFilename()
            );

        } catch (Exception e) {
            log.error("Failed to upload source document: {}", file.getOriginalFilename(), e);
            if (e instanceof BusinessException) {
                throw e;
            }
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, 
                    "Unexpected error during file upload: " + e.getMessage());
        }
    }

    /**
     * Retrieves all source documents with pagination.
     * 
     * @param pageable pagination parameters
     * @return Page of SourceDocumentDto
     */
    @Transactional(readOnly = true)
    public Page<SourceDocumentDto> getAllSourceDocuments(Pageable pageable) {
        log.debug("Retrieving source documents with pagination: page={}, size={}", 
                pageable.getPageNumber(), pageable.getPageSize());

        return sourceDocumentRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::convertToDto);
    }

    /**
     * Retrieves a specific source document by ID.
     * 
     * @param documentId the document ID
     * @return SourceDocumentDto
     */
    @Transactional(readOnly = true)
    public SourceDocumentDto getSourceDocument(UUID documentId) {
        log.debug("Retrieving source document: id={}", documentId);

        SourceDocument document = sourceDocumentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOURCE_DOCUMENT_NOT_FOUND, 
                        "Document not found: " + documentId));

        return convertToDto(document);
    }

    /**
     * Triggers re-ingestion of a source document.
     * 
     * @param documentId the document ID to re-ingest
     */
    public void resyncSourceDocument(UUID documentId) {
        log.info("Starting source document resync: id={}", documentId);

        SourceDocument document = sourceDocumentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOURCE_DOCUMENT_NOT_FOUND, 
                        "Document not found: " + documentId));

        // Check if document is currently being processed
        if (document.isProcessing()) {
            throw new BusinessException(ErrorCode.RESOURCE_IS_BEING_PROCESSED, 
                    "Document is currently being processed and cannot be resynced.");
        }

        // Reset status to PENDING and clear error message
        document.updateIngestionStatus(IngestionStatus.PENDING);
        sourceDocumentRepository.save(document);

        // Start async ingestion pipeline
        startIngestionPipeline(documentId);

        log.info("Source document resync initiated: id={}", documentId);
    }

    /**
     * Deletes a source document and all associated data.
     * 
     * @param documentId the document ID to delete
     */
    public void deleteSourceDocument(UUID documentId) {
        log.info("Starting source document deletion: id={}", documentId);

        SourceDocument document = sourceDocumentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOURCE_DOCUMENT_NOT_FOUND, 
                        "Document not found: " + documentId));

        // Check if document is currently being processed
        if (document.isProcessing()) {
            throw new BusinessException(ErrorCode.RESOURCE_IS_BEING_PROCESSED, 
                    "Document is currently being processed and cannot be deleted.");
        }

        try {
            // Mark document as being deleted
            document.updateIngestionStatus(IngestionStatus.DELETING);
            sourceDocumentRepository.save(document);

            // Start async deletion process
            startDeletionPipeline(documentId);

            log.info("Source document deletion initiated: id={}", documentId);

        } catch (Exception e) {
            log.error("Failed to initiate document deletion: id={}", documentId, e);
            throw new BusinessException(ErrorCode.FILE_DELETE_FAILED, 
                    "Failed to initiate document deletion: " + e.getMessage());
        }
    }

    /**
     * Starts the async ingestion pipeline for a document.
     * 
     * @param documentId the document ID to process
     */
    @Async
    public void startIngestionPipeline(UUID documentId) {
        log.info("Starting ingestion pipeline for document: id={}", documentId);
        
        // TODO: Implement actual ingestion pipeline
        // This would typically involve:
        // 1. Update status to PARSING
        // 2. Parse document using Unstructured API
        // 3. Update status to CHUNKING
        // 4. Split into chunks
        // 5. Update status to EMBEDDING
        // 6. Generate embeddings
        // 7. Update status to INDEXING
        // 8. Store in Elasticsearch
        // 9. Update status to COMPLETED
        
        // For now, just log the pipeline start
        log.info("Ingestion pipeline queued for document: id={}", documentId);
    }

    /**
     * Starts the async deletion pipeline for a document.
     * 
     * @param documentId the document ID to delete
     */
    @Async
    public void startDeletionPipeline(UUID documentId) {
        log.info("Starting deletion pipeline for document: id={}", documentId);
        
        // TODO: Implement actual deletion pipeline
        // This would typically involve:
        // 1. Delete from Elasticsearch
        // 2. Delete chunks from PostgreSQL
        // 3. Delete file from MinIO
        // 4. Delete SourceDocument record
        
        // For now, just log the pipeline start
        log.info("Deletion pipeline queued for document: id={}", documentId);
    }

    /**
     * Validates the uploaded file.
     */
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "File cannot be empty");
        }

        if (file.getSize() > 100 * 1024 * 1024) { // 100MB
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, 
                    "File size exceeds maximum limit of 100MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !SUPPORTED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, 
                    "Unsupported file type. Supported types: PDF, Markdown, and plain text files.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Filename cannot be empty");
        }

        log.debug("File validation passed: filename={}", filename);
    }

    /**
     * Calculates SHA-256 checksum of the file content.
     */
    private String calculateFileChecksum(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = file.getBytes();
            byte[] hashBytes = digest.digest(fileBytes);
            
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            
            return sb.toString();
            
        } catch (NoSuchAlgorithmException | IOException e) {
            log.error("Failed to calculate file checksum", e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, 
                    "Failed to calculate file checksum: " + e.getMessage());
        }
    }

    /**
     * Determines file type from content type.
     */
    private String determineFileType(String contentType) {
        return switch (contentType) {
            case "application/pdf" -> "PDF";
            case "text/markdown" -> "MARKDOWN";
            case "text/plain" -> "TEXT";
            default -> "UNKNOWN";
        };
    }

    /**
     * Converts SourceDocument entity to DTO.
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
}
