"""
Hybrid search service combining BM25 keyword search and vector similarity.
Replicates Java SearchService logic.
"""
import logging
from typing import List
from elasticsearch import AsyncElasticsearch

from app.services.embedding import EmbeddingService
from app.models.responses import SearchResultItem
from app.utils.exceptions import SearchException, ChunkNotFoundException

logger = logging.getLogger(__name__)


class SearchService:
    """Service for hybrid search (BM25 + Vector similarity)."""

    def __init__(
        self,
        es_client: AsyncElasticsearch,
        index_name: str,
        embedding_service: EmbeddingService,
        bm25_weight: float = 0.3,
        vector_weight: float = 0.7,
        snippet_max_length: int = 50
    ):
        """
        Initialize search service.

        Args:
            es_client: Elasticsearch async client
            index_name: Index name for searching
            embedding_service: Service for generating query embeddings
            bm25_weight: Weight for BM25 keyword search (default: 0.3)
            vector_weight: Weight for vector similarity (default: 0.7)
            snippet_max_length: Maximum length for snippets (default: 50)
        """
        self.es = es_client
        self.index_name = index_name
        self.embedding_service = embedding_service
        self.bm25_weight = bm25_weight
        self.vector_weight = vector_weight
        self.snippet_max_length = snippet_max_length

        logger.info(
            f"Initialized SearchService: index={index_name}, "
            f"bm25Weight={bm25_weight}, vectorWeight={vector_weight}"
        )

    async def search(
        self,
        query: str,
        top_k: int = 50
    ) -> List[SearchResultItem]:
        """
        Perform hybrid search (BM25 + Vector).

        Args:
            query: Search query text
            top_k: Number of top results to return

        Returns:
            List of search result items

        Raises:
            SearchException: If search fails
        """
        try:
            logger.info(f"Starting hybrid search: query='{query[:50]}...', topK={top_k}")

            # Generate query embedding
            query_vector = await self.embedding_service.generate_query_embedding(query)
            logger.debug(f"Generated query embedding: dimension={len(query_vector)}")

            # Build hybrid query
            search_query = {
                "size": top_k,
                "query": {
                    "bool": {
                        "should": [
                            # BM25 keyword search
                            {
                                "multi_match": {
                                    "query": query,
                                    "fields": ["content^2", "metadata.title^1.5"],
                                    "type": "best_fields",
                                    "fuzziness": "AUTO",
                                    "boost": self.bm25_weight
                                }
                            },
                            # Vector similarity search
                            {
                                "script_score": {
                                    "query": {"match_all": {}},
                                    "script": {
                                        "source": "(cosineSimilarity(params.query_vector, 'embedding') + 1.0) * params.vector_weight",
                                        "params": {
                                            "query_vector": query_vector,
                                            "vector_weight": self.vector_weight
                                        }
                                    }
                                }
                            }
                        ]
                    }
                },
                "_source": ["chunkId", "metadata.title", "content", "sourceDocumentId"],
                "sort": [{"_score": {"order": "desc"}}]
            }

            # Execute search
            response = await self.es.search(
                index=self.index_name,
                body=search_query
            )

            hits = response["hits"]["hits"]
            logger.debug(f"Elasticsearch returned {len(hits)} results")

            # Parse results
            results = self._parse_search_results(hits)

            logger.info(f"Search completed: query='{query[:50]}...', results={len(results)}")

            return results

        except Exception as e:
            logger.error(f"Search failed: query='{query[:50]}...'", exc_info=True)
            raise SearchException(f"Search failed: {str(e)}")

    async def get_content(
        self,
        chunk_id: str,
        max_tokens: int = 25000
    ) -> tuple[str, int]:
        """
        Retrieve full content of a chunk with token limiting.

        Args:
            chunk_id: Chunk identifier
            max_tokens: Maximum tokens to return

        Returns:
            Tuple of (content, actual_token_count)

        Raises:
            ChunkNotFoundException: If chunk not found
            SearchException: If retrieval fails
        """
        try:
            logger.info(f"Retrieving content: chunkId={chunk_id}, maxTokens={max_tokens}")

            # Get chunk from Elasticsearch
            try:
                response = await self.es.get(
                    index=self.index_name,
                    id=chunk_id
                )
                source = response["_source"]
            except Exception as e:
                logger.error(f"Chunk not found: {chunk_id}")
                raise ChunkNotFoundException(chunk_id)

            content = source.get("content", "")

            # Import here to avoid circular dependency
            from app.utils.token_counter import token_counter

            # Count tokens
            token_count = token_counter.count_tokens(content)

            # Truncate if necessary
            if token_count > max_tokens:
                logger.info(
                    f"Truncating content: original={token_count} tokens, "
                    f"limit={max_tokens} tokens"
                )
                content, token_count = token_counter.truncate_to_token_limit(
                    content,
                    max_tokens,
                    preserve_beginning=True
                )

            logger.info(
                f"Content retrieved: chunkId={chunk_id}, "
                f"length={len(content)}, tokens={token_count}"
            )

            return content, token_count

        except ChunkNotFoundException:
            raise
        except Exception as e:
            logger.error(f"Content retrieval failed: chunkId={chunk_id}", exc_info=True)
            raise SearchException(f"Content retrieval failed: {str(e)}")

    def _parse_search_results(self, hits: List[dict]) -> List[SearchResultItem]:
        """
        Parse Elasticsearch hits into SearchResultItem objects.

        Args:
            hits: Elasticsearch search hits

        Returns:
            List of SearchResultItem objects
        """
        if not hits:
            return []

        # Find max score for normalization
        max_score = max(hit["_score"] for hit in hits) if hits else 1.0

        results = []
        for hit in hits:
            source = hit["_source"]
            score = hit["_score"]

            # Normalize score to 0.0-1.0 range
            normalized_score = score / max_score if max_score > 0 else 0.0

            result = SearchResultItem(
                chunkId=source.get("chunkId", ""),
                title=source.get("metadata", {}).get("title", "제목 없음"),
                snippet=self._generate_snippet(source.get("content", "")),
                relevanceScore=round(normalized_score, 4)
            )
            results.append(result)

        return results

    def _generate_snippet(self, content: str) -> str:
        """
        Generate snippet from content (first N characters).

        Args:
            content: Full content

        Returns:
            Snippet string
        """
        if not content:
            return ""

        if len(content) <= self.snippet_max_length:
            return content

        return content[:self.snippet_max_length] + "..."
