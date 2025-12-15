"""
API v1 router aggregator.
Combines all v1 endpoints into a single router.
"""
from fastapi import APIRouter

from app.api.v1 import process, search, content

# Create main v1 router
router = APIRouter()

# Include sub-routers
router.include_router(process.router, tags=["Process"])
router.include_router(search.router, tags=["Search"])
router.include_router(content.router, tags=["Content"])
