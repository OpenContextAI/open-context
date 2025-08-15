package com.opencontext.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "DTO to request full content of a specific chunk")
@Getter
@lombok.Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetContentRequest {

    @Schema(description = "Chunk ID to retrieve content for", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "a1b2c3d4-e5f6-7890-1234-567890abcdef-chunk-0")
    @NotBlank(message = "chunkId is required")
    @JsonAlias({"chunkID", "chunk_id", "id"})
    @JsonProperty("chunkId")
    private String chunkId;

    @Schema(description = "Maximum number of tokens to return", defaultValue = "25000", example = "8000")
    @Positive(message = "maxTokens must be positive")
    @JsonProperty("maxTokens")
    private Integer maxTokens;

    // Use default constructor + setters for robust Jackson binding
}
