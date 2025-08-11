/**
 * Response type for OpenContext MCP's find_knowledge tool
 */
export interface KnowledgeSearchResult {
  chunkId: string;           // Unique identifier for the chunk (UUID)
  title: string;             // Title of the chunk
  snippet: string;           // Text extracted from the beginning of the chunk content with a certain length
  relevanceScore: number;    // Relevance score with the search query (0.0 ~ 1.0)
}

/**
 * Response structure for the find_knowledge tool
 */
export interface KnowledgeSearchResponse {
  results: KnowledgeSearchResult[];
  error?: {
    code: string;
    message: string;
  };
}

/**
 * Response type for OpenContext MCP's get_content tool
 */
export interface ContentResponse {
  content: string;           // Original content of the requested chunk
  tokenInfo: {
    tokenizer: string;       // Name of the tokenizer used to calculate token count
    actualTokens: number;    // Actual token count of the content
  };
  error?: {
    code: string;
    message: string;
  };
}

/**
 * Standard API response structure for OpenContext Core
 */
export interface CommonResponse<T> {
  success: boolean;
  data: T | null;
  message: string;
  errorCode: string | null;
  timestamp: string;
}

/**
 * Search API response data from OpenContext Core
 */
export interface SearchApiResponse {
  results: KnowledgeSearchResult[];
}

/**
 * Content API response data from OpenContext Core
 */
export interface GetContentApiResponse {
  content: string;
  tokenInfo: {
    tokenizer: string;
    actualTokens: number;
  };
}