"""
Configuration settings for OpenContext RAG Service.
Uses Pydantic Settings for environment variable management.
"""
from pydantic_settings import BaseSettings
from pydantic import Field


class Settings(BaseSettings):
    """Application settings loaded from environment variables."""

    # FastAPI
    app_name: str = "OpenContext RAG Service"
    app_version: str = "1.0.0"
    api_prefix: str = "/api/v1"

    # Elasticsearch
    elasticsearch_url: str = Field(
        default="http://elasticsearch:9200",
        description="Elasticsearch connection URL"
    )
    elasticsearch_username: str = Field(
        default="",
        description="Elasticsearch username (optional)"
    )
    elasticsearch_password: str = Field(
        default="",
        description="Elasticsearch password (optional)"
    )
    elasticsearch_verify_certs: bool = Field(
        default=True,
        description="Verify SSL certificates for Elasticsearch"
    )
    elasticsearch_index: str = Field(
        default="document_chunks_index",
        description="Elasticsearch index name for document chunks"
    )

    # Ollama
    ollama_url: str = Field(
        default="http://ollama:11434",
        description="Ollama API URL for embeddings"
    )
    ollama_model: str = Field(
        default="dengcao/Qwen3-Embedding-0.6B:F16",
        description="Ollama embedding model (1024-dim, Korean+English)"
    )

    # Processing Configuration
    embedding_batch_size: int = Field(
        default=10,
        ge=1,
        le=100,
        description="Batch size for embedding generation"
    )
    snippet_max_length: int = Field(
        default=50,
        ge=10,
        le=500,
        description="Maximum length for search result snippets"
    )

    # Search Configuration
    bm25_weight: float = Field(
        default=0.3,
        ge=0.0,
        le=1.0,
        description="Weight for BM25 keyword search in hybrid search"
    )
    vector_weight: float = Field(
        default=0.7,
        ge=0.0,
        le=1.0,
        description="Weight for vector similarity in hybrid search"
    )

    # Content Retrieval
    default_max_tokens: int = Field(
        default=25000,
        ge=1,
        description="Default maximum tokens for content retrieval"
    )
    tokenizer: str = Field(
        default="tiktoken-cl100k_base",
        description="Tokenizer for token counting"
    )

    class Config:
        env_file = ".env"
        case_sensitive = False
        env_prefix = ""


# Global settings instance
settings = Settings()
