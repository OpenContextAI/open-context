package com.opencontext.repository;

import com.opencontext.entity.SourceDocument;
import com.opencontext.enums.IngestionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for SourceDocument entity operations.
 * Provides data access methods for document metadata management
 * and ingestion pipeline status tracking.
 */
@Repository
public interface SourceDocumentRepository extends JpaRepository<SourceDocument, UUID> {

    /**
     * Finds a document by its file checksum to prevent duplicate uploads.
     * 
     * @param fileChecksum SHA-256 hash of the file content
     * @return Optional containing the document if found
     */
    Optional<SourceDocument> findByFileChecksum(String fileChecksum);

    /**
     * Finds all documents with a specific ingestion status.
     * 
     * @param status the ingestion status to filter by
     * @return list of documents with the specified status
     */
    List<SourceDocument> findByIngestionStatus(IngestionStatus status);

    /**
     * Finds all documents with a specific ingestion status with pagination.
     * 
     * @param status the ingestion status to filter by
     * @param pageable pagination parameters
     * @return page of documents with the specified status
     */
    Page<SourceDocument> findByIngestionStatus(IngestionStatus status, Pageable pageable);

    /**
     * Finds all documents that have been successfully ingested.
     * 
     * @return list of completed documents
     */
    @Query("SELECT sd FROM SourceDocument sd WHERE sd.ingestionStatus = 'COMPLETED'")
    List<SourceDocument> findAllCompleted();

    /**
     * Finds all documents that failed during ingestion.
     * 
     * @return list of documents with errors
     */
    @Query("SELECT sd FROM SourceDocument sd WHERE sd.ingestionStatus = 'ERROR'")
    List<SourceDocument> findAllWithErrors();

    /**
     * Finds all documents that are currently being processed.
     * 
     * @return list of documents in processing states
     */
    @Query("SELECT sd FROM SourceDocument sd WHERE sd.ingestionStatus IN ('PARSING', 'CHUNKING', 'EMBEDDING', 'INDEXING')")
    List<SourceDocument> findAllProcessing();

    /**
     * Finds documents by filename pattern (case-insensitive).
     * 
     * @param filename the filename pattern to search for
     * @return list of documents matching the filename pattern
     */
    @Query("SELECT sd FROM SourceDocument sd WHERE LOWER(sd.originalFilename) LIKE LOWER(CONCAT('%', :filename, '%'))")
    List<SourceDocument> findByOriginalFilenameContainingIgnoreCase(@Param("filename") String filename);

    /**
     * Finds documents created within a specific time range.
     * 
     * @param startDate the start of the time range
     * @param endDate the end of the time range
     * @return list of documents created within the range
     */
    List<SourceDocument> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Finds documents that were last ingested within a specific time range.
     * 
     * @param startDate the start of the time range
     * @param endDate the end of the time range
     * @return list of documents last ingested within the range
     */
    List<SourceDocument> findByLastIngestedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Counts the total number of documents by ingestion status.
     * 
     * @param status the ingestion status to count
     * @return count of documents with the specified status
     */
    long countByIngestionStatus(IngestionStatus status);

    /**
     * Finds documents by file type (case-insensitive).
     * 
     * @param fileType the file type to filter by (e.g., "PDF", "MARKDOWN")
     * @return list of documents with the specified file type
     */
    List<SourceDocument> findByFileTypeIgnoreCase(String fileType);

    /**
     * Checks if a document with the given checksum already exists.
     * 
     * @param fileChecksum SHA-256 hash of the file content
     * @return true if a document with this checksum exists
     */
    boolean existsByFileChecksum(String fileChecksum);

    /**
     * Finds documents ordered by creation date (most recent first).
     * 
     * @param pageable pagination parameters
     * @return page of documents ordered by creation date
     */
    Page<SourceDocument> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Finds documents that have been processing for too long (potential stuck documents).
     * 
     * @param cutoffTime the time before which documents are considered stuck
     * @return list of potentially stuck documents
     */
    @Query("SELECT sd FROM SourceDocument sd WHERE sd.ingestionStatus IN ('PARSING', 'CHUNKING', 'EMBEDDING', 'INDEXING') AND sd.updatedAt < :cutoffTime")
    List<SourceDocument> findStuckProcessingDocuments(@Param("cutoffTime") LocalDateTime cutoffTime);
}