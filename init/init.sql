-- OpenContext Database Initialization Script
-- This script creates the PostgreSQL tables according to PRD specifications

-- Create extension for UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Source Documents Table
CREATE TABLE source_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    original_filename VARCHAR(255) NOT NULL,
    file_storage_path VARCHAR(1024) NOT NULL,
    file_type VARCHAR(50) NOT NULL,
    file_size BIGINT NOT NULL,
    file_checksum VARCHAR(64) NOT NULL,
    ingestion_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (ingestion_status IN ('PENDING', 'PARSING', 'CHUNKING', 'EMBEDDING', 'INDEXING', 'COMPLETED', 'ERROR', 'DELETING')),
    error_message TEXT,
    last_ingested_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_file_checksum UNIQUE (file_checksum)
);

-- Document Chunks Table
CREATE TABLE document_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_document_id UUID NOT NULL REFERENCES source_documents(id) ON DELETE CASCADE,
    parent_chunk_id UUID REFERENCES document_chunks(id) ON DELETE CASCADE,
    sequence_in_document INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better performance
CREATE INDEX idx_chunk_source_document_id ON document_chunks(source_document_id);
CREATE INDEX idx_chunk_parent_chunk_id ON document_chunks(parent_chunk_id);

-- Add comments for better documentation
COMMENT ON TABLE source_documents IS 'Table storing management information for all uploaded source files';
COMMENT ON COLUMN source_documents.id IS 'Primary key - unique identifier for each original document';
COMMENT ON COLUMN source_documents.original_filename IS 'Original filename uploaded by user';
COMMENT ON COLUMN source_documents.file_storage_path IS 'Actual storage path in Object Storage (MinIO)';
COMMENT ON COLUMN source_documents.file_type IS 'File type (PDF, MARKDOWN, etc.)';
COMMENT ON COLUMN source_documents.file_size IS 'File size in bytes';
COMMENT ON COLUMN source_documents.file_checksum IS 'SHA-256 hash (for duplicate prevention)';
COMMENT ON COLUMN source_documents.ingestion_status IS 'Current status of document processing pipeline (PENDING, PARSING, CHUNKING, EMBEDDING, INDEXING, COMPLETED, ERROR, DELETING)';
COMMENT ON COLUMN source_documents.error_message IS 'Detailed error message when status is ERROR';
COMMENT ON COLUMN source_documents.last_ingested_at IS 'Last successful ingestion completion time';
COMMENT ON COLUMN source_documents.created_at IS 'Time when first created';
COMMENT ON COLUMN source_documents.updated_at IS 'Time when last updated';
COMMENT ON CONSTRAINT uq_file_checksum ON source_documents IS 'Constraint that enforces uniqueness of file_checksum values in the table';

COMMENT ON TABLE document_chunks IS 'Table storing metadata and hierarchical structure information for each Structured Chunk';
COMMENT ON COLUMN document_chunks.id IS 'Primary key - unique identifier for each Structured Chunk (same as Elasticsearch chunkId)';
COMMENT ON COLUMN document_chunks.source_document_id IS 'Original document ID';
COMMENT ON COLUMN document_chunks.parent_chunk_id IS 'Parent chunk ID - hierarchical structure representation';
COMMENT ON COLUMN document_chunks.sequence_in_document IS 'Order among sibling chunks with the same parent_chunk_id in the original document';

-- Insert sample data for development (optional)
-- This can be removed in production
INSERT INTO source_documents (original_filename, file_storage_path, file_type, file_size, file_checksum, ingestion_status)
VALUES 
    ('sample-guide.pdf', 'documents/sample-guide.pdf', 'PDF', 1024000, 'a1b2c3d4e5f6...', 'COMPLETED'),
    ('spring-security-reference.pdf', 'documents/spring-security-reference.pdf', 'PDF', 2048000, 'f6e5d4c3b2a1...', 'PENDING');

COMMIT;