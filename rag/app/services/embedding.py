"""
Embedding service for generating semantic vectors using Ollama.
Uses LangChain Ollama integration for embeddings.
"""
import logging
import asyncio
from typing import List
from langchain_ollama import OllamaEmbeddings

from app.models.chunks import StructuredChunk
from app.utils.exceptions import EmbeddingException

logger = logging.getLogger(__name__)


class EmbeddingService:
    """Service for generating embeddings using Ollama."""

    def __init__(
        self,
        ollama_url: str,
        model_name: str,
        batch_size: int = 10
    ):
        """
        Initialize embedding service.

        Args:
            ollama_url: Ollama API URL
            model_name: Embedding model name (e.g., dengcao/Qwen3-Embedding-0.6B:F16)
            batch_size: Batch size for processing chunks
        """
        self.ollama_url = ollama_url
        self.model_name = model_name
        self.batch_size = batch_size

        # Initialize Ollama embeddings
        self.embeddings = OllamaEmbeddings(
            base_url=ollama_url,
            model=model_name
        )

        logger.info(
            f"Initialized EmbeddingService: url={ollama_url}, "
            f"model={model_name}, batch_size={batch_size}"
        )

    async def generate_embeddings(
        self,
        chunks: List[StructuredChunk]
    ) -> List[StructuredChunk]:
        """
        Generate embeddings for chunks in batches.

        Args:
            chunks: List of StructuredChunk objects

        Returns:
            List of chunks with embeddings attached

        Raises:
            EmbeddingException: If embedding generation fails
        """
        try:
            logger.info(f"Starting embedding generation for {len(chunks)} chunks")

            embedded_chunks = []

            # Process in batches
            for i in range(0, len(chunks), self.batch_size):
                batch = chunks[i:i + self.batch_size]
                logger.debug(
                    f"Processing batch {i // self.batch_size + 1}: "
                    f"chunks {i}-{min(i + self.batch_size, len(chunks))}"
                )

                # Prepare texts for embedding (title + content)
                texts = [self._prepare_text(chunk) for chunk in batch]

                # Generate embeddings (run synchronous call in thread pool)
                try:
                    vectors = await asyncio.to_thread(
                        self.embeddings.embed_documents,
                        texts
                    )
                    logger.debug(
                        f"Generated {len(vectors)} embeddings, "
                        f"dimension={len(vectors[0]) if vectors else 0}"
                    )
                except Exception as e:
                    logger.error(f"Ollama embedding generation failed: {str(e)}")
                    raise EmbeddingException(f"Ollama API error: {str(e)}")

                # Attach embeddings to chunks
                for chunk, vector in zip(batch, vectors):
                    # Convert to List[float] (LangChain returns list already)
                    chunk.embedding = vector
                    embedded_chunks.append(chunk)

                    logger.debug(
                        f"Embedded chunk: chunkId={chunk.chunkId}, "
                        f"vectorDim={len(vector)}"
                    )

            logger.info(
                f"Embedding generation completed: {len(embedded_chunks)} chunks embedded"
            )

            return embedded_chunks

        except EmbeddingException:
            raise
        except Exception as e:
            logger.error("Embedding generation failed", exc_info=True)
            raise EmbeddingException(f"Embedding generation failed: {str(e)}")

    async def generate_query_embedding(self, query: str) -> List[float]:
        """
        Generate embedding for a search query.

        Args:
            query: Search query text

        Returns:
            Embedding vector

        Raises:
            EmbeddingException: If embedding generation fails
        """
        try:
            logger.debug(f"Generating query embedding: query='{query[:50]}...'")

            # Generate query embedding (run synchronous call in thread pool)
            vector = await asyncio.to_thread(
                self.embeddings.embed_query,
                query
            )

            logger.debug(f"Generated query embedding: dimension={len(vector)}")

            return vector

        except Exception as e:
            logger.error("Query embedding generation failed", exc_info=True)
            raise EmbeddingException(f"Query embedding failed: {str(e)}")

    def _prepare_text(self, chunk: StructuredChunk) -> str:
        """
        Prepare text for embedding (title + content).
        Truncates to max 8000 tokens (safe limit for most embedding models).

        Args:
            chunk: Chunk to prepare

        Returns:
            Combined text for embedding (truncated if needed)
        """
        title = chunk.title if chunk.title else ""
        content = chunk.content if chunk.content else ""

        if title:
            text = f"Title: {title}\n{content}"
        else:
            text = content

        # Truncate to approximately 8000 tokens (assuming ~1.5 chars per token)
        # This prevents Ollama from crashing on very long texts
        MAX_CHARS = 12000  # ~8000 tokens
        if len(text) > MAX_CHARS:
            logger.warning(
                f"Text too long ({len(text)} chars), truncating to {MAX_CHARS} chars "
                f"for embedding (chunkId={chunk.chunkId})"
            )
            text = text[:MAX_CHARS]

        return text
