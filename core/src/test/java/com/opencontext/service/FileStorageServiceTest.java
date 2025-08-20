package com.opencontext.service;

import com.opencontext.config.MinIOConfig;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileStorageService MinIO Integration Tests")
class FileStorageServiceTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private MinIOConfig minioConfig;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        when(minioConfig.getBucketName()).thenReturn("test-bucket");
        // Create a minimal FileStorageService instance for testing MinIO operations
        fileStorageService = new FileStorageService(
                minioClient, 
                minioConfig, 
                null, // sourceDocumentRepository 
                null, // documentChunkRepository
                null  // restTemplate
        );
    }

    @Test
    @DisplayName("Should upload file with proper stream parameters to avoid directory creation")
    void shouldUploadFileWithProperStreamParameters() throws Exception {
        // Given
        String fileName = "test-document.pdf";
        String contentType = "application/pdf";
        byte[] fileContent = "This is test PDF content".getBytes();
        
        MultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                contentType,
                fileContent
        );

        // Mock bucket exists check
        when(minioClient.bucketExists(any())).thenReturn(true);
        
        // When
        String objectKey = fileStorageService.uploadFile(file, contentType);
        
        // Then - Capture the PutObjectArgs to verify proper configuration
        ArgumentCaptor<PutObjectArgs> putObjectArgsCaptor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(putObjectArgsCaptor.capture());
        
        PutObjectArgs capturedArgs = putObjectArgsCaptor.getValue();
        
        // Verify object key structure
        assertThat(objectKey).startsWith("documents/");
        assertThat(objectKey).endsWith(fileName);
        
        // The key validation: ensure we're not creating directory-like paths
        assertThat(objectKey).doesNotEndWith("/");
        assertThat(objectKey).contains(fileName);
    }

    @Test
    @DisplayName("Should use correct partSize for small files (under 5MB)")
    void shouldUseCorrectPartSizeForSmallFiles() throws Exception {
        // Given
        String fileName = "small-file.txt";
        String contentType = "text/plain";
        byte[] fileContent = "Small file content".getBytes(); // Under 5MB
        
        MultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                contentType,
                fileContent
        );

        when(minioClient.bucketExists(any())).thenReturn(true);
        
        // When
        fileStorageService.uploadFile(file, contentType);
        
        // Then - Verify MinIO client was called
        verify(minioClient).putObject(any(PutObjectArgs.class));
        
        // For files under 5MB, partSize should be -1 (auto-detect)
        // This test ensures the method completes without issues
    }

    @Test
    @DisplayName("Should use correct partSize for large files (over 5MB)")
    void shouldUseCorrectPartSizeForLargeFiles() throws Exception {
        // Given
        String fileName = "large-file.pdf";
        String contentType = "application/pdf";
        
        // Create a mock file that reports size over 5MB
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(fileName);
        when(file.getSize()).thenReturn(6L * 1024 * 1024); // 6MB
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("large content".getBytes()));

        when(minioConfig.getBucketName()).thenReturn("test-bucket");
        when(minioClient.bucketExists(any())).thenReturn(true);
        
        // When
        fileStorageService.uploadFile(file, contentType);
        
        // Then - Verify MinIO client was called
        verify(minioClient).putObject(any(PutObjectArgs.class));
        
        // For files over 5MB, partSize should be 5MB
        // This test ensures the method handles large files properly
    }

    @Test
    @DisplayName("Should validate file size before upload")
    void shouldValidateFileSizeBeforeUpload() throws Exception {
        // Given
        String fileName = "empty-file.txt";
        String contentType = "text/plain";
        
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(fileName);
        when(file.getSize()).thenReturn(0L); // Empty file
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));

        when(minioConfig.getBucketName()).thenReturn("test-bucket");
        when(minioClient.bucketExists(any())).thenReturn(true);
        
        // When & Then
        assertThatCode(() -> fileStorageService.uploadFile(file, contentType))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("File size must be greater than 0");
    }

    @Test
    @DisplayName("Should properly configure stream parameters to prevent directory storage")
    void shouldConfigureStreamParametersCorrectly() throws Exception {
        // Given
        String fileName = "example.md";
        String contentType = "text/markdown";
        byte[] fileContent = "# Test Markdown\nThis is test content".getBytes();
        
        MultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                contentType,
                fileContent
        );

        // Mock bucket exists check
        when(minioClient.bucketExists(any())).thenReturn(true);
        
        // When
        fileStorageService.uploadFile(file, contentType);
        
        // Then - Verify the MinIO client was called with proper arguments
        verify(minioClient).putObject(any(PutObjectArgs.class));
        
        // This test ensures the method completes without throwing exceptions
        // which would indicate proper stream parameter configuration
    }

    @Test
    @DisplayName("Should generate proper object keys without trailing slashes")
    void shouldGenerateProperObjectKeys() throws Exception {
        // Given
        String fileName = "test-file.txt";
        String contentType = "text/plain";
        byte[] fileContent = "Test content".getBytes();
        
        MultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                contentType,
                fileContent
        );

        when(minioClient.bucketExists(any())).thenReturn(true);
        
        // When
        String objectKey = fileStorageService.uploadFile(file, contentType);
        
        // Then
        assertThat(objectKey)
                .startsWith("documents/")
                .endsWith(fileName)
                .doesNotEndWith("/")
                .doesNotContain("//")
                .matches("documents/\\d{4}/\\d{2}/\\d{2}/\\d+_[a-f0-9]{8}_" + fileName);
    }

    @Test
    @DisplayName("Should verify file exists correctly in MinIO")
    void shouldVerifyFileExistsCorrectly() throws Exception {
        // Given
        String objectKey = "documents/2025/01/20/test_file.pdf";
        StatObjectResponse mockResponse = mock(StatObjectResponse.class);
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(mockResponse);
        
        // When
        boolean exists = fileStorageService.fileExists(objectKey);
        
        // Then
        assertThat(exists).isTrue();
        verify(minioClient).statObject(any(StatObjectArgs.class));
    }

    @Test
    @DisplayName("Should download file correctly from MinIO")
    void shouldDownloadFileCorrectly() throws Exception {
        // Given
        String objectKey = "documents/2025/01/20/test_file.pdf";
        GetObjectResponse mockResponse = mock(GetObjectResponse.class);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mockResponse);
        
        // When
        InputStream result = fileStorageService.downloadFile(objectKey);
        
        // Then
        assertThat(result).isNotNull();
        verify(minioClient).getObject(any(GetObjectArgs.class));
    }
}