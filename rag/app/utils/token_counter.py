"""
Token counting utilities using tiktoken (cl100k_base encoder).
Provides accurate token counting for content retrieval with truncation support.
"""
import tiktoken
from typing import Optional


class TokenCounter:
    """Token counter using tiktoken cl100k_base encoder."""

    def __init__(self, encoding_name: str = "cl100k_base"):
        """
        Initialize token counter with specified encoding.

        Args:
            encoding_name: Tiktoken encoding name (default: cl100k_base for GPT-4)
        """
        self.encoding = tiktoken.get_encoding(encoding_name)
        self.encoding_name = encoding_name

    def count_tokens(self, text: str) -> int:
        """
        Count tokens in text using tiktoken.

        Args:
            text: Text to count tokens for

        Returns:
            Number of tokens
        """
        if not text:
            return 0
        return len(self.encoding.encode(text))

    def truncate_to_token_limit(
        self,
        text: str,
        max_tokens: int,
        preserve_beginning: bool = True
    ) -> tuple[str, int]:
        """
        Truncate text to fit within token limit using binary search.
        Replicates Java ContentRetrievalService logic.

        Args:
            text: Text to truncate
            max_tokens: Maximum tokens allowed
            preserve_beginning: If True, keep beginning of text; if False, keep end

        Returns:
            Tuple of (truncated_text, actual_token_count)
        """
        if not text:
            return "", 0

        # Check if truncation is needed
        total_tokens = self.count_tokens(text)
        if total_tokens <= max_tokens:
            return text, total_tokens

        # Binary search to find maximum length that fits within token limit
        left, right = 0, len(text)
        best_length = 0

        while left <= right:
            mid = (left + right) // 2
            if preserve_beginning:
                candidate = text[:mid]
            else:
                candidate = text[-mid:]

            token_count = self.count_tokens(candidate)

            if token_count <= max_tokens:
                best_length = mid
                left = mid + 1
            else:
                right = mid - 1

        # Return text at best length
        if preserve_beginning:
            truncated = text[:best_length]
        else:
            truncated = text[-best_length:]

        actual_tokens = self.count_tokens(truncated)
        return truncated, actual_tokens

    def estimate_tokens(self, text: str) -> int:
        """
        Estimate tokens using simplified heuristics (fallback method).
        Replicates Java ChunkingService token estimation logic.

        Args:
            text: Text to estimate tokens for

        Returns:
            Estimated token count
        """
        if not text:
            return 0

        # Count different character types
        korean_chars = sum(
            1 for c in text
            if '\uAC00' <= c <= '\uD7A3'  # Hangul syllables
        )
        english_chars = sum(
            1 for c in text
            if ('a' <= c <= 'z') or ('A' <= c <= 'Z')
        )
        other_chars = len(text) - korean_chars - english_chars

        # Word count for English text
        words = text.split()
        word_count = len(words)

        # Estimate tokens
        # - English: ~1.3 tokens per word
        # - Korean: ~1.5 tokens per character
        # - Other: ~0.8 tokens per character
        estimated_tokens = (
            word_count * 1.3 +
            korean_chars * 1.5 +
            other_chars * 0.8
        )

        return max(1, int(round(estimated_tokens)))


# Global token counter instance
token_counter = TokenCounter()
