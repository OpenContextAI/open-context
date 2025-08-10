package com.opencontext.entity;

import com.opencontext.enums.IngestionStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing the master information of uploaded source files.
 * This entity tracks the metadata and processing state of each document
 * throughout the ingestion pipeline.
 */
@Entity
@Table(name = "source_documents")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SourceDocument {

    /**
     * Primary key - unique identifier for each source document.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    /**
     * Original filename when user uploaded the file.
     */
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    /**
     * Actual storage path within Object Storage (MinIO).
     * Used to access the original file when needed.
     */
    @Column(name = "file_storage_path", nullable = false, length = 1024)
    private String fileStoragePath;

    /**
     * Type of the uploaded file (e.g., PDF, MARKDOWN).
     */
    @Column(name = "file_type", nullable = false, length = 50)
    private String fileType;

    /**
     * File size in bytes.
     */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /**
     * SHA-256 hash of file content.
     * Used to prevent duplicate uploads and verify file integrity.
     */
    @Column(name = "file_checksum", nullable = false, length = 64, unique = true)
    private String fileChecksum;

    /**
     * Current status of the ingestion pipeline process.
     * Tracks the document through states: PENDING → PARSING → CHUNKING → EMBEDDING → INDEXING → COMPLETED.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "ingestion_status", nullable = false, length = 20)
    @Builder.Default
    private IngestionStatus ingestionStatus = IngestionStatus.PENDING;

    /**
     * Detailed error message when ingestion_status is ERROR.
     * Used for developer debugging and user feedback.
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Timestamp when this document was last successfully ingested.
     */
    @Column(name = "last_ingested_at")
    private LocalDateTime lastIngestedAt;

    /**
     * Timestamp when this record was first created in database.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when this record was last updated.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Updates the ingestion status and clears error message if successful.
     * 
     * @param newStatus the new status to set
     */
    public void updateIngestionStatus(IngestionStatus newStatus) {
        this.ingestionStatus = newStatus;
        if (newStatus == IngestionStatus.COMPLETED) {
            this.errorMessage = null;
            this.lastIngestedAt = LocalDateTime.now();
        }
    }

    /**
     * Updates the ingestion status to ERROR with a detailed error message.
     * 
     * @param errorMessage detailed error message for debugging
     */
    public void updateIngestionStatusToError(String errorMessage) {
        this.ingestionStatus = IngestionStatus.ERROR;
        this.errorMessage = errorMessage;
    }

    /**
     * Checks if the document is currently being processed.
     * 
     * @return true if document is in any processing state
     */
    public boolean isProcessing() {
        return ingestionStatus == IngestionStatus.PARSING
                || ingestionStatus == IngestionStatus.CHUNKING
                || ingestionStatus == IngestionStatus.EMBEDDING
                || ingestionStatus == IngestionStatus.INDEXING
                || ingestionStatus == IngestionStatus.DELETING;
    }

    /**
     * Checks if the document processing has completed successfully.
     * 
     * @return true if ingestion status is COMPLETED
     */
    public boolean isCompleted() {
        return ingestionStatus == IngestionStatus.COMPLETED;
    }

    /**
     * Checks if the document processing has failed with an error.
     * 
     * @return true if ingestion status is ERROR
     */
    public boolean hasError() {
        return ingestionStatus == IngestionStatus.ERROR;
    }
}