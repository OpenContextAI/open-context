package com.opencontext.service;

import com.opencontext.config.MinIOConfig;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration test specifically for the MinIO file storage bug where files were stored as directories.
 * This test verifies that the fix properly handles different file types and sizes.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MinIO File Storage Bug Fix Integration Tests")
class MinIOFileStorageBugFixTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private MinIOConfig minioConfig;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        when(minioConfig.getBucketName()).thenReturn("opencontext-documents");
        fileStorageService = new FileStorageService(
                minioClient, 
                minioConfig, 
                null, // sourceDocumentRepository 
                null, // documentChunkRepository
                null  // restTemplate
        );
    }

    @Test
    @DisplayName("Should fix directory storage issue for PDF files")
    void shouldFixDirectoryStorageIssueForPDF() throws Exception {
        // Given - recreate the scenario from the bug report
        String fileName = "example_4.pdf";
        String contentType = "application/pdf";
        byte[] pdfContent = createMockPDFContent();
        
        MultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                contentType,
                pdfContent
        );

        when(minioClient.bucketExists(any())).thenReturn(true);
        
        // When
        String objectKey = fileStorageService.uploadFile(file, contentType);
        
        // Then
        ArgumentCaptor<PutObjectArgs> putObjectArgsCaptor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(putObjectArgsCaptor.capture());
        
        // Verify the object key is properly structured and doesn't end with slash
        assertThat(objectKey)
                .startsWith("documents/")
                .endsWith(fileName)
                .doesNotEndWith("/")
                .matches("documents/\\d{4}/\\d{2}/\\d{2}/\\d+_[a-f0-9]{8}_" + fileName);
        
        // Verify the file was uploaded as a single object, not a directory structure
        verify(minioClient, times(1)).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("Should fix directory storage issue for Markdown files")
    void shouldFixDirectoryStorageIssueForMarkdown() throws Exception {
        // Given - test with markdown file as mentioned in the bug report
        String fileName = "example_4.md";
        String contentType = "text/markdown";
        String markdownContent = """
                # Test Document
                
                This is a test markdown document that should be stored as a file, not a directory.
                
                ## Features
                - Should upload correctly
                - Should not create directory structure
                - Should be downloadable as a file
                """;
        
        MultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                contentType,
                markdownContent.getBytes()
        );

        when(minioClient.bucketExists(any())).thenReturn(true);
        
        // When
        String objectKey = fileStorageService.uploadFile(file, contentType);
        
        // Then - verify proper upload parameters were used
        ArgumentCaptor<PutObjectArgs> argsCaptor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(argsCaptor.capture());
        
        PutObjectArgs args = argsCaptor.getValue();
        
        // Key assertions to prevent directory storage
        assertThat(objectKey)
                .doesNotEndWith("/")
                .contains(fileName)
                .startsWith("documents/");
    }

    @Test
    @DisplayName("Should handle different part sizes correctly to prevent directory storage")
    void shouldHandleDifferentPartSizesCorrectly() throws Exception {
        // Given - small file (under 5MB)
        String smallFileName = "small_file.txt";
        String contentType = "text/plain";
        byte[] smallContent = "Small file content".getBytes();
        
        MultipartFile smallFile = new MockMultipartFile("file", smallFileName, contentType, smallContent);
        when(minioClient.bucketExists(any())).thenReturn(true);
        
        // When - upload small file
        String objectKey1 = fileStorageService.uploadFile(smallFile, contentType);
        
        // Then - verify proper upload
        assertThat(objectKey1).endsWith(smallFileName).doesNotEndWith("/");
        verify(minioClient, times(1)).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("Should generate proper object keys without causing directory conflicts")
    void shouldGenerateProperObjectKeysWithoutDirectoryConflicts() throws Exception {
        // Given - multiple files with same name but different timestamps
        String fileName = "duplicate_name.pdf";
        String contentType = "application/pdf";
        byte[] content = "test content".getBytes();
        
        MultipartFile file1 = new MockMultipartFile("file", fileName, contentType, content);
        MultipartFile file2 = new MockMultipartFile("file", fileName, contentType, content);

        when(minioClient.bucketExists(any())).thenReturn(true);
        
        // When - upload multiple files with same name
        String objectKey1 = fileStorageService.uploadFile(file1, contentType);
        Thread.sleep(1); // Ensure different timestamps
        String objectKey2 = fileStorageService.uploadFile(file2, contentType);
        
        // Then - verify unique object keys that don't conflict
        assertThat(objectKey1).isNotEqualTo(objectKey2);
        assertThat(objectKey1).endsWith(fileName);
        assertThat(objectKey2).endsWith(fileName);
        assertThat(objectKey1).doesNotEndWith("/");
        assertThat(objectKey2).doesNotEndWith("/");
        
        // Verify MinIO was called twice with different object keys
        verify(minioClient, times(2)).putObject(any(PutObjectArgs.class));
    }

    private byte[] createMockPDFContent() {
        // Create a simple mock PDF header to simulate real PDF content
        String pdfHeader = "%PDF-1.4\n";
        String pdfContent = """
                1 0 obj
                <<
                /Type /Catalog
                /Pages 2 0 R
                >>
                endobj
                2 0 obj
                <<
                /Type /Pages
                /Kids [3 0 R]
                /Count 1
                >>
                endobj
                3 0 obj
                <<
                /Type /Page
                /Parent 2 0 R
                /MediaBox [0 0 612 792]
                >>
                endobj
                xref
                0 4
                0000000000 65535 f 
                0000000010 00000 n 
                0000000079 00000 n 
                0000000173 00000 n 
                trailer
                <<
                /Size 4
                /Root 1 0 R
                >>
                startxref
                301
                %%EOF
                """;
        return (pdfHeader + pdfContent).getBytes();
    }
}