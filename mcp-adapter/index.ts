import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import { findKnowledge, getContent } from "./lib/api.js";
import { createServer } from "http";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { Command } from "commander";
import { IncomingMessage } from "http";

// Environment variables for default values
const DEFAULT_TOP_K = parseInt(process.env.OPENCONTEXT_DEFAULT_TOP_K || '5', 10);
const DEFAULT_MAX_TOKENS = parseInt(process.env.OPENCONTEXT_DEFAULT_MAX_TOKENS || '25000', 10);

// Parse CLI arguments using commander
const program = new Command()
  .option("--transport <stdio|http>", "transport type", "stdio")
  .option("--port <number>", "port for HTTP transport", "3000")
  .allowUnknownOption() // let MCP Inspector / other wrappers pass through extra flags
  .parse(process.argv);

const cliOptions = program.opts<{
  transport: string;
  port: string;
}>();

// Validate transport option
const allowedTransports = ["stdio", "http"];
if (!allowedTransports.includes(cliOptions.transport)) {
  console.error(
    `Invalid --transport value: '${cliOptions.transport}'. Must be one of: stdio, http.`
  );
  process.exit(1);
}

// Transport configuration
const TRANSPORT_TYPE = (cliOptions.transport || "stdio") as "stdio" | "http";

// HTTP port configuration - CLI arguments first, environment variables second
const CLI_PORT = (() => {
  const parsed = parseInt(cliOptions.port, 10);
  if (!isNaN(parsed)) return parsed;
  
  // Use environment variable if CLI argument is not provided
  const envPort = parseInt(process.env.MCP_SERVER_PORT || '3000', 10);
  return isNaN(envPort) ? 3000 : envPort;
})();

function getClientIp(req: IncomingMessage): string | undefined {
  // Check both possible header casings
  const forwardedFor = req.headers["x-forwarded-for"] || req.headers["X-Forwarded-For"];

  if (forwardedFor) {
    // X-Forwarded-For can contain multiple IPs
    const ips = Array.isArray(forwardedFor) ? forwardedFor[0] : forwardedFor;
    const ipList = ips.split(",").map((ip) => ip.trim());

    // Find the first public IP address
    for (const ip of ipList) {
      const plainIp = ip.replace(/^::ffff:/, "");
      if (
        !plainIp.startsWith("10.") &&
        !plainIp.startsWith("192.168.") &&
        !/^172\.(1[6-9]|2[0-9]|3[0-1])\./.test(plainIp)
      ) {
        return plainIp;
      }
    }
    // If all are private, use the first one
    return ipList[0].replace(/^::ffff:/, "");
  }

  // Fallback: use remote address, strip IPv6-mapped IPv4
  if (req.socket?.remoteAddress) {
    return req.socket.remoteAddress.replace(/^::ffff:/, "");
  }
  return undefined;
}

