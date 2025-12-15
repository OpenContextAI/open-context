"""
Search endpoint for hybrid search (BM25 + Vector).
Maintains API compatibility with existing Java implementation.
"""
import logging
from fastapi import APIRouter, Query, Depends

from app.models.responses import SearchResponse
from app.services.search import SearchService
from app.dependencies import get_search_service

logger = logging.getLogger(__name__)

router = APIRouter()


@router.get("/search", response_model=SearchResponse)
async def search(
    query: str = Query(..., min_length=1, description="Search query text"),
    topK: int = Query(50, ge=1, le=100, description="Number of top results"),
    search_service: SearchService = Depends(get_search_service)
):
    """
    Hybrid search endpoint combining BM25 keyword search and vector similarity.

    Args:
        query: Search query text
        topK: Number of top results to return (1-100)
        search_service: Injected search service

    Returns:
        SearchResponse with list of search results ordered by relevance
    """
    logger.info(f"Search request: query='{query[:50]}...', topK={topK}")

    # Perform search
    results = await search_service.search(query=query, top_k=topK)

    logger.info(f"Search completed: query='{query[:50]}...', results={len(results)}")

    return SearchResponse(results=results)
