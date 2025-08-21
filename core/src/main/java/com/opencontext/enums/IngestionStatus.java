package com.opencontext.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enum representing the progress states of the document ingestion pipeline.
 * Each state clearly indicates how documents are being processed in the system.
 */
@Getter
@RequiredArgsConstructor
public enum IngestionStatus {
    /**
     * Initial state waiting for processing after user upload request.
     * The system will start processing soon.
     */
    PENDING("Waiting for processing"),

    /**
     * Analyzing file text and structure through Unstructured API.
     * This step may take time depending on file complexity.
     */
    PARSING("Analyzing document structure"),

    /**
     * Splitting parsed content into hierarchical chunks by semantic units.
     * This is the stage where the document's logical structure is created.
     */
    CHUNKING("Creating content chunks"),

    /**
     * Converting each chunk to vectors through embedding model.
     * This is the stage where core data for semantic search is generated.
     */
    EMBEDDING("Generating semantic vectors"),

    /**
     * Storing vectors and metadata in Elasticsearch, hierarchy info in PostgreSQL.
     * This is the final data storage step to make content searchable.
     */
    INDEXING("Storing search data"),

    /**
     * Final state where all ingestion processes are successfully completed and searchable.
     * Users can now search the content of this document.
     */
    COMPLETED("Ready for search"),

    /**
     * State where an unrecoverable error occurred during the ingestion process.
     * Details are recorded in the 'error_message' column.
     */
    ERROR("Processing failed"),

    /**
     * Removing all related data (MinIO, PostgreSQL, Elasticsearch) after deletion request.
     * No other operations can be performed during this state.
     */
    DELETING("Removing data");

    private final String description;
}

