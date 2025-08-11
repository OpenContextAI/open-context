import { 
  KnowledgeSearchResponse, 
  ContentResponse, 
  CommonResponse, 
  SearchApiResponse, 
  GetContentApiResponse 
} from "./types.js";

const CORE_URL = process.env.OPENCONTEXT_CORE_URL || 'http://localhost:8080';

/**
 * Implementation of OpenContext MCP's find_knowledge tool
 * Calls GET /api/v1/search API to search for relevant knowledge chunks.
 */
export async function findKnowledge(query: string, topK: number = 5): Promise<KnowledgeSearchResponse> {
  if (!query || query.trim().length === 0) {
    console.warn("[findKnowledge] Empty query provided, returning empty results");
    return { results: [] };
  }

  const url = new URL(`${CORE_URL}/api/v1/search`);
  url.searchParams.set("query", query.trim());
  if (topK) {
    url.searchParams.set("topK", topK.toString());
  }

  console.log(`[findKnowledge] Searching for knowledge with query: "${query}", topK: ${topK}`);

  try {
    const response = await fetch(url.toString(), {
      method: "GET",
      headers: {
        "Accept": "application/json",
        "User-Agent": "OpenContext-MCP-Adapter/1.0.0"
      },
    });

    if (!response.ok) {
      const errorText = await response.text().catch(() => "Unable to read error response");
      console.error(`[findKnowledge] HTTP ${response.status} ${response.statusText}: ${errorText}`);
      console.error(`[findKnowledge] Request URL: ${url.toString()}`);
      return { 
        results: [],
        error: {
          code: "SEARCH_FAILED",
          message: `Search failed with status ${response.status}: ${errorText}`
        }
      };
    }

    const result: CommonResponse<SearchApiResponse> = await response.json();
    
    if (!result.success) {
      console.error(`[findKnowledge] API returned error: ${result.message}`);
      return { 
        results: [],
        error: {
          code: result.errorCode || "SEARCH_FAILED",
          message: result.message || "Search failed"
        }
      };
    }

    console.log(`[findKnowledge] Found ${result.data?.results.length || 0} knowledge chunks`);
    return { results: result.data?.results || [] };

  } catch (error) {
    const errorMsg = error instanceof Error ? error.message : String(error);
    console.error(`[findKnowledge] Network/Parse error: ${errorMsg}`);
    console.error(`[findKnowledge] Query: "${query}", URL: ${url.toString()}`);
    return { 
      results: [],
      error: {
        code: "NETWORK_ERROR",
        message: `Network error: ${errorMsg}`
      }
    };
  }
}

/**
 * Implementation of OpenContext MCP's get_content tool
 * Calls POST /api/v1/get-content API to retrieve the full content of a specific chunk.
 */
export async function getContent(chunkId: string, maxTokens: number = 25000): Promise<ContentResponse> {
  if (!chunkId || chunkId.trim().length === 0) {
    console.warn("[getContent] Empty chunkId provided");
    return { 
      content: "",
      tokenInfo: { tokenizer: "", actualTokens: 0 },
      error: {
        code: "VALIDATION_FAILED",
        message: "Chunk ID is required"
      }
    };
  }

  const url = `${CORE_URL}/api/v1/get-content`;
  const body: { chunkId: string; maxTokens?: number } = {
    chunkId: chunkId.trim()
  };
  
  if (maxTokens) {
    body.maxTokens = maxTokens;
  }

  console.log(`[getContent] Fetching content for chunkId: "${chunkId}", maxTokens: ${maxTokens}`);

  try {
    const response = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Accept": "application/json",
        "User-Agent": "OpenContext-MCP-Adapter/1.0.0"
      },
      body: JSON.stringify(body),
    });

    if (!response.ok) {
      const errorText = await response.text().catch(() => "Unable to read error response");
      const errorMsg = `HTTP ${response.status} ${response.statusText}: ${errorText}`;
      console.error(`[getContent] ${errorMsg}`);
      console.error(`[getContent] Request body:`, JSON.stringify(body, null, 2));
      return { 
        content: "",
        tokenInfo: { tokenizer: "", actualTokens: 0 },
        error: {
          code: "CONTENT_FETCH_FAILED",
          message: `Failed to fetch content. ${errorMsg}`
        }
      };
    }

    const result: CommonResponse<GetContentApiResponse> = await response.json();
    
    if (!result.success) {
      console.error(`[getContent] API returned error: ${result.message}`);
      return { 
        content: "",
        tokenInfo: { tokenizer: "", actualTokens: 0 },
        error: {
          code: result.errorCode || "CONTENT_FETCH_FAILED",
          message: result.message || "Content fetch failed"
        }
      };
    }

    console.log(`[getContent] Successfully fetched content with ${result.data?.tokenInfo?.actualTokens || 'unknown'} tokens`);
    return { 
      content: result.data?.content || "",
      tokenInfo: result.data?.tokenInfo || { tokenizer: "", actualTokens: 0 }
    };

  } catch (error) {
    const errorMsg = error instanceof Error ? error.message : String(error);
    console.error(`[getContent] Network/Parse error: ${errorMsg}`);
    console.error(`[getContent] ChunkId: "${chunkId}"`);
    return { 
      content: "",
      tokenInfo: { tokenizer: "", actualTokens: 0 },
      error: {
        code: "NETWORK_ERROR",
        message: `Network error: ${errorMsg}`
      }
    };
  }
}