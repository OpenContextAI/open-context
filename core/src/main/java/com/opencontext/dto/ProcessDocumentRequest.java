package com.opencontext.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for RAG service document processing endpoint.
 * Sent from core/ to rag/ FastAPI service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessDocumentRequest {

    /**
     * Document UUID
     */
    private String documentId;

    /**
     * Presigned MinIO URL for downloading the file
     */
    private String fileUrl;

    /**
     * Original filename
     */
    private String filename;

    /**
     * File type (PDF, MARKDOWN, TEXT)
     */
    private String fileType;
}
