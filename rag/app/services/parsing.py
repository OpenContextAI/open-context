"""
Document parsing service using LangChain PyMuPDF for PDF processing.
Downloads files from presigned URLs and parses them into structured elements.
"""
import logging
import httpx
import tempfile
import os
from typing import List, Dict, Any
from pathlib import Path

from langchain_community.document_loaders import PyMuPDFLoader
from app.utils.exceptions import DocumentParsingException, FileDownloadException

logger = logging.getLogger(__name__)


class DocumentParsingService:
    """Service for parsing PDF documents using PyMuPDF."""

    def __init__(self):
        """Initialize parsing service."""
        logger.info("Initialized DocumentParsingService with PyMuPDF")

    async def parse_document(
        self,
        file_url: str,
        filename: str,
        file_type: str
    ) -> List[Dict[str, Any]]:
        """
        Parse PDF document using PyMuPDF.

        Args:
            file_url: Presigned MinIO URL for downloading the file
            filename: Original filename
            file_type: Document type (currently only PDF is supported)

        Returns:
            List of parsed elements with type and text (compatible with chunking service)

        Raises:
            FileDownloadException: If file download fails
            DocumentParsingException: If parsing fails
        """
        temp_file_path = None

        try:
            logger.info(f"Starting PDF parsing: filename={filename}, type={file_type}")

            if file_type != "PDF":
                raise DocumentParsingException(
                    f"Only PDF files are currently supported. Got: {file_type}"
                )

            # Download file from presigned URL
            file_bytes = await self._download_file(file_url)
            logger.info(f"Downloaded file: size={len(file_bytes)} bytes")

            # Save to temporary file (PyMuPDFLoader requires file path)
            temp_file_path = await self._save_to_temp_file(file_bytes, filename)

            # Parse PDF using PyMuPDF
            elements = await self._parse_pdf(temp_file_path, filename)

            logger.info(f"Parsing completed: filename={filename}, elements={len(elements)}")

            return elements

        except FileDownloadException:
            raise
        except DocumentParsingException:
            raise
        except Exception as e:
            logger.error(f"Unexpected parsing error: filename={filename}", exc_info=True)
            raise DocumentParsingException(f"Parsing failed: {str(e)}")

        finally:
            # Clean up temporary file
            if temp_file_path and os.path.exists(temp_file_path):
                try:
                    os.unlink(temp_file_path)
                    logger.debug(f"Cleaned up temp file: {temp_file_path}")
                except Exception as e:
                    logger.warning(f"Failed to clean up temp file: {e}")

    async def _download_file(self, file_url: str) -> bytes:
        """
        Download file from presigned URL.

        Args:
            file_url: Presigned MinIO URL

        Returns:
            File bytes

        Raises:
            FileDownloadException: If download fails
        """
        try:
            async with httpx.AsyncClient(timeout=300.0) as client:
                response = await client.get(file_url)
                response.raise_for_status()
                return response.content

        except httpx.HTTPStatusError as e:
            logger.error(f"File download failed: status={e.response.status_code}, url={file_url}")
            raise FileDownloadException(
                f"File download failed with status {e.response.status_code}"
            )
        except Exception as e:
            logger.error(f"File download error: {str(e)}")
            raise FileDownloadException(f"File download failed: {str(e)}")

    async def _save_to_temp_file(self, file_bytes: bytes, filename: str) -> str:
        """
        Save file bytes to a temporary file.

        Args:
            file_bytes: File content
            filename: Original filename (used for extension)

        Returns:
            Path to temporary file

        Raises:
            DocumentParsingException: If file save fails
        """
        try:
            # Get file extension
            suffix = Path(filename).suffix or ".pdf"

            # Create temporary file
            with tempfile.NamedTemporaryFile(
                mode='wb',
                suffix=suffix,
                delete=False
            ) as temp_file:
                temp_file.write(file_bytes)
                temp_path = temp_file.name

            logger.debug(f"Saved to temp file: {temp_path}")
            return temp_path

        except Exception as e:
            logger.error(f"Failed to save temp file: {str(e)}")
            raise DocumentParsingException(f"Failed to save temp file: {str(e)}")

    async def _parse_pdf(self, file_path: str, filename: str) -> List[Dict[str, Any]]:
        """
        Parse PDF using PyMuPDF and convert to element format.

        Args:
            file_path: Path to PDF file
            filename: Original filename

        Returns:
            List of parsed elements compatible with chunking service

        Raises:
            DocumentParsingException: If parsing fails
        """
        try:
            # Load PDF with PyMuPDF
            loader = PyMuPDFLoader(file_path)
            documents = loader.load()

            logger.debug(f"PyMuPDF loaded {len(documents)} pages")

            # Convert LangChain documents to elements format
            elements = []
            for i, doc in enumerate(documents):
                # Each page becomes a "NarrativeText" element
                element = {
                    "type": "NarrativeText",
                    "text": doc.page_content,
                    "metadata": {
                        "page_number": i + 1,
                        "filename": filename,
                        "source": file_path,
                        **doc.metadata
                    }
                }
                elements.append(element)

            logger.info(f"Converted {len(elements)} pages to elements")

            return elements

        except Exception as e:
            logger.error(f"PDF parsing failed: {str(e)}", exc_info=True)
            raise DocumentParsingException(f"PDF parsing failed: {str(e)}")
