"""
Chunking service for splitting parsed documents into structured chunks.
Uses paragraph-based chunking: splits on \\n\\n (double newline).
"""
import re
import logging
import uuid
from typing import List, Dict, Any
from app.models.chunks import StructuredChunk
from app.utils.exceptions import ChunkingException

logger = logging.getLogger(__name__)


class ChunkingService:
    """Service for creating paragraph-based chunks from parsed document elements."""

    def __init__(self, min_chunk_size: int = 50):
        """
        Initialize chunking service.

        Args:
            min_chunk_size: Minimum characters for a chunk (default 50)
        """
        self.min_chunk_size = min_chunk_size
        logger.info(f"Initialized ChunkingService: min_chunk_size={min_chunk_size}")

    def create_chunks(
        self,
        document_id: str,
        parsed_elements: List[Dict[str, Any]]
    ) -> List[StructuredChunk]:
        """
        Create chunks by splitting on paragraph breaks (\\n\\n).
        Each paragraph becomes a separate chunk for better RAG retrieval.

        Args:
            document_id: Document UUID
            parsed_elements: List of elements from PDF parsing

        Returns:
            List of StructuredChunk objects

        Raises:
            ChunkingException: If chunking fails
        """
        try:
            logger.info(f"Starting paragraph-based chunking: documentId={document_id}, elements={len(parsed_elements)}")

            # Step 1: Combine all elements into full text
            full_text = ""
            for element in parsed_elements:
                text = element.get("text", "").strip()
                if text:
                    full_text += text + "\n\n"

            if not full_text.strip():
                logger.warning("No text content found in parsed elements")
                return []

            # Step 2: Split by paragraph (\\n\\n)
            paragraphs = full_text.split("\n\n")

            # Step 3: Create chunks from paragraphs
            chunks = []
            chunk_index = 0

            for paragraph in paragraphs:
                paragraph = paragraph.strip()

                # Skip empty or very short paragraphs
                if len(paragraph) < self.min_chunk_size:
                    logger.debug(f"Skipping short paragraph: length={len(paragraph)}")
                    continue

                # Create chunk
                chunk_id = f"{document_id}-chunk-{chunk_index}"

                # Extract title (first line or first 50 chars)
                title = self._extract_title(paragraph)

                chunk = StructuredChunk(
                    chunkId=chunk_id,
                    documentId=document_id,
                    content=paragraph,
                    title=title,
                    hierarchyLevel=1,
                    elementType="ParagraphChunk",
                    embedding=None,
                    metadata={
                        "chunkIndex": chunk_index,
                        "length": len(paragraph),
                        "tokens": self._estimate_token_count(paragraph)
                    }
                )

                chunks.append(chunk)
                chunk_index += 1

                logger.debug(
                    f"Created chunk {chunk_index}: title='{title[:50]}...', "
                    f"length={len(paragraph)}, tokens=~{chunk.metadata['tokens']}"
                )

            # Log summary statistics
            if chunks:
                avg_length = sum(len(c.content) for c in chunks) / len(chunks)
                avg_tokens = sum(
                    self._estimate_token_count(c.content) for c in chunks
                ) / len(chunks)

                logger.info(
                    f"Chunking completed: documentId={document_id}, "
                    f"elements={len(parsed_elements)}, chunks={len(chunks)}, "
                    f"avgLength={int(avg_length)}, avgTokens={int(avg_tokens)}"
                )

                # Log each chunk summary
                logger.info("Chunk summary:")
                for i, chunk in enumerate(chunks):
                    tokens = self._estimate_token_count(chunk.content)
                    logger.info(
                        f"  {i+1}. Title: '{chunk.title}' | "
                        f"Length: {len(chunk.content)} | Tokens: ~{tokens}"
                    )

            return chunks

        except Exception as e:
            logger.error(f"Chunking failed: documentId={document_id}", exc_info=True)
            raise ChunkingException(f"Chunking failed: {str(e)}")

    def _extract_title(self, paragraph: str) -> str:
        """
        Extract title from paragraph (first line or first 50 chars).

        Args:
            paragraph: Paragraph text

        Returns:
            Title string
        """
        # Try to use first line as title
        lines = paragraph.split('\n')
        first_line = lines[0].strip()

        if len(first_line) > 0 and len(first_line) <= 100:
            return first_line

        # Otherwise use first 50 characters
        return paragraph[:50].strip() + ("..." if len(paragraph) > 50 else "")

    def _estimate_token_count(self, text: str) -> int:
        """
        Estimate token count using simple heuristics.
        Korean: ~1.5 tokens/char, English: ~1.3 tokens/word, Other: ~0.8 tokens/char

        Args:
            text: Text to estimate tokens for

        Returns:
            Estimated token count
        """
        if not text:
            return 0

        # Count character types
        korean_chars = sum(1 for c in text if '\uAC00' <= c <= '\uD7A3')
        english_chars = sum(1 for c in text if ('a' <= c <= 'z') or ('A' <= c <= 'Z'))
        other_chars = len(text) - korean_chars - english_chars

        # Count words for English
        words = text.split()
        word_count = len(words)

        # Estimate tokens
        estimated_tokens = (
            word_count * 1.3 +
            korean_chars * 1.5 +
            other_chars * 0.8
        )

        return max(1, int(round(estimated_tokens)))
