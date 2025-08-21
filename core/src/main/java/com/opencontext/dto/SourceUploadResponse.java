package com.opencontext.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for source document upload operations.
 * 
 * This response is returned when a document is successfully uploaded
 * and queued for ingestion processing, following PRD specifications.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceUploadResponse {

    /**
     * UUID of the created SourceDocument entity.
     */
    private String sourceDocumentId;

    /**
     * Original filename as provided by the client.
     */
    private String originalFilename;

    /**
     * Current ingestion status (always PENDING for new uploads).
     */
    private String ingestionStatus;

    /**
     * Message indicating the upload result.
     */
    private String message;

    /**
     * Timestamp when the upload was processed.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime timestamp;

    /**
     * Creates a successful upload response.
     */
    public static SourceUploadResponse success(String sourceDocumentId, String originalFilename) {
        return SourceUploadResponse.builder()
                .sourceDocumentId(sourceDocumentId)
                .originalFilename(originalFilename)
                .ingestionStatus("PENDING")
                .message("File uploaded successfully and is now pending for ingestion.")
                .timestamp(LocalDateTime.now())
                .build();
    }
}
