package com.opencontext.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Enum defining all possible error codes in the system.
 * Each error code provides an HTTP status code along with machine-parsable code 
 * and human-readable message.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // --- COMMON ---
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_001", "Invalid request."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "COMMON_002", "Input validation failed."),
    UNKNOWN_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_003", "Unknown server error occurred."),

    // --- AUTHENTICATION ---
    INSUFFICIENT_PERMISSION(HttpStatus.FORBIDDEN, "AUTH_001", "Insufficient permission to perform this request."),

    // --- DOCUMENT ---
    SOURCE_DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "DOC_001", "Document with the specified ID not found."),
    DUPLICATE_FILE_UPLOADED(HttpStatus.CONFLICT, "DOC_002", "A file with identical content already exists."),
    RESOURCE_IS_BEING_PROCESSED(HttpStatus.CONFLICT, "DOC_003", "The document is currently being processed by another operation."),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "DOC_004", "File size exceeds the maximum upload limit."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "DOC_005", "Unsupported file format. Please upload PDF or Markdown files."),

    // --- CONTEXT/SEARCH ---
    TOKEN_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "CTX_001", "Requested content token count exceeds maximum limit."),
    CHUNK_NOT_FOUND(HttpStatus.NOT_FOUND, "CTX_002", "Chunk with the specified ID not found."),

    // --- FILE STORAGE ---
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_001", "Failed to upload file to storage."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "FILE_002", "Requested file not found in storage."),
    FILE_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_003", "Failed to delete file from storage."),
    STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_004", "Storage system error occurred."),
    FILE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "FILE_005", "Access denied to the requested file."),

    // --- INFRASTRUCTURE/EXTERNAL SERVICES ---
    INGESTION_PIPELINE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "INFRA_001", "Internal error occurred during document processing."),
    EXTERNAL_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "INFRA_002", "External service is not responding."),
    DB_CONNECTION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "INFRA_003", "Database connection failed."),
    ELASTICSEARCH_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "INFRA_004", "Search engine error occurred.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}

