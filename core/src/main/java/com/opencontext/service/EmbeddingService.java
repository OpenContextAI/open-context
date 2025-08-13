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
 * LangChain4j와 Ollama를 사용하여 텍스트 청크의 임베딩 벡터를 생성하는 서비스.
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
     * 구조화된 청크들의 임베딩 벡터를 생성합니다.
     * 
     * @param documentId 문서 ID
     * @param structuredChunks 임베딩을 생성할 청크 목록
     * @return 임베딩이 포함된 청크 목록
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

        // 배치 단위로 임베딩 생성
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
            
            log.info("✅ [EMBEDDING] Batch {}/{} completed: processed={}/{}, duration={}ms", 
                    currentBatch, batchCount, processedChunks, totalChunks, batchDuration);
        }

        long duration = System.currentTimeMillis() - startTime;
        int finalEmbeddedCount = embeddedChunks.size();
        
        log.info("🎉 [EMBEDDING] Embedding generation completed successfully: documentId={}, chunks={}, duration={}ms", 
                documentId, finalEmbeddedCount, duration);
        
        // 임베딩 통계 로깅
        if (finalEmbeddedCount > 0) {
            long avgEmbeddingTime = duration / finalEmbeddedCount;
            log.debug("📊 [EMBEDDING] Statistics: avgTimePerChunk={}ms, batchSize={}, totalBatches={}", 
                    avgEmbeddingTime, batchSize, batchCount);
        }
        
        return embeddedChunks;
    }

    /**
     * LangChain4j를 사용하여 청크 배치의 임베딩을 생성합니다.
     */
    private List<StructuredChunk> processBatchWithLangChain4j(List<StructuredChunk> batch) {
        long batchStartTime = System.currentTimeMillis();
        log.debug("🤖 [LANGCHAIN4J] Processing batch with LangChain4j: chunks={}", batch.size());
        
        List<StructuredChunk> result = new ArrayList<>();

        try {
            // 배치 처리를 위한 TextSegment 목록 생성
            List<TextSegment> textSegments = new ArrayList<>();
            for (StructuredChunk chunk : batch) {
                String textForEmbedding = prepareTextForEmbedding(chunk);
                TextSegment segment = TextSegment.from(textForEmbedding);
                textSegments.add(segment);
            }

            // LangChain4j를 사용한 배치 임베딩 생성
            log.debug("🚀 [LANGCHAIN4J] Calling Ollama embedding model: segments={}", textSegments.size());
            long embeddingStartTime = System.currentTimeMillis();
            Response<List<Embedding>> response = embeddingModel.embedAll(textSegments);
            List<Embedding> embeddings = response.content();
            long embeddingDuration = System.currentTimeMillis() - embeddingStartTime;
            
            log.info("✅ [LANGCHAIN4J] Ollama embedding completed: segments={}, duration={}ms, vectorDimension={}", 
                    textSegments.size(), embeddingDuration, 
                    embeddings.size() > 0 ? embeddings.get(0).dimension() : 0);

            // 결과 처리
            for (int i = 0; i < batch.size(); i++) {
                StructuredChunk chunk = batch.get(i);
                Embedding embedding = embeddings.get(i);
                
                // float[] 벡터를 List<Double>로 변환
                List<Double> embeddingVector = new ArrayList<>();
                float[] vector = embedding.vector();
                for (float value : vector) {
                    embeddingVector.add((double) value);
                }
                
                // 임베딩이 포함된 새로운 청크 생성
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
                
                log.debug("✅ [LANGCHAIN4J] Embedding processed for chunk: id={}, vectorSize={}, textLength={}", 
                        chunk.getChunkId(), embedding.dimension(), chunk.getContent().length());
            }

            long batchDuration = System.currentTimeMillis() - batchStartTime;
            log.info("✅ [LANGCHAIN4J] Batch processing completed: processed={}, duration={}ms, avgPerChunk={}ms", 
                    result.size(), batchDuration, batchDuration / batch.size());

        } catch (Exception e) {
            long batchDuration = System.currentTimeMillis() - batchStartTime;
            log.error("❌ [LANGCHAIN4J] Batch processing failed: chunks={}, duration={}ms, error={}", 
                    batch.size(), batchDuration, e.getMessage(), e);
            throw new BusinessException(ErrorCode.EMBEDDING_GENERATION_FAILED, 
                    "Failed to generate embeddings: " + e.getMessage());
        }

        return result;
    }

    /**
     * 임베딩 생성을 위한 텍스트를 준비합니다.
     * 제목과 내용을 결합하여 더 풍부한 컨텍스트를 제공합니다.
     */
    private String prepareTextForEmbedding(StructuredChunk chunk) {
        StringBuilder text = new StringBuilder();
        
        log.debug("📝 [EMBEDDING] Preparing text for embedding: chunkId={}", chunk.getChunkId());

        // 제목이 있으면 추가
        if (chunk.getTitle() != null && !chunk.getTitle().trim().isEmpty()) {
            text.append("Title: ").append(chunk.getTitle()).append("\n");
        }

        // 내용 추가
        text.append(chunk.getContent());

        String finalText = text.toString().trim();
        
        log.debug("✅ [EMBEDDING] Text prepared: chunkId={}, finalLength={}, hasTitle={}", 
                chunk.getChunkId(), finalText.length(), chunk.getTitle() != null);
        
        return finalText;
    }
}
