package com.opencontext.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO from RAG service document processing endpoint.
 * Received by core/ from rag/ FastAPI service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessDocumentResponse {

    /**
     * Whether processing completed successfully
     */
    private boolean success;

    /**
     * Document UUID
     */
    private String documentId;

    /**
     * Number of chunks created and indexed
     */
    private int chunksProcessed;

    /**
     * Processing status (COMPLETED or ERROR)
     */
    private String status;

    /**
     * Error message if processing failed
     */
    private String errorMessage;
}
