"""
Data models for structured document chunks.
"""
from pydantic import BaseModel, Field
from typing import List, Dict, Any, Optional


class StructuredChunk(BaseModel):
    """Structured chunk model for RAG pipeline processing."""

    chunkId: str = Field(
        ...,
        description="Unique identifier for the chunk (format: {documentId}-chunk-{index})"
    )
    documentId: str = Field(
        ...,
        description="Parent document UUID"
    )
    content: str = Field(
        ...,
        description="Text content of the chunk"
    )
    title: str = Field(
        ...,
        description="Section heading or title (from H1 header)"
    )
    hierarchyLevel: int = Field(
        default=1,
        ge=1,
        description="Hierarchy level (1 for H1-based chunks)"
    )
    elementType: str = Field(
        default="TitleBasedChunk",
        description="Type of chunk (TitleBasedChunk for H1-split chunks)"
    )
    embedding: Optional[List[float]] = Field(
        default=None,
        description="1024-dimensional embedding vector (added after embedding generation)"
    )
    metadata: Dict[str, Any] = Field(
        default_factory=dict,
        description="Additional metadata (element counts, types, etc.)"
    )

    class Config:
        json_schema_extra = {
            "example": {
                "chunkId": "550e8400-e29b-41d4-a716-446655440000-chunk-0",
                "documentId": "550e8400-e29b-41d4-a716-446655440000",
                "content": "This is the content of the first section...",
                "title": "Introduction",
                "hierarchyLevel": 1,
                "elementType": "TitleBasedChunk",
                "embedding": None,
                "metadata": {
                    "chunk_type": "title_based",
                    "element_count": 5
                }
            }
        }
