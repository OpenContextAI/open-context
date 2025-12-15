"""
Custom exceptions for OpenContext RAG Service.
"""
from fastapi import HTTPException, status


class RagServiceException(Exception):
    """Base exception for RAG service errors."""

    def __init__(self, message: str, status_code: int = status.HTTP_500_INTERNAL_SERVER_ERROR):
        self.message = message
        self.status_code = status_code
        super().__init__(self.message)


class DocumentParsingException(RagServiceException):
    """Exception raised when document parsing fails."""

    def __init__(self, message: str = "Document parsing failed"):
        super().__init__(message, status.HTTP_500_INTERNAL_SERVER_ERROR)


class ChunkingException(RagServiceException):
    """Exception raised when chunking fails."""

    def __init__(self, message: str = "Document chunking failed"):
        super().__init__(message, status.HTTP_500_INTERNAL_SERVER_ERROR)


class EmbeddingException(RagServiceException):
    """Exception raised when embedding generation fails."""

    def __init__(self, message: str = "Embedding generation failed"):
        super().__init__(message, status.HTTP_500_INTERNAL_SERVER_ERROR)


class IndexingException(RagServiceException):
    """Exception raised when Elasticsearch indexing fails."""

    def __init__(self, message: str = "Elasticsearch indexing failed"):
        super().__init__(message, status.HTTP_500_INTERNAL_SERVER_ERROR)


class SearchException(RagServiceException):
    """Exception raised when search fails."""

    def __init__(self, message: str = "Search operation failed"):
        super().__init__(message, status.HTTP_500_INTERNAL_SERVER_ERROR)


class ChunkNotFoundException(RagServiceException):
    """Exception raised when chunk is not found."""

    def __init__(self, chunk_id: str):
        message = f"Chunk not found: {chunk_id}"
        super().__init__(message, status.HTTP_404_NOT_FOUND)


class FileDownloadException(RagServiceException):
    """Exception raised when file download from presigned URL fails."""

    def __init__(self, message: str = "Failed to download file from presigned URL"):
        super().__init__(message, status.HTTP_500_INTERNAL_SERVER_ERROR)


def rag_exception_handler(exc: RagServiceException) -> HTTPException:
    """Convert RAG service exceptions to HTTP exceptions."""
    return HTTPException(status_code=exc.status_code, detail=exc.message)
