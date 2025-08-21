package com.opencontext.config;

import io.minio.MinioClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO client configuration for object storage operations.
 * 
 * This configuration creates a MinIO client bean that can be used throughout
 * the application for file upload, download, and management operations.
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinIOConfig {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketName;

    /**
     * Creates and configures the MinIO client bean.
     * 
     * @return configured MinioClient instance
     */
    @Bean
    public MinioClient minioClient() {
        log.info("Initializing MinIO client with endpoint: {}", endpoint);
        
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
