    package com.opencontext.service;

    import com.opencontext.config.MinIOConfig;
    import com.opencontext.dto.SourceDocumentDto;
    import com.opencontext.entity.SourceDocument;
    import com.opencontext.enums.ErrorCode;
    import com.opencontext.enums.IngestionStatus;
    import com.opencontext.exception.BusinessException;
    import com.opencontext.repository.DocumentChunkRepository;
    import com.opencontext.repository.SourceDocumentRepository;
    import io.minio.*;
    import lombok.RequiredArgsConstructor;
    import lombok.extern.slf4j.Slf4j;
    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.data.domain.Page;
    import org.springframework.data.domain.Pageable;
    import org.springframework.http.*;
    import org.springframework.stereotype.Service;
    import org.springframework.transaction.annotation.Transactional;
    import org.springframework.web.client.RestTemplate;
    import org.springframework.web.multipart.MultipartFile;

    import java.io.IOException;
    import java.io.InputStream;
    import java.security.MessageDigest;
    import java.security.NoSuchAlgorithmException;
    import java.time.LocalDateTime;
    import java.util.List;
    import java.util.UUID;

    /**
     * Service for handling file storage operations with MinIO and document metadata management.
     * 
     * This service provides comprehensive methods for storing and managing
     * files in MinIO object storage along with their metadata in PostgreSQL
     * for the document ingestion pipeline.
     */
    @Slf4j
    @Service
    @RequiredArgsConstructor
    @Transactional
    public class FileStorageService {

        private final MinioClient minioClient;
        private final MinIOConfig minioConfig;
        private final SourceDocumentRepository sourceDocumentRepository;
        private final DocumentChunkRepository documentChunkRepository;
        private final RestTemplate restTemplate;

        @Value("${app.elasticsearch.url:http://elasticsearch:9200}")
        private String elasticsearchUrl;

        @Value("${app.elasticsearch.index:document_chunks_index}")
        private String indexName;
        
        // Supported file types for document processing (canonical)
        private static final List<String> ALLOWED_CANONICAL_CONTENT_TYPES = List.of(
                "application/pdf",
                "text/markdown",
                "text/plain"
        );

        /**
         * Uploads a file to MinIO storage, creates document metadata, and returns the document.
         * 
         * @param file the multipart file to upload
         * @return the created SourceDocument entity
         */
        public SourceDocument uploadFileWithMetadata(MultipartFile file) {
            String filename = file.getOriginalFilename();
            long fileSize = file.getSize();
            String contentType = resolveContentType(file);
            
            log.info("[UPLOAD] Starting file upload with metadata: filename={}, size={} bytes, contentType={}", 
                    filename, fileSize, contentType);
            
            long startTime = System.currentTimeMillis();

            // Validate file
            log.debug(" [UPLOAD] Step 1/5: Validating file: {}", filename);
            validateFile(file);
            log.info("[UPLOAD] File validation completed successfully: {}", filename);

            // Calculate file checksum to prevent duplicates
            log.debug("[UPLOAD] Step 2/5: Calculating file checksum: {}", filename);
            String fileChecksum = calculateFileChecksum(file);
            log.info("[UPLOAD] File checksum calculated: {} -> {}", filename, fileChecksum);
            
            // Check for duplicate files
            log.debug("[UPLOAD] Step 3/5: Checking for duplicate files: {}", filename);
            if (sourceDocumentRepository.existsByFileChecksum(fileChecksum)) {
                log.warn("[UPLOAD] Duplicate file upload attempt: filename={}, checksum={}", filename, fileChecksum);
                throw new BusinessException(ErrorCode.DUPLICATE_FILE_UPLOADED, 
                        "A file with identical content already exists.");
            }
            log.info("[UPLOAD] No duplicate files found: {}", filename);

            try {
                // Upload file to MinIO
                log.debug("[UPLOAD] Step 4/5: Uploading file to MinIO: {}", filename);
                String objectKey = uploadFile(file, contentType);
                log.info("[UPLOAD] File uploaded to MinIO successfully: {} -> {}", filename, objectKey);

                // Create SourceDocument entity
                log.debug("[UPLOAD] Step 5/5: Creating database record: {}", filename);
                SourceDocument sourceDocument = SourceDocument.builder()
                        .originalFilename(file.getOriginalFilename())
                        .fileStoragePath(objectKey)
                        .fileType(determineFileType(contentType))
                        .fileSize(file.getSize())
                        .fileChecksum(fileChecksum)
                        .ingestionStatus(IngestionStatus.PENDING)
                        .build();

                // Save to database
                SourceDocument savedDocument = sourceDocumentRepository.save(sourceDocument);
                
                long duration = System.currentTimeMillis() - startTime;
                log.info(" [UPLOAD] File upload completed successfully: id={}, filename={}, duration={}ms", 
                        savedDocument.getId(), savedDocument.getOriginalFilename(), duration);

                return savedDocument;

            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                log.error("[UPLOAD] File upload failed: filename={}, duration={}ms, error={}", 
                        filename, duration, e.getMessage(), e);
                if (e instanceof BusinessException) {
                    throw e;
                }
                throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, 
                        "Unexpected error during file upload: " + e.getMessage());
            }
        }

        /**
         * Uploads a file to MinIO storage and returns the object key.
         * 
         * @param file the multipart file to upload
         * @return the object key where the file was stored
         */
        public String uploadFile(MultipartFile file, String resolvedContentType) {
            try {
                // Ensure bucket exists
                ensureBucketExists();

                // Generate unique object key
                String objectKey = generateObjectKey(file.getOriginalFilename());

                // Upload file to MinIO
                PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(objectKey)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(resolvedContentType)
                        .build();

                minioClient.putObject(putObjectArgs);
                
                log.debug("[MINIO] File uploaded to MinIO: {} -> bucket:{}, key:{}", 
                        file.getOriginalFilename(), minioConfig.getBucketName(), objectKey);

                return objectKey;

            } catch (Exception e) {
                log.error("[MINIO] MinIO upload failed: filename={}, error={}", 
                        file.getOriginalFilename(), e.getMessage(), e);
                throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, 
                        "Failed to upload file to storage: " + e.getMessage());
            }
        }

        /**
         * Downloads a file from MinIO storage.
         * 
         * @param objectKey the object key of the file to download
         * @return InputStream of the file content
         */
        public InputStream downloadFile(String objectKey) {
            try {
                GetObjectArgs getObjectArgs = GetObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(objectKey)
                        .build();

                InputStream stream = minioClient.getObject(getObjectArgs);
                log.debug("[MINIO] File downloaded successfully: key={}", objectKey);
                return stream;

            } catch (Exception e) {
                log.error("[MINIO] File download failed: key={}, error={}", objectKey, e.getMessage(), e);
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND, 
                        "Failed to download file: " + e.getMessage());
            }
        }

        /**
         * Deletes a file from MinIO storage.
         * 
         * @param objectKey the object key of the file to delete
         */
        public void deleteFile(String objectKey) {
            try {
                RemoveObjectArgs removeObjectArgs = RemoveObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(objectKey)
                        .build();

                minioClient.removeObject(removeObjectArgs);
                log.debug("[MINIO] File deleted successfully: key={}", objectKey);

            } catch (Exception e) {
                log.error("[MINIO] File deletion failed: key={}, error={}", objectKey, e.getMessage(), e);
                throw new BusinessException(ErrorCode.FILE_DELETE_FAILED, 
                        "Failed to delete file: " + e.getMessage());
            }
        }

        /**
         * Checks if a file exists in MinIO storage.
         * 
         * @param objectKey the object key to check
         * @return true if file exists, false otherwise
         */
        public boolean fileExists(String objectKey) {
            try {
                StatObjectArgs statObjectArgs = StatObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(objectKey)
                        .build();

                minioClient.statObject(statObjectArgs);
                return true;

            } catch (Exception e) {
                return false;
            }
        }

        /**
         * Ensures that the configured bucket exists, creating it if necessary.
         */
        private void ensureBucketExists() {
            try {
                BucketExistsArgs bucketExistsArgs = BucketExistsArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .build();

                boolean bucketExists = minioClient.bucketExists(bucketExistsArgs);
                
                if (!bucketExists) {
                    MakeBucketArgs makeBucketArgs = MakeBucketArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .build();
                    
                    minioClient.makeBucket(makeBucketArgs);
                    log.info(" [MINIO] Created MinIO bucket: {}", minioConfig.getBucketName());
                }

            } catch (Exception e) {
                log.error("[MINIO] Failed to ensure bucket exists: bucket={}, error={}", 
                        minioConfig.getBucketName(), e.getMessage(), e);
                throw new BusinessException(ErrorCode.STORAGE_ERROR, 
                        "Failed to ensure bucket exists: " + e.getMessage());
            }
        }

        /**
         * Generates an object key for MinIO storage.
         * 
         * @param originalFilename the original filename
         * @return generated object key
         */
        private String generateObjectKey(String originalFilename) {
            LocalDateTime now = LocalDateTime.now();
            String uuid = UUID.randomUUID().toString().substring(0, 8);
            String timestamp = String.valueOf(System.currentTimeMillis());
            
            String filename = String.format("%s_%s_%s", timestamp, uuid, originalFilename);
            
            return String.format("documents/%d/%02d/%02d/%s", 
                    now.getYear(), now.getMonthValue(), now.getDayOfMonth(), filename);
        }

        // ========== Document Metadata Management Methods ==========

        /**
         * Retrieves a source document by ID.
         * 
         * @param documentId the document ID
         * @return SourceDocument entity
         */
        @Transactional(readOnly = true)
        public SourceDocument getDocument(UUID documentId) {
            log.debug(" [QUERY] Retrieving document: id={}", documentId);
            
            return sourceDocumentRepository.findById(documentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SOURCE_DOCUMENT_NOT_FOUND, 
                            "Document not found: " + documentId));
        }

        /**
         * Retrieves all source documents with pagination.
         * 
         * @param pageable pagination parameters
         * @return Page of SourceDocumentDto
         */
        @Transactional(readOnly = true)
        public Page<SourceDocumentDto> getAllDocuments(Pageable pageable) {
            log.debug(" [QUERY] Retrieving documents with pagination: page={}, size={}", 
                    pageable.getPageNumber(), pageable.getPageSize());

            return sourceDocumentRepository.findAllByOrderByCreatedAtDesc(pageable)
                    .map(this::convertToDto);
        }

        /**
         * Updates document status.
         * 
         * @param documentId the document ID
         * @param status the new ingestion status
         */
        public void updateDocumentStatus(UUID documentId, IngestionStatus status) {
            sourceDocumentRepository.findById(documentId)
                    .ifPresent(document -> {
                        document.updateIngestionStatus(status);
                        sourceDocumentRepository.save(document);
                        log.info(" [STATUS] Document status updated: id={}, status={}", documentId, status);
                    });
        }

        /**
         * Updates document status to COMPLETED and sets completion timestamp.
         * 
         * @param documentId the document ID
         */
        public void updateDocumentStatusToCompleted(UUID documentId) {
            sourceDocumentRepository.findById(documentId)
                    .ifPresent(document -> {
                        document.updateIngestionStatus(IngestionStatus.COMPLETED);
                        sourceDocumentRepository.save(document);
                        log.info(" [STATUS] Document status updated to COMPLETED: id={}", documentId);
                    });
        }

        /**
         * Updates document status to ERROR with error message.
         * 
         * @param documentId the document ID
         * @param errorMessage the error message
         */
        public void updateDocumentStatusToError(UUID documentId, String errorMessage) {
            try {
                sourceDocumentRepository.findById(documentId)
                        .ifPresent(document -> {
                            document.updateIngestionStatusToError(errorMessage);
                            sourceDocumentRepository.save(document);
                            log.error("[STATUS] Document status updated to ERROR: id={}, errorMessage={}", documentId, errorMessage);
                        });
            } catch (Exception e) {
                log.error("[STATUS] Failed to update document status to ERROR: id={}, error={}", 
                        documentId, e.getMessage(), e);
            }
        }

        /**
         * Deletes a document and all its associated data from all storage systems.
         * This includes MinIO files, PostgreSQL records, and Elasticsearch indices.
         * 
         * @param documentId the document ID to delete
         */
        @Transactional
        public void deleteDocument(UUID documentId) {
            long startTime = System.currentTimeMillis();
            log.info("[DELETE] Starting comprehensive document deletion: id={}", documentId);

            SourceDocument document = getDocument(documentId);
            String filename = document.getOriginalFilename();
            IngestionStatus status = document.getIngestionStatus();
            
            log.info(" [DELETE] Document details: filename={}, status={}, size={} bytes", 
                    filename, status, document.getFileSize());

            // Check if document is currently being processed (but allow DELETING status)
            if (document.isProcessing() && status != IngestionStatus.DELETING) {
                log.warn(" [DELETE] Cannot delete document in processing state: id={}, status={}", 
                        documentId, status);
                throw new BusinessException(ErrorCode.RESOURCE_IS_BEING_PROCESSED, 
                        "Document is currently being processed and cannot be deleted.");
            }

            // Update status to DELETING (only if not already DELETING)
            if (status != IngestionStatus.DELETING) {
                log.debug(" [DELETE] Step 1/4: Updating status to DELETING: {}", filename);
                document.updateIngestionStatus(IngestionStatus.DELETING);
                sourceDocumentRepository.save(document);
                log.info(" [DELETE] Status updated to DELETING: {}", filename);
            } else {
                log.info(" [DELETE] Document already in DELETING status: {}", filename);
            }

            try {
                // Step 2: Delete from Elasticsearch (if exists)
                log.debug(" [DELETE] Step 2/4: Deleting from Elasticsearch: {}", filename);
                deleteFromElasticsearch(documentId);
                log.info("[DELETE] Elasticsearch deletion completed: {}", filename);

                // Step 3: Delete chunks from PostgreSQL
                log.debug("[DELETE] Step 3/4: Deleting chunks from PostgreSQL: {}", filename);
                int deletedChunks = deleteChunksFromPostgreSQL(documentId);
                log.info(" [DELETE] PostgreSQL chunks deleted: {} chunks removed for {}", deletedChunks, filename);

                // Step 4: Delete file from MinIO
                log.debug("[DELETE] Step 4/4: Deleting file from MinIO: {}", filename);
                deleteFile(document.getFileStoragePath());
                log.info("[DELETE] MinIO file deleted: {} -> {}", filename, document.getFileStoragePath());

                // Final step: Delete SourceDocument record
                log.debug("[DELETE] Final step: Deleting source document record: {}", filename);
                sourceDocumentRepository.delete(document);
                log.info("[DELETE] Source document record deleted: {}", filename);

                long duration = System.currentTimeMillis() - startTime;
                log.info("[DELETE] Document deletion completed successfully: id={}, filename={}, duration={}ms", 
                        documentId, filename, duration);

            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                log.error("[DELETE] Document deletion failed: id={}, filename={}, duration={}ms, error={}", 
                        documentId, filename, duration, e.getMessage(), e);
                
                // Try to revert status if possible
                try {
                    SourceDocument updatedDoc = sourceDocumentRepository.findById(documentId).orElse(null);
                    if (updatedDoc != null) {
                        updatedDoc.updateIngestionStatusToError("Deletion failed: " + e.getMessage());
                        sourceDocumentRepository.save(updatedDoc);
                        log.info("[DELETE] Status reverted to ERROR after deletion failure: {}", filename);
                    }
                } catch (Exception revertEx) {
                    log.error("[DELETE] Failed to revert status after deletion failure: {}", filename, revertEx);
                }
                
                throw new BusinessException(ErrorCode.DELETION_PIPELINE_FAILED, 
                        "Failed to delete document: " + e.getMessage());
            }
        }

        /**
         * Checks if a document is currently being processed.
         * 
         * @param documentId the document ID
         * @return true if document is in processing state
         */
        @Transactional(readOnly = true)
        public boolean isDocumentProcessing(UUID documentId) {
            return sourceDocumentRepository.findById(documentId)
                    .map(SourceDocument::isProcessing)
                    .orElse(false);
        }

        /**
         * Gets the file storage path for a document.
         * 
         * @param documentId the document ID
         * @return the file storage path
         */
        @Transactional(readOnly = true)
        public String getDocumentStoragePath(UUID documentId) {
            return sourceDocumentRepository.findById(documentId)
                    .map(SourceDocument::getFileStoragePath)
                    .orElse(null);
        }

        // ========== Deletion Helper Methods ==========

        /**
         * Deletes all chunks associated with a document from Elasticsearch.
         * Uses delete-by-query API to remove all chunks with matching document_id.
         * 
         * @param documentId the document ID whose chunks should be deleted
         */
        private void deleteFromElasticsearch(UUID documentId) {
            try {
                log.debug("[ELASTICSEARCH] Starting deletion for document: {}", documentId);
                
                // Create delete-by-query request
                String deleteQuery = String.format(
                        "{\"query\": {\"term\": {\"document_id\": \"%s\"}}}", 
                        documentId.toString()
                );
                
                log.debug(" [ELASTICSEARCH] Delete query: {}", deleteQuery);
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> requestEntity = new HttpEntity<>(deleteQuery, headers);
                
                String deleteUrl = String.format("%s/%s/_delete_by_query", elasticsearchUrl, indexName);
                log.debug(" [ELASTICSEARCH] Delete URL: {}", deleteUrl);
                
                ResponseEntity<String> response = restTemplate.exchange(
                        deleteUrl,
                        HttpMethod.POST,
                        requestEntity,
                        String.class
                );
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    String responseBody = response.getBody();
                    log.info(" [ELASTICSEARCH] Document chunks deleted successfully: documentId={}, response={}", 
                            documentId, responseBody);
                } else {
                    log.warn(" [ELASTICSEARCH] Delete operation returned non-2xx status: documentId={}, status={}, response={}", 
                            documentId, response.getStatusCode(), response.getBody());
                }
                
            } catch (Exception e) {
                // Log the error but don't fail the entire deletion process
                // This handles cases where the document was never indexed (ERROR status)
                if (e.getMessage() != null && e.getMessage().contains("index_not_found_exception")) {
                    log.info(" [ELASTICSEARCH] Index not found - document was likely never indexed: documentId={}", documentId);
                } else {
                    log.warn(" [ELASTICSEARCH] Failed to delete from Elasticsearch (continuing deletion): documentId={}, error={}", 
                            documentId, e.getMessage(), e);
                }
            }
        }
        
        /**
         * Deletes all chunks associated with a document from PostgreSQL.
         * 
         * @param documentId the document ID whose chunks should be deleted
         * @return the number of deleted chunks
         */
        private int deleteChunksFromPostgreSQL(UUID documentId) {
            try {
                log.debug(" [POSTGRESQL] Starting chunk deletion for document: {}", documentId);
                
                int deletedChunks = documentChunkRepository.deleteBySourceDocumentId(documentId);
                
                log.info(" [POSTGRESQL] Chunks deleted successfully: documentId={}, deletedCount={}", 
                        documentId, deletedChunks);
                
                return deletedChunks;
                
            } catch (Exception e) {
                log.error(" [POSTGRESQL] Failed to delete chunks: documentId={}, error={}", 
                        documentId, e.getMessage(), e);
                throw new BusinessException(ErrorCode.DATABASE_ERROR, 
                        "Failed to delete document chunks: " + e.getMessage());
            }
        }

        // ========== Private Helper Methods ==========

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

            String filename = file.getOriginalFilename();
            if (filename == null || filename.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Filename cannot be empty");
            }

            // Resolve canonical content type from both content-type header and filename extension
            String resolvedContentType = resolveContentType(file);
            
            // Check if the resolved content type is supported
            if (!ALLOWED_CANONICAL_CONTENT_TYPES.contains(resolvedContentType)) {
                log.debug(" [UPLOAD] Unsupported content type: filename={}, original={}, resolved={}", 
                        filename, file.getContentType(), resolvedContentType);
                throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                        "Unsupported file type. Supported types: PDF, Markdown, and plain text files.");
            }

            log.debug(" [UPLOAD] File validation passed: filename={}, resolved_type={}", filename, resolvedContentType);
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
                log.error(" [UPLOAD] Failed to calculate file checksum: filename={}, error={}", 
                        file.getOriginalFilename(), e.getMessage(), e);
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
         * Resolves the effective content type for the uploaded file.
         * If the inbound content type is null or generic (e.g., application/octet-stream),
         * infer from the filename extension and normalize to a canonical, allowed type.
         */
        private String resolveContentType(MultipartFile file) {
            String inbound = file.getContentType();
            // If clearly an allowed canonical type, return as-is
            if (ALLOWED_CANONICAL_CONTENT_TYPES.contains(inbound)) {
                return inbound;
            }

            // Try to infer from file extension for generic or alternate types
            String filename = file.getOriginalFilename();
            String ext = (filename == null) ? null : getFileExtension(filename).toLowerCase();

            if (ext != null) {
                return switch (ext) {
                    case "pdf" -> "application/pdf";
                    case "md", "markdown" -> "text/markdown";
                    case "txt" -> "text/plain";
                    default -> (inbound != null ? inbound : "application/octet-stream");
                };
            }

            // Fallback to inbound or generic
            return inbound != null ? inbound : "application/octet-stream";
        }

        /**
         * Returns the extension without the leading dot. Example: "README.md" -> "md".
         */
        private String getFileExtension(String filename) {
            int lastDot = filename.lastIndexOf('.');
            if (lastDot == -1 || lastDot == filename.length() - 1) {
                return "";
            }
            return filename.substring(lastDot + 1);
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