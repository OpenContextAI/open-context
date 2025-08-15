-- Add missing columns to document_chunks table for Entity compatibility
-- This migration aligns the database schema with the current DocumentChunk entity

-- Add chunk_id column (string representation of the chunk identifier for Elasticsearch consistency)
ALTER TABLE document_chunks ADD COLUMN chunk_id VARCHAR(255);

-- Add source_document_uuid column (for direct UUID access)
ALTER TABLE document_chunks ADD COLUMN source_document_uuid UUID;

-- Add parent_chunk_uuid column (for parent relationship as UUID)
ALTER TABLE document_chunks ADD COLUMN parent_chunk_uuid UUID;

-- Add title column (section heading or title)
ALTER TABLE document_chunks ADD COLUMN title VARCHAR(500);

-- Add hierarchy_level column (depth level in document structure)
ALTER TABLE document_chunks ADD COLUMN hierarchy_level INTEGER;

-- Add element_type column (type of original document element)
ALTER TABLE document_chunks ADD COLUMN element_type VARCHAR(50);

-- Create unique constraint on chunk_id
CREATE UNIQUE INDEX idx_document_chunks_chunk_id ON document_chunks(chunk_id);

-- Add index for hierarchy_level for performance
CREATE INDEX idx_document_chunks_hierarchy_level ON document_chunks(hierarchy_level);

-- Add index for element_type
CREATE INDEX idx_document_chunks_element_type ON document_chunks(element_type);

-- Add index for parent_chunk_uuid
CREATE INDEX idx_document_chunks_parent_chunk_uuid ON document_chunks(parent_chunk_uuid);

-- Add index for source_document_uuid 
CREATE INDEX idx_document_chunks_source_document_uuid ON document_chunks(source_document_uuid);

-- Add comments for the new columns
COMMENT ON COLUMN document_chunks.chunk_id IS 'String representation of chunk identifier, consistent with Elasticsearch chunkId field';
COMMENT ON COLUMN document_chunks.source_document_uuid IS 'Direct UUID reference to source document for performance optimization';
COMMENT ON COLUMN document_chunks.parent_chunk_uuid IS 'Direct UUID reference to parent chunk for hierarchical navigation';
COMMENT ON COLUMN document_chunks.title IS 'Title or heading of the section this chunk belongs to';
COMMENT ON COLUMN document_chunks.hierarchy_level IS 'Depth level of this chunk in the document hierarchy (1=root, 2=child, etc.)';
COMMENT ON COLUMN document_chunks.element_type IS 'Type of the original document element (Title, Header, NarrativeText, etc.)';