package com.opencontext.service;

import com.opencontext.config.MinIOConfig;
import com.opencontext.enums.ErrorCode;
import com.opencontext.exception.BusinessException;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for handling basic file storage operations with MinIO.
 * 
 * This service provides essential methods for storing and managing
 * files in MinIO object storage for the document ingestion pipeline.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final MinioClient minioClient;
    private final MinIOConfig minioConfig;

    /**
     * Uploads a file to MinIO storage and returns the object key.
     * 
     * @param file the multipart file to upload
     * @return the object key where the file was stored
     */
    public String uploadFile(MultipartFile file) {
        try {
            // Ensure bucket exists
            ensureBucketExists();

            // Generate unique object key
            String objectKey = generateObjectKey(file.getOriginalFilename());

            // Upload file to MinIO
            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build();

            minioClient.putObject(putObjectArgs);
            
            log.info("Successfully uploaded file: {} to bucket: {} with key: {}", 
                    file.getOriginalFilename(), minioConfig.getBucketName(), objectKey);

            return objectKey;

        } catch (Exception e) {
            log.error("Failed to upload file: {}", file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, 
                    "Failed to upload file to storage: " + e.getMessage());
        }
    }

    /**
     * Deletes a file from MinIO storage.
     * 
     * @param objectKey the object key of the file to delete
     */
    public void deleteFile(String objectKey) {
        try {
            RemoveObjectArgs removeObjectArgs = RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .build();

            minioClient.removeObject(removeObjectArgs);
            log.info("Successfully deleted file with key: {}", objectKey);

        } catch (Exception e) {
            log.error("Failed to delete file with key: {}", objectKey, e);
            throw new BusinessException(ErrorCode.FILE_DELETE_FAILED, 
                    "Failed to delete file: " + e.getMessage());
        }
    }

    /**
     * Checks if a file exists in MinIO storage.
     * 
     * @param objectKey the object key to check
     * @return true if file exists, false otherwise
     */
    public boolean fileExists(String objectKey) {
        try {
            StatObjectArgs statObjectArgs = StatObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .build();

            minioClient.statObject(statObjectArgs);
            return true;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Ensures that the configured bucket exists, creating it if necessary.
     */
    private void ensureBucketExists() {
        try {
            BucketExistsArgs bucketExistsArgs = BucketExistsArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .build();

            boolean bucketExists = minioClient.bucketExists(bucketExistsArgs);
            
            if (!bucketExists) {
                MakeBucketArgs makeBucketArgs = MakeBucketArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .build();
                
                minioClient.makeBucket(makeBucketArgs);
                log.info("Created MinIO bucket: {}", minioConfig.getBucketName());
            }

        } catch (Exception e) {
            log.error("Failed to ensure bucket exists: {}", minioConfig.getBucketName(), e);
            throw new BusinessException(ErrorCode.STORAGE_ERROR, 
                    "Failed to ensure bucket exists: " + e.getMessage());
        }
    }

    /**
     * Generates an object key for MinIO storage.
     * 
     * @param originalFilename the original filename
     * @return generated object key
     */
    private String generateObjectKey(String originalFilename) {
        LocalDateTime now = LocalDateTime.now();
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String timestamp = String.valueOf(System.currentTimeMillis());
        
        String filename = String.format("%s_%s_%s", timestamp, uuid, originalFilename);
        
        return String.format("documents/%d/%02d/%02d/%s", 
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(), filename);
    }
}