function createServerInstance(clientIp?: string) {
  const server = new McpServer(
    { 
      name: "OpenContext MCP Adapter", 
      version: "1.0.0" 
    },
    { 
      instructions: "Use this server to access and search a secure, local knowledge base compiled from the user's private documents and technical specifications. This is the authoritative source of truth for answering questions about the user's specific codebase, internal architecture, or project-related documentation, as it contains proprietary and context-rich information not available on the public internet." 
    }
  );

  // OpenContext MCP: find_knowledge tool
  server.registerTool(
    "find_knowledge", 
    {
      title: "Find Knowledge",
      description: `This is the first-stage search tool for identifying the most relevant knowledge chunk ID (chunkId) based on the user's query. The primary purpose of this tool is to identify the best candidate for the subsequent get_content tool.

Process:
It performs an initial search for a list of relevant knowledge chunk candidates based on the user's natural language question.
The LLM must analyze the returned list (which includes titles, snippets, and relevance scores) to select the single best item that most closely matches the user's intent.
The chunkId from the selected item must be used immediately to call the 'get_content tool'.

Usage Rule:
This tool must always be called first when the user asks a question about their internal documents or specific projects.`,
      inputSchema: { 
        query: z.string().describe("User's original natural language search query (e.g., 'Spring Security JWT filter implementation')"),
        topK: z.number().optional().describe(`Maximum number of results to return (default: ${DEFAULT_TOP_K})`)
      }
    },
    async ({ query, topK = DEFAULT_TOP_K }) => {
      const clientInfo = clientIp ? ` from ${clientIp}` : "";
      console.log(`[MCP] find_knowledge called${clientInfo} with query: "${query}", topK: ${topK}`);
      
      try {
        const result = await findKnowledge(query, topK);
        
        if (result.error) {
          console.error(`[MCP] find_knowledge error${clientInfo}: ${result.error.message}`);
          return { 
            content: [{ 
              type: "text", 
              text: `Error occurred during search: ${result.error.message}` 
            }] 
          };
        }
        
        if (!result.results || result.results.length === 0) {
          return { 
            content: [{ 
              type: "text", 
              text: "No knowledge chunks found related to the search query." 
            }] 
          };
        }
        
        const resultsText = result.results.map((item, index) => {
          return `> **Result ${index + 1}: ${item.title}**\n> - **Relevance:** ${(item.relevanceScore * 100).toFixed(1)}%\n> - **Snippet:** ${item.snippet}\n> - **ChunkID:** \`${item.chunkId}\``;
        }).join("\n\n");
        
        const responseText = `Search complete. The following knowledge chunks were found. The LLM's next action is to select the best ChunkID and call the 'get_content' tool.\n\n${resultsText}`;
        
        console.log(`[MCP] find_knowledge returned ${result.results.length} results${clientInfo}`);
        return { 
          content: [{ 
            type: "text", 
            text: responseText 
          }] 
        };
        
      } catch (error) {
        console.error(`[MCP] find_knowledge unexpected error${clientInfo}:`, error);
        return { 
          content: [{ 
            type: "text", 
            text: `Unexpected error occurred during search processing: ${error instanceof Error ? error.message : String(error)}` 
          }] 
        };
      }
    }
  );

  // OpenContext MCP: get_content tool
  server.registerTool(
    "get_content", 
    {
      title: "Get Content",
      description: `This is the second-stage tool that retrieves the full, original text content of a specific chunk using a \`chunkId\` obtained from the 'find_knowledge' tool.
    
Prerequisite:
- You must call 'find_knowledge' first to get a valid \`chunkId\`. This tool cannot be used without it.

Action:
- It takes a single \`chunkId\` as input.
- It returns the complete text content, which serves as the decisive context for the LLM to generate the final, detailed answer for the user.`,
      inputSchema: { 
        chunkId: z.string().describe("The single, most relevant chunkId selected from the results of the 'find_knowledge' tool"),
        maxTokens: z.number().optional().describe(`The maximum number of tokens for the returned content (default: ${DEFAULT_MAX_TOKENS})`)
      }
    },
    async ({ chunkId, maxTokens = DEFAULT_MAX_TOKENS }) => {
      const clientInfo = clientIp ? ` from ${clientIp}` : "";
      console.log(`[MCP] get_content called${clientInfo} with chunkId: "${chunkId}", maxTokens: ${maxTokens}`);
      
      try {
        const result = await getContent(chunkId, maxTokens);
        
        if (result.error) {
          console.error(`[MCP] get_content error${clientInfo}: ${result.error.message}`);
                  return { 
          content: [{ 
            type: "text", 
            text: `Error occurred while retrieving content: ${result.error.message}` 
          }] 
        };
        }
        
        const tokenInfo = result.tokenInfo ? 
          `\n\n**Token Information:**\n- Tokenizer: ${result.tokenInfo.tokenizer}\n- Actual Token Count: ${result.tokenInfo.actualTokens}` : 
          "";
        
        const responseText = result.content;
        
        console.log(`[MCP] get_content successfully returned content with ${result.tokenInfo?.actualTokens || 'unknown'} tokens${clientInfo}`);
        return { 
          content: [{ 
            type: "text", 
            text: responseText 
          }] 
        };
        
      } catch (error) {
        console.error(`[MCP] get_content unexpected error${clientInfo}:`, error);
        return { 
          content: [{ 
            type: "text", 
            text: `Unexpected error occurred during content processing: ${error instanceof Error ? error.message : String(error)}` 
          }] 
        };
      }
    }
  );

  return server;
}

