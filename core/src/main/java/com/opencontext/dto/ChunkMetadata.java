package com.opencontext.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Hierarchical and contextual metadata for document chunks.
 * Supports document structure navigation and search result organization.
 */
@Schema(description = "Hierarchical and contextual metadata for document chunks")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ChunkMetadata {

    /**
     * Title or heading of this chunk.
     * Used for displaying chunk context to users in search results.
     */
    @Schema(description = "Title or heading of the chunk", example = "5.8.2. JWT Authentication Converter")
    private String title;

    /**
     * Hierarchical breadcrumbs from document root to this chunk.
     * Stored as array for flexibility in UI display and filtering.
     * ES keyword type naturally supports arrays.
     */
    @Schema(description = "Breadcrumb path from root to current chunk", 
            example = "[\"Chapter 5\", \"Security\", \"JWT\", \"Configuration\"]")
    private List<String> breadcrumbs;

    /**
     * Depth level in document hierarchy (0 for root chunks).
     * Used for hierarchy-aware search and display organization.
     */
    @Schema(description = "Hierarchical depth level (0 for root)", example = "2")
    @PositiveOrZero
    private Integer hierarchyLevel;

    /**
     * Sequential order within document structure.
     * Maintains original document flow for coherent retrieval.
     */
    @Schema(description = "Sequential position in document", example = "15")
    @PositiveOrZero
    private Integer sequenceInDocument;

    /**
     * Document language for proper text processing.
     * Currently supports Korean (ko) and English (en).
     */
    @Schema(description = "Document language code", example = "ko")
    private String language;

    /**
     * Source file type for context and processing hints.
     */
    @Schema(description = "Source file type", example = "PDF")
    private String fileType;
}