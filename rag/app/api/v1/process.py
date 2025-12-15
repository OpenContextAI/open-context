"""
Document processing endpoint for RAG pipeline.
Internal API called by core/ service after file upload.
"""
import logging
from fastapi import APIRouter, HTTPException

from app.models.requests import ProcessDocumentRequest
from app.models.responses import ProcessResponse
from app.services.parsing import DocumentParsingService
from app.services.chunking import ChunkingService
from app.services.embedding import EmbeddingService
from app.services.indexing import IndexingService
from app.config.settings import settings
from app.dependencies import get_embedding_service, get_indexing_service

logger = logging.getLogger(__name__)

router = APIRouter()


@router.post("/process", response_model=ProcessResponse)
async def process_document(request: ProcessDocumentRequest):
    """
    Process document through complete RAG pipeline.

    Pipeline:
    1. Download file from presigned URL
    2. Parse document (Unstructured.io)
    3. Chunk document (H1-based)
    4. Generate embeddings (Ollama)
    5. Index to Elasticsearch

    Args:
        request: ProcessDocumentRequest with file URL and metadata

    Returns:
        ProcessResponse with success status and chunk count
    """
    document_id = request.documentId

    try:
        logger.info(
            f"Starting RAG pipeline: documentId={document_id}, "
            f"filename={request.filename}, fileType={request.fileType}"
        )

        # Initialize services
        parsing_service = DocumentParsingService()
        chunking_service = ChunkingService()
        embedding_service = get_embedding_service()
        indexing_service = get_indexing_service()

        # Step 1: Parse document
        logger.info(f"[1/4] Parsing document: {request.filename}")
        parsed_elements = await parsing_service.parse_document(
            file_url=request.fileUrl,
            filename=request.filename,
            file_type=request.fileType
        )
        logger.info(f"Parsing completed: {len(parsed_elements)} elements")

        # Step 2: Chunk document
        logger.info(f"[2/4] Chunking document: {document_id}")
        chunks = chunking_service.create_chunks(
            document_id=document_id,
            parsed_elements=parsed_elements
        )
        logger.info(f"Chunking completed: {len(chunks)} chunks")

        if not chunks:
            logger.warning(f"No chunks created for document: {document_id}")
            return ProcessResponse(
                success=True,
                documentId=document_id,
                chunksProcessed=0,
                status="COMPLETED",
                errorMessage="No chunks created (empty document)"
            )

        # Step 3: Generate embeddings
        logger.info(f"[3/4] Generating embeddings for {len(chunks)} chunks")
        embedded_chunks = await embedding_service.generate_embeddings(chunks)
        logger.info(f"Embedding generation completed: {len(embedded_chunks)} chunks")

        # Step 4: Index to Elasticsearch
        logger.info(f"[4/4] Indexing {len(embedded_chunks)} chunks to Elasticsearch")
        indexed_count = await indexing_service.index_chunks(
            chunks=embedded_chunks,
            file_type=request.fileType
        )
        logger.info(f"Indexing completed: {indexed_count} chunks indexed")

        # Return success response
        logger.info(
            f"RAG pipeline completed successfully: documentId={document_id}, "
            f"chunks={indexed_count}"
        )

        return ProcessResponse(
            success=True,
            documentId=document_id,
            chunksProcessed=indexed_count,
            status="COMPLETED",
            errorMessage=None
        )

    except Exception as e:
        logger.error(
            f"RAG pipeline failed: documentId={document_id}, error={str(e)}",
            exc_info=True
        )

        return ProcessResponse(
            success=False,
            documentId=document_id,
            chunksProcessed=0,
            status="ERROR",
            errorMessage=str(e)
        )
