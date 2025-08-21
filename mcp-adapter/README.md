# OpenContext MCP Adapter

A Model Context Protocol (MCP) adapter for the OpenContext system. This adapter allows Cursor and other MCP clients to use OpenContext's document search and context provision capabilities.

## Key Features

- **Two-Phase Knowledge Search System**: 
  - `find_knowledge`: Semantic search to identify relevant document chunks
  - `get_content`: Retrieve complete content of selected chunks
- **Dual Transport Support**: Supports both stdio and HTTP transport
- **Real-time Logging**: Detailed logging for all MCP tool calls

## System Requirements

- **Node.js**: 18.0.0 or higher

## Installation and Setup

### Method 1: Using Docker Compose (Recommended)
```bash
# Run from project root
cd /path/to/open-context
docker-compose up -d
```

### Method 2: Local Development Environment
```bash
# Install dependencies
cd mcp-adapter
npm install

# Set environment variables
export OPENCONTEXT_CORE_URL=http://localhost:8080
export MCP_SERVER_PORT=3000
export OPENCONTEXT_DEFAULT_TOP_K=5
export OPENCONTEXT_DEFAULT_MAX_TOKENS=25000
```


## MCP Client Configuration

### MCP Client Registration (HTTP Mode)

**Cursor/VSCode**:
```json
{
  "mcpServers": {
    "opencontext": {
      "url": "http://localhost:3000/mcp"
    }
  }
}
```

**For other MCP clients**:
```json
{
  "mcpServers": {
  "opencontext": {
    "type": "streamable-http",
    "url": "http://localhost:3000/mcp"
  }
}
```

**Configuration File Locations:**
- **Cursor**: `%USERPROFILE%\.cursor\mcp.json` (Windows), `~/Library/Application Support/Cursor/mcp.json` (macOS), `~/.cursor/mcp.json` (Linux)
- **Claude Desktop**: `%APPDATA%\Claude\claude_desktop_config.json` (Windows), `~/Library/Application Support/Claude/claude_desktop_config.json` (macOS)

**Important**: The MCP server runs in HTTP mode and requires an MCP client for communication. Direct execution is not possible.

## Usage

Once configuration is complete, you can use it in MCP clients as follows:

```
use opencontext
```

Entering this command will give you access to all tools of the OpenContext MCP adapter.

## Project Structure

```
mcp-adapter/
├── index.ts                 # Main entry point and MCP server configuration
├── lib/
│   ├── api.ts              # OpenContext Core API client
│   └── types.ts            # TypeScript type definitions
├── mock-server.ts          # Development mock server
├── dist/                   # Built JavaScript files
├── package.json            # Project dependencies and scripts
├── tsconfig.json          # TypeScript configuration
└── README.md              # This file
```

## Available MCP Tools

### 1. `find_knowledge` - Knowledge Search
**Description**: First-stage search tool that identifies the most relevant knowledge chunk ID based on user queries

**Input Parameters**:
- `query` (required): Natural language search query (e.g., "Spring Security JWT filter implementation")
- `topK` (optional): Maximum number of results to return (default: 5)

**Output**: List of chunks with relevance scores (chunkId, title, snippet, relevanceScore)

**Usage Rule**: This tool must always be called first, and the most appropriate chunkId must be selected from the results.

### 2. `get_content` - Content Retrieval
**Description**: Second-stage tool that retrieves complete content of specific chunks using chunkId obtained from `find_knowledge`

**Input Parameters**:
- `chunkId` (required): Chunk ID selected from `find_knowledge`
- `maxTokens` (optional): Maximum tokens to return (default: 25000)

**Output**: Original text content and token information

**Usage Rule**: Must call `find_knowledge` first to obtain a valid chunkId.

### Port Verification
When the MCP adapter is running normally, you can access the following endpoints:
- **MCP Endpoint**: `http://localhost:3000/mcp`
- **Health Check**: `http://localhost:3000/health`
- **Server Info**: `http://localhost:3000/info`
- **Ping Test**: `http://localhost:3000/ping`


## Logging and Monitoring

All MCP tool calls are logged in the following format:
```
[MCP] find_knowledge called from 192.168.1.100 with query: "Spring Security", topK: 5
[MCP] find_knowledge returned 3 results from 192.168.1.100
[MCP] get_content called from 192.168.1.100 with chunkId: "uuid-123", maxTokens: 25000
[MCP] get_content successfully returned content with 1500 tokens from 192.168.1.100
```

