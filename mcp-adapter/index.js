const http = require('http');

// Simple MCP Adapter - Docker Health Check Only
const server = http.createServer((req, res) => {
  res.setHeader('Content-Type', 'application/json');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
  
  if (req.method === 'OPTIONS') {
    res.writeHead(200);
    res.end();
    return;
  }
  
  if (req.method === 'GET' && req.url === '/health') {
    const health = {
      status: 'healthy',
      service: 'mcp-adapter',
      timestamp: new Date().toISOString(),
      uptime: process.uptime(),
      version: '1.0.0'
    };
    
    res.writeHead(200);
    res.end(JSON.stringify(health, null, 2));
  } else if (req.method === 'GET' && req.url === '/') {
    const info = {
      service: 'OpenContext MCP Adapter',
      version: '1.0.0',
      status: 'running',
      endpoints: {
        health: '/health',
        info: '/'
      }
    };
    
    res.writeHead(200);
    res.end(JSON.stringify(info, null, 2));
  } else {
    res.writeHead(404);
    res.end(JSON.stringify({ 
      error: 'Endpoint not found',
      available: ['/', '/health']
    }, null, 2));
  }
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, '0.0.0.0', () => {
  console.log(`🚀 MCP Adapter running on port ${PORT}`);
  console.log(`📊 Health check: http://localhost:${PORT}/health`);
  console.log(`ℹ️  Info: http://localhost:${PORT}/`);
});

// Graceful shutdown
process.on('SIGTERM', () => {
  console.log('SIGTERM received, shutting down gracefully');
  server.close(() => {
    console.log('Server closed');
    process.exit(0);
  });
});

process.on('SIGINT', () => {
  console.log('SIGINT received, shutting down gracefully');
  server.close(() => {
    console.log('Server closed');
    process.exit(0);
  });
});
