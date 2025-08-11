import { createServer, IncomingMessage, ServerResponse } from 'http';
import { URL } from 'url';

// Mock find_knowledge API - GET /api/v1/search
function handleSearch(req: IncomingMessage, res: ServerResponse) {
  const url = new URL(req.url || '', `http://${req.headers.host}`);
  const query = url.searchParams.get('query');
  const topK = parseInt(url.searchParams.get('topK') || '5', 10);
  
  console.log(`[Mock Server] find_knowledge called with query: "${query}", topK: ${topK}`);
  
  // Simulate different responses based on query
  let mockResults = [];
  
  if (query?.includes('Spring Security') || query?.includes('JWT')) {
    mockResults = [
      {
        chunkId: "spring-security-jwt-1",
        title: "Spring Security JWT Filter Implementation",
        snippet: "This guide covers implementing JWT authentication filters in Spring Security, including token validation and user authentication...",
        relevanceScore: 0.95
      },
      {
        chunkId: "spring-security-jwt-2", 
        title: "JWT Token Configuration in Spring Boot",
        snippet: "Configure JWT token settings, expiration times, and signing keys in your Spring Boot application...",
        relevanceScore: 0.88
      }
    ];
  } else if (query?.includes('Elasticsearch') || query?.includes('검색')) {
    mockResults = [
      {
        chunkId: "elasticsearch-search-1",
        title: "Elasticsearch Search Configuration",
        snippet: "Learn how to configure Elasticsearch for optimal search performance and relevance scoring...",
        relevanceScore: 0.92
      }
    ];
  } else {
    // Default mock response
    mockResults = [
      {
        chunkId: "mock-chunk-1",
        title: `Mock Result for: ${query}`,
        snippet: "This is a mock snippet for testing purposes. The query was processed successfully by the mock server.",
        relevanceScore: 0.85
      },
      {
        chunkId: "mock-chunk-2",
        title: "Another Mock Result",
        snippet: "Additional mock data to test the topK parameter functionality.",
        relevanceScore: 0.75
      }
    ];
  }
  
  // Limit results based on topK parameter
  const limitedResults = mockResults.slice(0, topK);
  
  const response = {
    success: true,
    data: {
      results: limitedResults
    },
    message: "Search completed successfully",
    errorCode: null,
    timestamp: new Date().toISOString()
  };
  
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(response, null, 2));
}

// Mock get_content API - POST /api/v1/get-content
function handleGetContent(req: IncomingMessage, res: ServerResponse) {
  let body = '';
  
  req.on('data', (chunk) => {
    body += chunk.toString();
  });
  
  req.on('end', () => {
    try {
      const { chunkId, maxTokens = 25000 } = JSON.parse(body);
      
      console.log(`[Mock Server] get_content called with chunkId: "${chunkId}", maxTokens: ${maxTokens}`);
      
      // Simulate different content based on chunkId
      let mockContent = "";
      
      if (chunkId === "spring-security-jwt-1") {
        mockContent = `# Spring Security JWT Filter Implementation

## Overview
This comprehensive guide covers implementing JWT (JSON Web Token) authentication filters in Spring Security applications.

## Key Components

### 1. JWT Filter Class
\`\`\`java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        // JWT token extraction and validation logic
        String token = extractTokenFromRequest(request);
        if (token != null && jwtTokenProvider.validateToken(token)) {
            Authentication auth = jwtTokenProvider.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }
}
\`\`\`

### 2. Token Provider
\`\`\`java
@Component
public class JwtTokenProvider {
    @Value("\${jwt.secret}")
    private String jwtSecret;
    
    public String generateToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + 86400000); // 24 hours
        
        return Jwts.builder()
                .setSubject(userPrincipal.getUsername())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(SignatureAlgorithm.HS512, jwtSecret)
                .compact();
    }
}
\`\`\`

## Configuration
Add the filter to your SecurityConfig:
\`\`\`java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
\`\`\`

This implementation provides secure JWT-based authentication for your Spring Boot application.`;
        
      } else if (chunkId === "elasticsearch-search-1") {
        mockContent = `# Elasticsearch Search Configuration

## Search Optimization
Learn how to configure Elasticsearch for optimal search performance and relevance scoring.

## Key Settings
- Index mapping optimization
- Analyzer configuration  
- Relevance scoring tuning
- Query performance optimization

This content demonstrates how to configure Elasticsearch for the best search experience.`;
        
      } else {
        // Default mock content
        mockContent = `# Mock Content for Testing

## Chunk ID: ${chunkId}
This is mock content generated by the test server for testing purposes.

## Content Details
- **Chunk ID**: ${chunkId}
- **Max Tokens**: ${maxTokens}
- **Generated At**: ${new Date().toISOString()}

## Test Information
This mock content is designed to test the mcp-adapter functionality without requiring a real OpenContext Core server.

You can use this to verify:
1. Tool parameter handling
2. Response formatting
3. Error handling
4. Token counting

The content is structured to provide realistic test data that mimics actual knowledge base responses.`;
      }
      
      // Simulate token counting (simple word count for demo)
      const actualTokens = Math.min(mockContent.split(/\s+/).length, maxTokens);
      
      const response = {
        success: true,
        data: {
          content: mockContent,
          tokenInfo: {
            tokenizer: "mock-tokenizer",
            actualTokens: actualTokens
          }
        },
        message: "Content retrieved successfully",
        errorCode: null,
        timestamp: new Date().toISOString()
      };
      
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(response, null, 2));
      
    } catch (error) {
      console.error('[Mock Server] Error parsing request body:', error);
      res.writeHead(400, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        success: false,
        data: null,
        message: 'Invalid JSON in request body',
        errorCode: 'INVALID_JSON',
        timestamp: new Date().toISOString()
      }));
    }
  });
}

