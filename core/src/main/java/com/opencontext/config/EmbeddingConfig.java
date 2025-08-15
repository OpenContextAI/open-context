package com.opencontext.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for embedding model integration.
 * Sets up Ollama embedding model for text vectorization.
 */
@Slf4j
@Configuration
public class EmbeddingConfig {

    @Value("${app.ollama.api.url}")
    private String ollamaBaseUrl;
    
    @Value("${app.ollama.embedding.model}")
    private String ollamaModel;

    /**
     * Creates and configures the Ollama embedding model bean.
     *  
     * @return configured EmbeddingModel instance
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("Configuring Ollama embedding model: {} at {}", ollamaModel, ollamaBaseUrl);
        
        return OllamaEmbeddingModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaModel)
                .build();
    }
}