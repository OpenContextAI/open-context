"""
Elasticsearch indexing service for storing document chunks.
Stores chunks in Elasticsearch ONLY (no PostgreSQL storage).
"""
import logging
from datetime import datetime
from typing import List
from elasticsearch import AsyncElasticsearch
from elasticsearch.helpers import async_bulk

from app.models.chunks import StructuredChunk
from app.utils.exceptions import IndexingException

logger = logging.getLogger(__name__)


class IndexingService:
    """Service for indexing chunks to Elasticsearch."""

    def __init__(
        self,
        es_client: AsyncElasticsearch,
        index_name: str
    ):
        """
        Initialize indexing service.

        Args:
            es_client: Elasticsearch async client
            index_name: Index name for document chunks
        """
        self.es = es_client
        self.index_name = index_name

        logger.info(f"Initialized IndexingService: index={index_name}")

    async def index_chunks(
        self,
        chunks: List[StructuredChunk],
        file_type: str = "UNKNOWN"
    ) -> int:
        """
        Bulk index chunks to Elasticsearch.

        Args:
            chunks: List of StructuredChunk objects with embeddings
            file_type: Document file type (PDF, MARKDOWN, TEXT)

        Returns:
            Number of successfully indexed chunks

        Raises:
            IndexingException: If indexing fails
        """
        try:
            logger.info(f"Starting bulk indexing: {len(chunks)} chunks")

            if not chunks:
                logger.warning("No chunks to index")
                return 0

            # Prepare bulk actions
            actions = []
            for chunk in chunks:
                action = {
                    "_index": self.index_name,
                    "_id": chunk.chunkId,
                    "_source": self._prepare_document(chunk, file_type)
                }
                actions.append(action)

            # Perform bulk indexing
            success_count, failed_items = await async_bulk(
                self.es,
                actions,
                raise_on_error=False
            )

            if failed_items:
                logger.error(f"Bulk indexing partial failure: {len(failed_items)} failed")
                # Log first few failures for debugging
                for item in failed_items[:5]:
                    logger.error(f"Failed item: {item}")

            logger.info(
                f"Bulk indexing completed: success={success_count}, "
                f"failed={len(failed_items)}"
            )

            # Refresh index to make documents immediately searchable
            await self.es.indices.refresh(index=self.index_name)

            return success_count

        except Exception as e:
            logger.error("Elasticsearch indexing failed", exc_info=True)
            raise IndexingException(f"Indexing failed: {str(e)}")

    def _prepare_document(
        self,
        chunk: StructuredChunk,
        file_type: str
    ) -> dict:
        """
        Prepare Elasticsearch document from chunk.

        Args:
            chunk: StructuredChunk object
            file_type: Document file type

        Returns:
            Elasticsearch document dictionary
        """
        return {
            "chunkId": chunk.chunkId,
            "sourceDocumentId": chunk.documentId,
            "content": chunk.content,
            "embedding": chunk.embedding,  # 1024-dimensional vector
            "indexedAt": datetime.utcnow().isoformat(),
            "metadata": {
                "title": chunk.title,
                "hierarchyLevel": chunk.hierarchyLevel,
                "fileType": file_type,
                "language": "ko",  # Korean as primary language
                "elementType": chunk.elementType,
                **chunk.metadata  # Include original metadata
            }
        }

    async def delete_by_document_id(self, document_id: str) -> int:
        """
        Delete all chunks for a document.

        Args:
            document_id: Document UUID

        Returns:
            Number of deleted chunks

        Raises:
            IndexingException: If deletion fails
        """
        try:
            logger.info(f"Deleting chunks for document: {document_id}")

            query = {
                "query": {
                    "term": {
                        "sourceDocumentId": document_id
                    }
                }
            }

            response = await self.es.delete_by_query(
                index=self.index_name,
                body=query
            )

            deleted_count = response.get("deleted", 0)
            logger.info(f"Deleted {deleted_count} chunks for document {document_id}")

            return deleted_count

        except Exception as e:
            logger.error(f"Deletion failed for document {document_id}", exc_info=True)
            raise IndexingException(f"Deletion failed: {str(e)}")

    async def get_chunk_by_id(self, chunk_id: str) -> dict:
        """
        Retrieve chunk by ID.

        Args:
            chunk_id: Chunk identifier

        Returns:
            Chunk document

        Raises:
            IndexingException: If retrieval fails
        """
        try:
            response = await self.es.get(
                index=self.index_name,
                id=chunk_id
            )

            return response["_source"]

        except Exception as e:
            logger.error(f"Failed to retrieve chunk: {chunk_id}", exc_info=True)
            raise IndexingException(f"Chunk retrieval failed: {str(e)}")
