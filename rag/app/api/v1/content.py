"""
Content retrieval endpoint with token limiting.
Maintains API compatibility with existing Java implementation.
"""
import logging
from fastapi import APIRouter, Depends

from app.models.requests import GetContentRequest
from app.models.responses import GetContentResponse, TokenInfo
from app.services.search import SearchService
from app.config.settings import settings
from app.dependencies import get_search_service

logger = logging.getLogger(__name__)

router = APIRouter()


@router.post("/get-content", response_model=GetContentResponse)
async def get_content(
    request: GetContentRequest,
    search_service: SearchService = Depends(get_search_service)
):
    """
    Retrieve full content of a chunk with token limiting.

    Args:
        request: GetContentRequest with chunk ID and max tokens
        search_service: Injected search service

    Returns:
        GetContentResponse with content and token information
    """
    logger.info(
        f"Content retrieval request: chunkId={request.chunkId}, "
        f"maxTokens={request.maxTokens}"
    )

    # Retrieve content
    content, actual_tokens = await search_service.get_content(
        chunk_id=request.chunkId,
        max_tokens=request.maxTokens or settings.default_max_tokens
    )

    logger.info(
        f"Content retrieved: chunkId={request.chunkId}, "
        f"length={len(content)}, tokens={actual_tokens}"
    )

    # Prepare response
    token_info = TokenInfo(
        tokenizer=settings.tokenizer,
        actualTokens=actual_tokens
    )

    return GetContentResponse(
        content=content,
        tokenInfo=token_info
    )
