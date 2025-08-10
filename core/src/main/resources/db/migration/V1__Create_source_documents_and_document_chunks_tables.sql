-- OpenContext MVP Database Schema
-- This migration creates the core tables for document storage and hierarchical chunk management
-- Based on PRD Section 5.1 PostgreSQL Data Model

-- Table: source_documents
-- Stores master information for all uploaded source files
CREATE TABLE source_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    original_filename VARCHAR(255) NOT NULL,
    file_storage_path VARCHAR(1024) NOT NULL,
    file_type VARCHAR(50) NOT NULL,
    file_size BIGINT NOT NULL,
    file_checksum VARCHAR(64) NOT NULL,
    ingestion_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    last_ingested_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_file_checksum UNIQUE (file_checksum)
);

-- Table: document_chunks  
-- Stores hierarchical structure information for structured chunks
CREATE TABLE document_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_document_id UUID NOT NULL REFERENCES source_documents(id) ON DELETE CASCADE,
    parent_chunk_id UUID REFERENCES document_chunks(id) ON DELETE CASCADE,
    sequence_in_document INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance optimization
-- Index for querying chunks by source document
CREATE INDEX idx_chunk_source_document_id ON document_chunks(source_document_id);

-- Index for hierarchical navigation (parent-child relationships)
CREATE INDEX idx_chunk_parent_chunk_id ON document_chunks(parent_chunk_id);

-- Index for duplicate file prevention via checksum
CREATE INDEX idx_source_documents_file_checksum ON source_documents(file_checksum);

-- Index for querying documents by ingestion status
CREATE INDEX idx_source_documents_ingestion_status ON source_documents(ingestion_status);

-- Comments for table and column documentation
COMMENT ON TABLE source_documents IS 'Master table for storing metadata of all uploaded source files';
COMMENT ON COLUMN source_documents.id IS 'Primary key - unique identifier for each uploaded source document';
COMMENT ON COLUMN source_documents.original_filename IS 'Original filename provided by user during upload';
COMMENT ON COLUMN source_documents.file_storage_path IS 'Storage path in Object Storage (MinIO) for accessing the original file';
COMMENT ON COLUMN source_documents.file_type IS 'Type of uploaded file (e.g., PDF, MARKDOWN)';
COMMENT ON COLUMN source_documents.file_size IS 'Size of uploaded file in bytes';
COMMENT ON COLUMN source_documents.file_checksum IS 'SHA-256 hash of file content for duplicate prevention and integrity verification';
COMMENT ON COLUMN source_documents.ingestion_status IS 'Current status of document ingestion process (PENDING, PARSING, CHUNKING, EMBEDDING, INDEXING, COMPLETED, ERROR, DELETING)';
COMMENT ON COLUMN source_documents.error_message IS 'Detailed error message when ingestion_status is ERROR (for developer debugging)';
COMMENT ON COLUMN source_documents.last_ingested_at IS 'Timestamp when this document was last successfully ingested';
COMMENT ON COLUMN source_documents.created_at IS 'Timestamp when this record was first created in database';
COMMENT ON COLUMN source_documents.updated_at IS 'Timestamp when this record was last updated';
COMMENT ON CONSTRAINT uq_file_checksum ON source_documents IS 'Ensures file_checksum values are unique across the table, essential for MVP implementation';

COMMENT ON TABLE document_chunks IS 'Table storing hierarchical structure information for document chunks';
COMMENT ON COLUMN document_chunks.id IS 'Primary key - unique identifier for each structured chunk. Same value as chunkId in Elasticsearch';
COMMENT ON COLUMN document_chunks.source_document_id IS 'Foreign key to source_documents table. Parent document is cascade deleted when chunk is removed';
COMMENT ON COLUMN document_chunks.parent_chunk_id IS 'Foreign key to this table for hierarchical parent chunk. NULL for top-level chunks';
COMMENT ON COLUMN document_chunks.sequence_in_document IS 'Sequential order within document, among sibling chunks with same parent_chunk_id';
COMMENT ON COLUMN document_chunks.created_at IS 'Timestamp when this chunk record was first created';