async function main() {
  const transportType = TRANSPORT_TYPE;

  if (transportType === "http") {
    // Get initial port from environment or use default
    const initialPort = CLI_PORT ?? 3000;
    // Keep track of which port we end up using
    let actualPort = initialPort;
    
    const httpServer = createServer(async (req, res) => {
      const url = new URL(req.url || "", `http://${req.headers.host}`).pathname;

      // Set CORS headers for all responses
      res.setHeader("Access-Control-Allow-Origin", "*");
      res.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS,DELETE");
      res.setHeader(
        "Access-Control-Allow-Headers",
        "Content-Type, MCP-Session-Id, mcp-session-id, MCP-Protocol-Version"
      );
      res.setHeader("Access-Control-Expose-Headers", "MCP-Session-Id");

      // Handle preflight OPTIONS requests
      if (req.method === "OPTIONS") {
        res.writeHead(200);
        res.end();
        return;
      }

      try {
        // Extract client IP address using socket remote address (most reliable)
        const clientIp = getClientIp(req);

        // Create new server instance for each request
        const requestServer = createServerInstance(clientIp);

        if (url === "/mcp") {
          const transport = new StreamableHTTPServerTransport({
            sessionIdGenerator: undefined,
          });
          await requestServer.connect(transport);
          await transport.handleRequest(req, res);
        } else if (url === "/ping") {
          res.writeHead(200, { "Content-Type": "text/plain" });
          res.end("pong");
        } else if (url === "/health") {
          res.writeHead(200, { "Content-Type": "application/json" });
          res.end(JSON.stringify({ 
            status: 'healthy', 
            service: 'OpenContext MCP Adapter',
            mode: 'http',
            timestamp: new Date().toISOString()
          }));
        } else if (url === "/info") {
          res.writeHead(200, { "Content-Type": "application/json" });
          res.end(JSON.stringify({
            name: "OpenContext MCP Adapter",
            version: "1.0.0",
            mode: "http",
            port: actualPort,
            defaultTopK: DEFAULT_TOP_K,
            defaultMaxTokens: DEFAULT_MAX_TOKENS,
            endpoints: {
              health: '/health',
              info: '/info',
              mcp: '/mcp'
            }
          }));
        } else {
          res.writeHead(404);
          res.end("Not found");
        }
      } catch (error) {
        console.error("Error handling request:", error);
        if (!res.headersSent) {
          res.writeHead(500);
          res.end("Internal Server Error");
        }
      }
    });

    // Function to attempt server listen with port fallback
    const startServer = (port: number, maxAttempts = 10) => {
      httpServer.once("error", (err: NodeJS.ErrnoException) => {
        if (err.code === "EADDRINUSE" && port < initialPort + maxAttempts) {
          console.warn(`Port ${port} is in use, trying port ${port + 1}...`);
          startServer(port + 1, maxAttempts);
        } else {
          console.error(`Failed to start server: ${err.message}`);
          process.exit(1);
        }
      });

      httpServer.listen(port, () => {
        actualPort = port;
        console.log(`[Server] OpenContext MCP Server running on HTTP mode at http://localhost:${actualPort}/mcp`);
        console.log(`[Server] Health check: http://localhost:${actualPort}/health`);
        console.log(`[Server] Server info: http://localhost:${actualPort}/info`);
      });
    };

    // Start the server with initial port
    startServer(initialPort);
  } else {
    // Stdio transport - this is already stateless by nature
    const server = createServerInstance();
    const transport = new StdioServerTransport();
    await server.connect(transport);
    console.log("[Server] OpenContext MCP Server running on stdio");
  }
}

main().catch((error) => {
  const errorMsg = error instanceof Error ? error.message : String(error);
  console.error(`[Fatal] Unhandled error in main(): ${errorMsg}`);
  if (error instanceof Error && error.stack) {
    console.error(`[Fatal] Stack trace:`, error.stack);
  }
  process.exit(1);
});