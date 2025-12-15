"""
Pydantic request models for OpenContext RAG API endpoints.
"""
from pydantic import BaseModel, Field
from typing import Optional


class ProcessDocumentRequest(BaseModel):
    """Request model for processing a document through the RAG pipeline."""

    documentId: str = Field(
        ...,
        description="Unique identifier for the document (UUID)"
    )
    fileUrl: str = Field(
        ...,
        description="Presigned MinIO URL for downloading the document"
    )
    filename: str = Field(
        ...,
        description="Original filename of the document"
    )
    fileType: str = Field(
        ...,
        description="Document file type (PDF, MARKDOWN, TEXT)"
    )

    class Config:
        json_schema_extra = {
            "example": {
                "documentId": "550e8400-e29b-41d4-a716-446655440000",
                "fileUrl": "http://minio:9000/opencontext-documents/2024/01/15/doc.pdf?X-Amz-Expires=3600",
                "filename": "sample-document.pdf",
                "fileType": "PDF"
            }
        }


class SearchRequest(BaseModel):
    """Request model for hybrid search (BM25 + Vector)."""

    query: str = Field(
        ...,
        min_length=1,
        description="Search query text"
    )
    topK: int = Field(
        default=50,
        ge=1,
        le=100,
        description="Number of top results to return"
    )

    class Config:
        json_schema_extra = {
            "example": {
                "query": "How to configure Elasticsearch?",
                "topK": 10
            }
        }


class GetContentRequest(BaseModel):
    """Request model for retrieving full content of a chunk."""

    chunkId: str = Field(
        ...,
        description="Unique identifier for the chunk"
    )
    maxTokens: Optional[int] = Field(
        default=25000,
        ge=1,
        description="Maximum tokens to return (content will be truncated if exceeds)"
    )

    class Config:
        json_schema_extra = {
            "example": {
                "chunkId": "550e8400-e29b-41d4-a716-446655440000-chunk-0",
                "maxTokens": 25000
            }
        }
