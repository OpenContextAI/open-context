# OpenContext Core Backend

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.11-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-0.36.2-blue.svg)](https://github.com/langchain4j/langchain4j)

**Enterprise-grade RAG backend service built with Spring Boot and LangChain4j**

This is the core backend service of OpenContext, implementing document processing pipelines, hybrid search capabilities, and MCP protocol APIs for secure, self-hosted RAG systems.

## Architecture Overview

The core service follows a layered architecture with clear separation of concerns:

```
com.opencontext/
├── config/              # Spring configuration classes
├── common/              # Common response objects and utilities
├── controller/          # REST API endpoints
├── service/             # Business logic layer
├── repository/          # Data access layer with QueryDSL
├── entity/              # JPA entities
├── dto/                 # Data transfer objects
├── enums/               # Enum definitions (IngestionStatus, ErrorCode)
├── exception/           # Custom exceptions and global handlers
└── pipeline/            # Document processing pipeline components
```

## Key Components

### Document Processing Pipeline
- **DocumentParsingService**: Integrates with Unstructured.io for PDF/Markdown parsing
- **ChunkingService**: Hierarchical text chunking with LangChain4j
- **EmbeddingService**: Ollama API integration for text embeddings
- **IndexingService**: Elasticsearch indexing with hybrid search setup

### Search & Retrieval Engine  
- **SearchService**: Hybrid BM25 + vector search with Korean language support
- **ContentRetrievalService**: Chunk content retrieval with token limit handling
- **SearchController**: MCP protocol API endpoints (`/search`, `/get-content`)

### Data Layer
- **PostgreSQL**: Source document metadata and chunk hierarchy storage
- **Elasticsearch**: Document search index with Korean Nori analyzer
- **MinIO**: Original file storage with S3-compatible API

## Technology Stack

### Core Framework
- **Java 21**: Latest LTS with virtual thread support
- **Spring Boot 3.3.11**: Main application framework
- **Spring Security**: API key authentication
- **Spring Data JPA + Hibernate 6.x**: ORM with PostgreSQL
- **QueryDSL 5.x**: Type-safe query construction

### RAG Pipeline
- **LangChain4j 0.36.2**: RAG pipeline orchestration framework
- **Ollama**: Local embedding model serving (Qwen3-Embedding-0.6B)
- **Elasticsearch 8.x**: Hybrid search engine with vector capabilities
- **Unstructured.io**: Document parsing service (Docker API)

### Testing & Quality
- **JUnit 5**: Unit testing framework
- **TestContainers**: Integration testing with real databases
- **Mockito**: Mocking framework
- **Flyway**: Database migration management

## API Endpoints

### MCP Protocol APIs (No Authentication)
These endpoints are used by the MCP adapter for AI assistant integration:

```http
# Phase 1: Exploratory search
GET /api/v1/search?query={query}&topK={count}

# Phase 2: Content retrieval  
POST /api/v1/get-content
Content-Type: application/json
{
  "chunkId": "uuid-string",
  "maxTokens": 25000
}
```

### Administrative APIs (API Key Required)
Document management endpoints requiring `X-API-KEY` header:

```http
# Upload documents
POST /api/v1/sources/upload
X-API-KEY: your-api-key
Content-Type: multipart/form-data

# List documents with pagination
GET /api/v1/sources?page=0&size=20&sort=createdAt,desc
X-API-KEY: your-api-key

# Trigger document reprocessing  
POST /api/v1/sources/{id}/resync
X-API-KEY: your-api-key

# Delete document
DELETE /api/v1/sources/{id}
X-API-KEY: your-api-key
```

## Configuration

### Database Configuration
```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/opencontext
    username: user
    password: password
  jpa:
    hibernate:
      ddl-auto: validate    # Production: validate only
    show-sql: false
  flyway:
    baseline-on-migrate: true
```

### Elasticsearch Configuration
```yaml
app:
  elasticsearch:
    url: http://localhost:9200
    index: document_chunks_index
```

### Embedding Service Configuration  
```yaml
app:
  ollama:
    api:
      url: http://localhost:11434
    embedding:
      model: dengcao/Qwen3-Embedding-0.6B:F16
  embedding:
    batch-size: 10
```

### API Security
```yaml
opencontext:
  api:
    key: dev-api-key-123    # Change for production
```

## Database Schema

### Source Documents Table
Stores original document metadata and processing status:

```sql
CREATE TABLE source_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    original_filename VARCHAR(255) NOT NULL,
    file_storage_path VARCHAR(1024) NOT NULL,
    file_type VARCHAR(50) NOT NULL,
    file_size BIGINT NOT NULL,
    file_checksum VARCHAR(64) NOT NULL UNIQUE,
    ingestion_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    last_ingested_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### Document Chunks Table
Maintains hierarchical chunk structure and relationships:

```sql
CREATE TABLE document_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_document_id UUID NOT NULL REFERENCES source_documents(id) ON DELETE CASCADE,
    parent_chunk_id UUID REFERENCES document_chunks(id) ON DELETE CASCADE,
    sequence_in_document INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### Elasticsearch Index Mapping
Document chunks are stored in Elasticsearch with the following structure:

```json
{
  "mappings": {
    "properties": {
      "chunkId": { "type": "keyword" },
      "content": { 
        "type": "text", 
        "analyzer": "korean_nori" 
      },
      "embedding": {
        "type": "dense_vector",
        "dims": 1024,
        "index": "true",
        "similarity": "cosine"
      },
      "metadata": {
        "properties": {
          "title": { "type": "text", "analyzer": "korean_nori" },
          "hierarchyLevel": { "type": "integer" },
          "originalFilename": { "type": "keyword" },
          "fileType": { "type": "keyword" }
        }
      }
    }
  }
}
```

## Development

### Prerequisites
```bash
java --version    # OpenJDK 21
./gradlew --version    # Gradle 8+
```

### Local Development Setup
```bash
# Start dependencies with Docker Compose
docker compose up -d postgres elasticsearch ollama minio

# Run application
./gradlew bootRun

# Run with specific profile
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### Testing
```bash
# Unit tests
./gradlew test

# Integration tests (requires TestContainers)
./gradlew integrationTest

# Test with coverage
./gradlew jacocoTestReport

# Build without tests
./gradlew build -x test
```

### Database Migrations
```bash
# Apply migrations
./gradlew flywayMigrate

# Check migration status
./gradlew flywayInfo

# Repair migration checksums (if needed)
./gradlew flywayRepair
```

## Monitoring & Operations

### Health Checks
The application exposes Spring Boot Actuator endpoints:

```bash
# Overall health status
curl http://localhost:8080/actuator/health

# Database connectivity
curl http://localhost:8080/actuator/health/db

# Elasticsearch connectivity  
curl http://localhost:8080/actuator/health/elasticsearch

# Application info
curl http://localhost:8080/actuator/info
```

### Logging Configuration
```yaml
# application.yml
logging:
  level:
    com.opencontext: DEBUG        # Application logs
    org.hibernate.SQL: DEBUG      # SQL queries
    org.hibernate.orm.jdbc.bind: TRACE    # SQL parameters
```

### Performance Tuning
```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 20000

server:
  tomcat:
    threads:
      max: 200
      min-spare: 10
```

## Error Handling

The application uses structured error responses with specific error codes:

### Common Error Codes
- `VALIDATION_FAILED` (400): Request validation errors
- `INSUFFICIENT_PERMISSION` (403): Invalid API key
- `SOURCE_DOCUMENT_NOT_FOUND` (404): Document not found
- `CHUNK_NOT_FOUND` (404): Chunk not found
- `DUPLICATE_FILE_UPLOADED` (409): File already exists
- `CONTENT_NOT_AVAILABLE` (422): Chunk content unavailable
- `EXTERNAL_SERVICE_UNAVAILABLE` (503): Ollama/Elasticsearch connection error

### Error Response Format
```json
{
  "success": false,
  "data": null,
  "message": "Human-readable error message",
  "errorCode": "ERROR_CODE_ENUM_VALUE",
  "timestamp": "2025-08-21T12:00:00Z"
}
```

## Security Considerations

### API Key Authentication
- All administrative endpoints require `X-API-KEY` header
- Default development key: `dev-api-key-123`
- Generate production keys with: `openssl rand -hex 32`

### Data Protection
- All data processing happens locally
- No external API calls for sensitive document content
- PostgreSQL stores only metadata and relationships
- Elasticsearch contains indexed content for search

### Network Security
- MCP endpoints (`/search`, `/get-content`) have no authentication for internal use
- Administrative endpoints require API key authentication
- Configure reverse proxy (nginx) for HTTPS termination in production

## Troubleshooting

### Common Issues

**Elasticsearch Connection Failed**
```bash
# Check Elasticsearch status
curl http://localhost:9200/_cluster/health

# Verify Korean analyzer plugin
curl http://localhost:9200/_cat/plugins?v
```

**Ollama Model Not Found**
```bash
# Pull embedding model
docker compose exec ollama ollama pull dengcao/Qwen3-Embedding-0.6B:F16

# List available models
docker compose exec ollama ollama list
```

**Database Migration Issues**
```bash
# Check migration status
./gradlew flywayInfo

# Repair checksums after manual changes
./gradlew flywayRepair
```

## Contributing

### Code Style
- Follow Google Java Style Guide
- Use Lombok annotations for boilerplate reduction
- Prefer constructor injection over field injection
- Write comprehensive JavaDoc for public APIs

### Testing Requirements
- Unit tests for all service classes
- Integration tests using TestContainers for repository classes
- API tests for all controller endpoints
- Minimum 80% code coverage required

### Commit Guidelines
- Use conventional commits format
- Include issue numbers in commit messages
- Keep commits focused on single concerns
- Write clear, descriptive commit messages

For more information, see the main [Contributing Guide](../CONTRIBUTING.md).