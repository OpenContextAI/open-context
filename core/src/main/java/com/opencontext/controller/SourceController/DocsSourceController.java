package com.opencontext.controller.SourceController;

import com.opencontext.common.CommonResponse;
import com.opencontext.common.PageResponse;
import com.opencontext.dto.SourceDocumentDto;
import com.opencontext.dto.SourceUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Interface defining source document management API endpoints with comprehensive Swagger documentation.
 * 
 * This interface serves as the contract for document ingestion pipeline management APIs
 * with detailed API documentation including examples and response schemas.
 */
@Tag(
    name = "Source Document Management", 
    description = "Admin APIs for document ingestion pipeline management. Requires X-API-KEY authentication."
)
@SecurityRequirement(name = "X-API-KEY")
public interface DocsSourceController {

    /**
     * Uploads a file and starts the ingestion pipeline.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Upload file and start ingestion pipeline",
        description = """
            Uploads a PDF or Markdown file to the system and starts the asynchronous ingestion pipeline.
            
            **Authentication Required:** X-API-KEY header must be provided.
            
            **Supported File Types:**
            - PDF documents (application/pdf)
            - Markdown files (text/markdown)
            - Plain text files (text/plain)
            
            **File Size Limit:** 100MB
            
            The API accepts the file upload request immediately and returns 202 Accepted status,
            indicating that the actual processing will be performed in the background.
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Multipart form data containing the file to upload",
            required = true,
            content = @Content(
                mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                examples = @ExampleObject(
                    name = "File Upload",
                    description = "Upload a PDF document",
                    value = "file: [binary PDF content]"
                )
            )
        )
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "202", 
            description = "File uploaded successfully and queued for ingestion",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SourceUploadResponse.class),
                examples = @ExampleObject(
                    name = "Upload Success",
                    value = """
                        {
                            "success": true,
                            "data": {
                                "sourceDocumentId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
                                "originalFilename": "document.pdf",
                                "ingestionStatus": "PENDING",
                                "message": "File uploaded successfully and is now pending for ingestion."
                            },
                            "message": "요청이 성공적으로 처리되었습니다.",
                            "errorCode": null,
                            "timestamp": "2025-08-07T11:50:00Z"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400", 
            description = "Bad Request - file part missing or validation failed",
            content = @Content(
                examples = @ExampleObject(
                    name = "Validation Failed",
                    value = """
                        {
                            "success": false,
                            "data": null,
                            "message": "File cannot be empty",
                            "errorCode": "COMMON_002",
                            "timestamp": "2025-08-07T11:50:00Z"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "403", 
            description = "Forbidden - invalid or missing API Key",
            content = @Content(
                examples = @ExampleObject(
                    name = "Invalid API Key",
                    value = """
                        {
                            "success": false,
                            "data": null,
                            "message": "Invalid API Key provided.",
                            "errorCode": "AUTH_001",
                            "timestamp": "2025-08-07T11:50:00Z"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "409", 
            description = "Conflict - duplicate file already exists",
            content = @Content(
                examples = @ExampleObject(
                    name = "Duplicate File",
                    value = """
                        {
                            "success": false,
                            "data": null,
                            "message": "A file with identical content already exists.",
                            "errorCode": "DOC_002",
                            "timestamp": "2025-08-07T11:50:00Z"
                        }
                        """
                )
            )
        ),
        @ApiResponse(responseCode = "413", description = "Payload Too Large - file exceeds 100MB limit"),
        @ApiResponse(responseCode = "415", description = "Unsupported Media Type - invalid file format")
    })
    ResponseEntity<CommonResponse<SourceUploadResponse>> uploadFile(
            @Parameter(description = "File to upload (PDF, Markdown, or plain text)", required = true)
            @RequestParam("file") @NotNull MultipartFile file
    );

    /**
     * Retrieves all uploaded documents with their current status.
     */
    @GetMapping
    @Operation(
        summary = "Get all source documents",
        description = """
            Retrieves a paginated list of all source documents in the system with their current ingestion status.
            
            **Authentication Required:** X-API-KEY header must be provided.
            
            This endpoint is primarily used by the Admin UI dashboard to periodically refresh and display
            the processing status of uploaded documents.
            """
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "Source documents retrieved successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(
                    name = "Document List",
                    value = """
                        {
                            "success": true,
                            "data": {
                                "content": [
                                    {
                                        "id": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
                                        "originalFilename": "spring-security-reference.pdf",
                                        "fileType": "PDF",
                                        "fileSize": 10485760,
                                        "ingestionStatus": "COMPLETED",
                                        "errorMessage": null,
                                        "lastIngestedAt": "2025-08-07T12:00:00Z",
                                        "createdAt": "2025-08-07T11:50:00Z",
                                        "updatedAt": "2025-08-07T12:00:00Z"
                                    }
                                ],
                                "page": 0,
                                "size": 10,
                                "totalElements": 48,
                                "totalPages": 5,
                                "first": true,
                                "last": false,
                                "hasNext": true,
                                "hasPrevious": false
                            },
                            "message": "요청이 성공적으로 처리되었습니다.",
                            "timestamp": "2025-08-07T12:05:00Z"
                        }
                        """
                )
            )
        ),
        @ApiResponse(responseCode = "403", description = "Forbidden - invalid or missing API Key")
    })
    ResponseEntity<CommonResponse<PageResponse<SourceDocumentDto>>> getAllSourceDocuments(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size,
            
            @Parameter(description = "Sort specification", example = "createdAt,desc")
            @RequestParam(defaultValue = "createdAt,desc") String sort
    );

    /**
     * Triggers re-ingestion of a specific document.
     */
    @PostMapping("/{sourceId}/resync")
    @Operation(
        summary = "Re-ingest a source document",
        description = """
            Forces re-ingestion of a specific source document by restarting the ingestion pipeline.
            
            **Authentication Required:** X-API-KEY header must be provided.
            
            The actual re-processing is performed asynchronously in the background,
            so this endpoint returns 202 Accepted to indicate the request has been queued.
            """
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "202", 
            description = "Re-ingestion request accepted and queued",
            content = @Content(
                examples = @ExampleObject(
                    name = "Resync Accepted",
                    value = """
                        {
                            "success": true,
                            "data": "Document re-ingestion has been queued successfully.",
                            "message": "요청이 성공적으로 처리되었습니다.",
                            "timestamp": "2025-08-07T12:05:00Z"
                        }
                        """
                )
            )
        ),
        @ApiResponse(responseCode = "403", description = "Forbidden - invalid or missing API Key"),
        @ApiResponse(responseCode = "404", description = "Not Found - document does not exist"),
        @ApiResponse(responseCode = "409", description = "Conflict - document is currently being processed")
    })
    ResponseEntity<CommonResponse<String>> resyncSourceDocument(
            @Parameter(description = "Source document ID", required = true)
            @PathVariable UUID sourceId
    );

    /**
     * Deletes a source document and all associated data.
     */
    @DeleteMapping("/{sourceId}")
    @Operation(
        summary = "Delete a source document",
        description = """
            Permanently deletes a source document and all its associated data from the system.
            
            **Authentication Required:** X-API-KEY header must be provided.
            
            This operation removes:
            - The original file from MinIO storage
            - All generated chunks from PostgreSQL
            - All embeddings and indices from Elasticsearch
            - The source document metadata
            
            The actual deletion is performed asynchronously in the background,
            so this endpoint returns 202 Accepted to indicate the request has been queued.
            """
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "202", 
            description = "Deletion request accepted and queued",
            content = @Content(
                examples = @ExampleObject(
                    name = "Deletion Accepted",
                    value = """
                        {
                            "success": true,
                            "data": "Document deletion has been queued successfully.",
                            "message": "요청이 성공적으로 처리되었습니다.",
                            "timestamp": "2025-08-07T12:05:00Z"
                        }
                        """
                )
            )
        ),
        @ApiResponse(responseCode = "403", description = "Forbidden - invalid or missing API Key"),
        @ApiResponse(responseCode = "404", description = "Not Found - document does not exist"),
        @ApiResponse(responseCode = "409", description = "Conflict - document is currently being processed")
    })
    ResponseEntity<CommonResponse<String>> deleteSourceDocument(
            @Parameter(description = "Source document ID", required = true)
            @PathVariable UUID sourceId
    );
}