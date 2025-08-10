package com.opencontext.core.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents the current status of the document ingestion pipeline.
 * This enum defines all possible states that a source document can be in
 * during its processing lifecycle from upload to completion.
 */
@Getter
@RequiredArgsConstructor
public enum IngestionStatus {
    /**
     * Initial state after user upload, waiting for processing to begin.
     * The system will soon start processing this document.
     */
    PENDING("Processing pending"),

    /**
     * Currently analyzing document structure and content via Unstructured API.
     * This step may take time depending on document complexity.
     */
    PARSING("Document parsing in progress"),

    /**
     * Splitting parsed content into meaningful hierarchical chunks.
     * This step creates the logical structure of the document.
     */
    CHUNKING("Content chunking in progress"),

    /**
     * Converting each chunk into vector embeddings using embedding model.
     * This step creates semantic data for similarity search.
     */
    EMBEDDING("Semantic vector generation in progress"),

    /**
     * Storing vectors and metadata in Elasticsearch, hierarchy in PostgreSQL.
     * Final data storage step to make content searchable.
     */
    INDEXING("Search data storage in progress"),

    /**
     * All ingestion processes completed successfully, content is searchable.
     * Users can now search for content from this document.
     */
    COMPLETED("Completed"),

    /**
     * Unrecoverable error occurred during ingestion process.
     * Details are recorded in the 'error_message' column.
     */
    ERROR("Error occurred"),

    /**
     * Document deletion in progress, removing all related data.
     * No other operations can be performed during this state.
     */
    DELETING("Deletion in progress");

    private final String description;
}