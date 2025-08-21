package com.opencontext.service;

import com.opencontext.dto.StructuredChunk;
import com.opencontext.enums.ErrorCode;
import com.opencontext.exception.BusinessException;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for generating embedding vectors for text chunks using LangChain4j and Ollama.
 * 
 * Service for generating embedding vectors for text chunks using LangChain4j and Ollama.
 * 
 * Converts the semantic representation of each chunk into a vector to enable semantic search.
 */
@Slf4j
@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    @Value("${app.embedding.batch-size:10}")
    private int batchSize;

    public EmbeddingService(
            @Value("${app.ollama.api.url:http://localhost:11434}") String ollamaApiUrl,
            @Value("${app.ollama.embedding.model:nomic-embed-text}") String modelName) {
        
        this.embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl(ollamaApiUrl)
                .modelName(modelName)
                .build();
        
        log.info("Initialized LangChain4j OllamaEmbeddingModel with baseUrl: {}, model: {}", 
                ollamaApiUrl, modelName);
    }

    /**
     * Generates embedding vectors for structured chunks.
     * 
     * @param documentId Document ID
     * @param structuredChunks List of chunks to generate embeddings for
     * @return List of chunks with embeddings included
     */
    public List<StructuredChunk> generateEmbeddings(UUID documentId, List<StructuredChunk> structuredChunks) {
        long startTime = System.currentTimeMillis();
        int totalChunks = structuredChunks.size();
        
        log.info("🤖 [EMBEDDING] Starting embedding generation: documentId={}, chunks={}", documentId, totalChunks);

        List<StructuredChunk> embeddedChunks = new ArrayList<>();
        int processedChunks = 0;
        int batchCount = (int) Math.ceil((double) totalChunks / batchSize);
        
        log.debug("📦 [EMBEDDING] Processing {} chunks in {} batches (batchSize={})", 
                totalChunks, batchCount, batchSize);

        // Generate embeddings in batch units
        for (int i = 0; i < structuredChunks.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, structuredChunks.size());
            List<StructuredChunk> batch = structuredChunks.subList(i, endIndex);
            int currentBatch = (i / batchSize) + 1;
            
            log.debug("📦 [EMBEDDING] Processing batch {}/{}: chunks {}-{}", 
                    currentBatch, batchCount, i + 1, endIndex);
            
            long batchStartTime = System.currentTimeMillis();
            List<StructuredChunk> batchResult = processBatchWithLangChain4j(batch);
            embeddedChunks.addAll(batchResult);
            
            processedChunks += batch.size();
            long batchDuration = System.currentTimeMillis() - batchStartTime;
            
            log.info("[EMBEDDING] Batch {}/{} completed: processed={}/{}, duration={}ms", 
                    currentBatch, batchCount, processedChunks, totalChunks, batchDuration);
        }

        long duration = System.currentTimeMillis() - startTime;
        int finalEmbeddedCount = embeddedChunks.size();
        
        log.info("🎉 [EMBEDDING] Embedding generation completed successfully: documentId={}, chunks={}, duration={}ms", 
                documentId, finalEmbeddedCount, duration);
        
        // Log embedding statistics
        if (finalEmbeddedCount > 0) {
            long avgEmbeddingTime = duration / finalEmbeddedCount;
            log.debug("📊 [EMBEDDING] Statistics: avgTimePerChunk={}ms, batchSize={}, totalBatches={}", 
                    avgEmbeddingTime, batchSize, batchCount);
        }
        
        return embeddedChunks;
    }

    /**
     * Generates embeddings for a batch of chunks using LangChain4j.
     */
    private List<StructuredChunk> processBatchWithLangChain4j(List<StructuredChunk> batch) {
        long batchStartTime = System.currentTimeMillis();
        log.debug("🤖 [LANGCHAIN4J] Processing batch with LangChain4j: chunks={}", batch.size());
        
        List<StructuredChunk> result = new ArrayList<>();

        try {
            // Create TextSegment list for batch processing
            List<TextSegment> textSegments = new ArrayList<>();
            for (StructuredChunk chunk : batch) {
                String textForEmbedding = prepareTextForEmbedding(chunk);
                TextSegment segment = TextSegment.from(textForEmbedding);
                textSegments.add(segment);
            }

            // Generate batch embeddings using LangChain4j
            log.debug("[LANGCHAIN4J] Calling Ollama embedding model: segments={}", textSegments.size());
            long embeddingStartTime = System.currentTimeMillis();
            Response<List<Embedding>> response = embeddingModel.embedAll(textSegments);
            List<Embedding> embeddings = response.content();
            long embeddingDuration = System.currentTimeMillis() - embeddingStartTime;
            
            log.info("[LANGCHAIN4J] Ollama embedding completed: segments={}, duration={}ms, vectorDimension={}", 
                    textSegments.size(), embeddingDuration, 
                    embeddings.size() > 0 ? embeddings.get(0).dimension() : 0);

            // Process results
            for (int i = 0; i < batch.size(); i++) {
                StructuredChunk chunk = batch.get(i);
                Embedding embedding = embeddings.get(i);
                
                // Convert float[] vector to List<Double>
                List<Double> embeddingVector = new ArrayList<>();
                float[] vector = embedding.vector();
                for (float value : vector) {
                    embeddingVector.add((double) value);
                }
                
                // Create new chunk with embedding included
                StructuredChunk embeddedChunk = StructuredChunk.builder()
                        .chunkId(chunk.getChunkId())
                        .documentId(chunk.getDocumentId())
                        .content(chunk.getContent())
                        .title(chunk.getTitle())
                        .hierarchyLevel(chunk.getHierarchyLevel())
                        .parentChunkId(chunk.getParentChunkId())
                        .elementType(chunk.getElementType())
                        .metadata(chunk.getMetadata())
                        .embedding(embeddingVector)
                        .build();
                
                result.add(embeddedChunk);
                
                log.debug("[LANGCHAIN4J] Embedding processed for chunk: id={}, vectorSize={}, textLength={}", 
                        chunk.getChunkId(), embedding.dimension(), chunk.getContent().length());
            }

            long batchDuration = System.currentTimeMillis() - batchStartTime;
            log.info("[LANGCHAIN4J] Batch processing completed: processed={}, duration={}ms, avgPerChunk={}ms", 
                    result.size(), batchDuration, batchDuration / batch.size());

        } catch (Exception e) {
            long batchDuration = System.currentTimeMillis() - batchStartTime;
            log.error("[LANGCHAIN4J] Batch processing failed: chunks={}, duration={}ms, error={}", 
                    batch.size(), batchDuration, e.getMessage(), e);
            throw new BusinessException(ErrorCode.EMBEDDING_GENERATION_FAILED, 
                    "Failed to generate embeddings: " + e.getMessage());
        }

        return result;
    }

    /**
     * Prepares text for embedding generation.
     * Combines title and content to provide richer context.
     */
    private String prepareTextForEmbedding(StructuredChunk chunk) {
        StringBuilder text = new StringBuilder();
        
        log.debug("[EMBEDDING] Preparing text for embedding: chunkId={}", chunk.getChunkId());

        // Add title if exists
        if (chunk.getTitle() != null && !chunk.getTitle().trim().isEmpty()) {
            text.append("Title: ").append(chunk.getTitle()).append("\n");
        }

        // Add content
        text.append(chunk.getContent());

        String finalText = text.toString().trim();
        
        log.debug("[EMBEDDING] Text prepared: chunkId={}, finalLength={}, hasTitle={}", 
                chunk.getChunkId(), finalText.length(), chunk.getTitle() != null);
        
        return finalText;
    }
}
