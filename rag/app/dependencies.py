"""
FastAPI dependency injection for services.
Provides singleton instances of services for efficient resource management.
"""
from functools import lru_cache
from elasticsearch import AsyncElasticsearch

from app.config.settings import settings
from app.services.embedding import EmbeddingService
from app.services.indexing import IndexingService
from app.services.search import SearchService


@lru_cache()
def get_elasticsearch_client() -> AsyncElasticsearch:
    """
    Get singleton Elasticsearch client with authentication and SSL support.

    Returns:
        AsyncElasticsearch client instance
    """
    # Build connection parameters
    client_params = {"hosts": [settings.elasticsearch_url]}

    # Add authentication if provided
    if settings.elasticsearch_username and settings.elasticsearch_password:
        client_params["basic_auth"] = (
            settings.elasticsearch_username,
            settings.elasticsearch_password
        )

    # Configure SSL verification
    if settings.elasticsearch_url.startswith("https"):
        client_params["verify_certs"] = settings.elasticsearch_verify_certs
        if not settings.elasticsearch_verify_certs:
            # Suppress SSL warnings in development
            import urllib3
            urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

    return AsyncElasticsearch(**client_params)


@lru_cache()
def get_embedding_service() -> EmbeddingService:
    """
    Get singleton embedding service.

    Returns:
        EmbeddingService instance
    """
    return EmbeddingService(
        ollama_url=settings.ollama_url,
        model_name=settings.ollama_model,
        batch_size=settings.embedding_batch_size
    )


@lru_cache()
def get_indexing_service() -> IndexingService:
    """
    Get singleton indexing service.

    Returns:
        IndexingService instance
    """
    es_client = get_elasticsearch_client()
    return IndexingService(
        es_client=es_client,
        index_name=settings.elasticsearch_index
    )


@lru_cache()
def get_search_service() -> SearchService:
    """
    Get singleton search service.

    Returns:
        SearchService instance
    """
    es_client = get_elasticsearch_client()
    embedding_service = get_embedding_service()
    return SearchService(
        es_client=es_client,
        index_name=settings.elasticsearch_index,
        embedding_service=embedding_service,
        bm25_weight=settings.bm25_weight,
        vector_weight=settings.vector_weight,
        snippet_max_length=settings.snippet_max_length
    )