// Health check endpoint
function handleHealth(req: IncomingMessage, res: ServerResponse) {
  const response = {
    status: 'healthy',
    service: 'OpenContext Mock Server',
    timestamp: new Date().toISOString()
  };
  
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(response, null, 2));
}

// CORS headers
function setCorsHeaders(res: ServerResponse) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
}

// Main server logic
const server = createServer((req: IncomingMessage, res: ServerResponse) => {
  setCorsHeaders(res);
  
  // Handle preflight OPTIONS request
  if (req.method === 'OPTIONS') {
    res.writeHead(200);
    res.end();
    return;
  }
  
  const url = new URL(req.url || '', `http://${req.headers.host}`);
  const path = url.pathname;
  
  console.log(`[Mock Server] ${req.method} ${path}`);
  
  try {
    if (req.method === 'GET' && path === '/api/v1/search') {
      handleSearch(req, res);
    } else if (req.method === 'POST' && path === '/api/v1/get-content') {
      handleGetContent(req, res);
    } else if (req.method === 'GET' && path === '/health') {
      handleHealth(req, res);
    } else {
      // 404 handler
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        success: false,
        data: null,
        message: 'Endpoint not found',
        errorCode: 'NOT_FOUND',
        timestamp: new Date().toISOString()
      }));
    }
  } catch (error) {
    console.error('[Mock Server] Error:', error);
    res.writeHead(500, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      success: false,
      data: null,
      message: 'Internal server error',
      errorCode: 'INTERNAL_ERROR',
      timestamp: new Date().toISOString()
    }));
  }
});

const PORT = parseInt(process.env.MOCK_SERVER_PORT || '8080', 10);

server.listen(PORT, '0.0.0.0', () => {
  console.log(`🚀 OpenContext Mock Server running on http://0.0.0.0:${PORT}`);
  console.log(`📖 Available endpoints:`);
  console.log(`   GET  /api/v1/search?query=<query>&topK=<number>`);
  console.log(`   POST /api/v1/get-content`);
  console.log(`   GET  /health`);
  console.log(`\n💡 Test with: export OPENCONTEXT_CORE_URL=http://localhost:${PORT}`);
});

// Graceful shutdown
process.on('SIGINT', () => {
  console.log('\n🛑 Shutting down mock server...');
  server.close(() => {
    console.log('✅ Mock server stopped');
    process.exit(0);
  });
});
