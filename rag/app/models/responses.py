"""
Pydantic response models for OpenContext RAG API endpoints.
"""
from pydantic import BaseModel, Field
from typing import List, Optional


class SearchResultItem(BaseModel):
    """Individual search result item."""

    chunkId: str = Field(
        ...,
        description="Unique identifier for the chunk"
    )
    title: str = Field(
        ...,
        description="Section heading or title"
    )
    snippet: str = Field(
        ...,
        description="Content preview (first 50 characters)"
    )
    relevanceScore: float = Field(
        ...,
        ge=0.0,
        le=1.0,
        description="Normalized relevance score (0.0-1.0)"
    )

    class Config:
        json_schema_extra = {
            "example": {
                "chunkId": "550e8400-e29b-41d4-a716-446655440000-chunk-0",
                "title": "Introduction",
                "snippet": "This document describes the configuration...",
                "relevanceScore": 0.95
            }
        }


class SearchResponse(BaseModel):
    """Response model for search endpoint."""

    results: List[SearchResultItem] = Field(
        default_factory=list,
        description="List of search results ordered by relevance"
    )

    class Config:
        json_schema_extra = {
            "example": {
                "results": [
                    {
                        "chunkId": "550e8400-e29b-41d4-a716-446655440000-chunk-0",
                        "title": "Introduction",
                        "snippet": "This document describes the configuration...",
                        "relevanceScore": 0.95
                    }
                ]
            }
        }


class TokenInfo(BaseModel):
    """Token counting information for content retrieval."""

    tokenizer: str = Field(
        default="tiktoken-cl100k_base",
        description="Tokenizer used for counting"
    )
    actualTokens: int = Field(
        ...,
        ge=0,
        description="Actual token count of returned content"
    )

    class Config:
        json_schema_extra = {
            "example": {
                "tokenizer": "tiktoken-cl100k_base",
                "actualTokens": 1250
            }
        }


class GetContentResponse(BaseModel):
    """Response model for content retrieval endpoint."""

    content: str = Field(
        ...,
        description="Full chunk content (may be truncated if exceeds token limit)"
    )
    tokenInfo: TokenInfo = Field(
        ...,
        description="Token counting details"
    )

    class Config:
        json_schema_extra = {
            "example": {
                "content": "# Introduction\n\nThis is the full content of the chunk...",
                "tokenInfo": {
                    "tokenizer": "tiktoken-cl100k_base",
                    "actualTokens": 1250
                }
            }
        }


class ProcessResponse(BaseModel):
    """Response model for document processing endpoint."""

    success: bool = Field(
        ...,
        description="Whether processing completed successfully"
    )
    documentId: str = Field(
        ...,
        description="Document identifier"
    )
    chunksProcessed: int = Field(
        ...,
        ge=0,
        description="Number of chunks created and indexed"
    )
    status: str = Field(
        ...,
        description="Processing status (COMPLETED or ERROR)"
    )
    errorMessage: Optional[str] = Field(
        default=None,
        description="Error message if processing failed"
    )

    class Config:
        json_schema_extra = {
            "example": {
                "success": True,
                "documentId": "550e8400-e29b-41d4-a716-446655440000",
                "chunksProcessed": 15,
                "status": "COMPLETED",
                "errorMessage": None
            }
        }
