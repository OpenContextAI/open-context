package com.opencontext.controller;

import com.opencontext.common.CommonResponse;
import com.opencontext.dto.GetContentRequest;
import com.opencontext.dto.GetContentResponse;
import com.opencontext.dto.SearchResultsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "MCP Search", description = "APIs for exploratory and focused retrieval used by OpenContext MCP tools. No authentication required.")
public interface DocsSearchController {

    @GetMapping("/search")
    @Operation(
            summary = "Exploratory search (find_knowledge)",
            description = "Returns top-k structured chunk summaries based on a natural language query. Default returns 50 results. Snippet policy: first 50 chars + '...'."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search completed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SearchResultsResponse.class),
                            examples = @ExampleObject(name = "Sample Search Results", value = """
                            {
                              "success": true,
                              "data": {
                                "results": [
                                  {
                                    "chunkId": "c1d2e3f4-a5b6-7890-1234-567890abcdef",
                                    "title": "5.8.2. Configuring the JWT Authentication Converter",
                                    "snippet": "To customize the conversion from a JWT to an Auth...",
                                    "relevanceScore": 0.92,
                                    "breadcrumbs": ["Chapter 5", "Security", "JWT"]
                                  }
                                ]
                              },
                              "message": "Search completed successfully"
                            }
                            """))
            ),
            @ApiResponse(responseCode = "400", description = "Validation failed")
    })
    ResponseEntity<CommonResponse<SearchResultsResponse>> search(
            @Parameter(description = "User search query", example = "Spring Security JWT filter configuration", required = true)
            @RequestParam String query,
            @Parameter(description = "Max number of results", example = "50")
            @RequestParam(defaultValue = "50") Integer topK);

    @PostMapping(value = "/get-content", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Focused retrieval (get_content)",
            description = "Returns the full original text of a selected chunk. If token count exceeds maxTokens, text is truncated from the end."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Content retrieved",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = GetContentResponse.class),
                            examples = @ExampleObject(name = "Sample Content", value = """
                            {
                              "success": true,
                              "data": {
                                "content": "### 5.8.2. Configuring the JWT Authentication Converter...",
                                "tokenInfo": {"tokenizer": "tiktoken-cl100k_base", "actualTokens": 789}
                              },
                              "message": "Content retrieved successfully"
                            }
                            """))
            ),
            @ApiResponse(responseCode = "400", description = "Validation failed")
    })
    ResponseEntity<CommonResponse<GetContentResponse>> getContent(
            @RequestBody(
                    required = true,
                    description = "Chunk selection and optional token limit",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = GetContentRequest.class),
                            examples = @ExampleObject(value = """
                            {
                              "chunkId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
                              "maxTokens": 8000
                            }
                            """))
            ) GetContentRequest request);
}
