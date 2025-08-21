package com.opencontext.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Response DTO for the get_content API endpoint.
 * Provides complete chunk content with token information for LLM context management.
 */
@Schema(description = "Response for focused content retrieval (get_content)")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class GetContentResponse {

    /**
     * Complete text content of the requested chunk.
     * May be truncated if exceeds maxTokens limit, but still returns 200 OK.
     */
    @Schema(description = "Complete chunk content (may be token-limited)", 
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String content;

    /**
     * Token processing information for LLM context management.
     */
    @Schema(description = "Token processing information", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private TokenInfo tokenInfo;
}