package com.opencontext.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for source document information returned in list operations.
 * 
 * Contains essential information about source documents for admin UI display.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceDocumentDto {

    /**
     * Unique identifier of the source document.
     */
    private String id;

    /**
     * Original filename when uploaded.
     */
    private String originalFilename;

    /**
     * File type (PDF, MARKDOWN, etc.).
     */
    private String fileType;

    /**
     * File size in bytes.
     */
    private Long fileSize;

    /**
     * Current ingestion status.
     */
    private String ingestionStatus;

    /**
     * Error message if ingestion failed.
     */
    private String errorMessage;

    /**
     * Timestamp when document was last successfully ingested.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime lastIngestedAt;

    /**
     * Timestamp when document was created.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime createdAt;

    /**
     * Timestamp when document was last updated.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime updatedAt;
}
