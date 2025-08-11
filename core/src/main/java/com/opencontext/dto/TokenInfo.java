package com.opencontext.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Token processing information for LLM context management.
 */
@Schema(description = "Token processing information for LLM context management")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TokenInfo {

    /**
     * Name of the tokenizer used for token counting.
     * Currently fixed to tiktoken-cl100k_base but extensible for future LLM support.
     */
    @Schema(description = "Tokenizer used for counting", example = "tiktoken-cl100k_base", 
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String tokenizer;

    /**
     * Actual number of tokens in the returned content.
     * May be less than original if content was truncated due to maxTokens limit.
     * Can be 0 in extreme cases (empty content after processing).
     */
    @Schema(description = "Actual token count of returned content", example = "7999", 
            requiredMode = Schema.RequiredMode.REQUIRED)
    @PositiveOrZero
    private Integer actualTokens;
}