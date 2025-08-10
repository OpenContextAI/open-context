package com.opencontext.core.entity;

import com.opencontext.core.enums.IngestionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SourceDocument Entity Unit Tests")
class SourceDocumentTest {

    @Test
    @DisplayName("Can create SourceDocument using builder pattern")
    void canCreateSourceDocumentWithBuilder() {
        // Given
        String filename = "test-document.pdf";
        String storagePath = "/documents/test-document.pdf";
        String fileType = "PDF";
        Long fileSize = 1024L;
        String checksum = "abc123def456";
        
        // When
        SourceDocument document = SourceDocument.builder()
                .originalFilename(filename)
                .fileStoragePath(storagePath)
                .fileType(fileType)
                .fileSize(fileSize)
                .fileChecksum(checksum)
                .ingestionStatus(IngestionStatus.PENDING)
                .build();
        
        // Then
        assertThat(document.getOriginalFilename()).isEqualTo(filename);
        assertThat(document.getFileStoragePath()).isEqualTo(storagePath);
        assertThat(document.getFileType()).isEqualTo(fileType);
        assertThat(document.getFileSize()).isEqualTo(fileSize);
        assertThat(document.getFileChecksum()).isEqualTo(checksum);
        assertThat(document.getIngestionStatus()).isEqualTo(IngestionStatus.PENDING);
    }

    @Test
    @DisplayName("Can update ingestion status to ERROR with error message")
    void canUpdateIngestionStatusToError() {
        // Given
        SourceDocument document = createTestDocument();
        String errorMessage = "Parsing failed";
        
        // When
        document.updateIngestionStatusToError(errorMessage);
        
        // Then
        assertThat(document.getIngestionStatus()).isEqualTo(IngestionStatus.ERROR);
        assertThat(document.getErrorMessage()).isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("Updating status to COMPLETED sets lastIngestedAt and clears error message")
    void setsLastIngestedAtWhenCompletingIngestion() {
        // Given
        SourceDocument document = createTestDocument();
        LocalDateTime beforeUpdate = LocalDateTime.now().minusSeconds(1);
        
        // When
        document.updateIngestionStatus(IngestionStatus.COMPLETED);
        
        // Then
        LocalDateTime afterUpdate = LocalDateTime.now().plusSeconds(1);
        assertThat(document.getIngestionStatus()).isEqualTo(IngestionStatus.COMPLETED);
        assertThat(document.getErrorMessage()).isNull();
        assertThat(document.getLastIngestedAt()).isBetween(beforeUpdate, afterUpdate);
    }

    @Test
    @DisplayName("isProcessing method correctly identifies processing states")
    void isProcessingReturnsTrueForProcessingStatuses() {
        // Given
        SourceDocument document = createTestDocument();
        
        // When & Then - Processing statuses
        document.updateIngestionStatus(IngestionStatus.PARSING);
        assertThat(document.isProcessing()).isTrue();
        
        document.updateIngestionStatus(IngestionStatus.CHUNKING);
        assertThat(document.isProcessing()).isTrue();
        
        document.updateIngestionStatus(IngestionStatus.EMBEDDING);
        assertThat(document.isProcessing()).isTrue();
        
        document.updateIngestionStatus(IngestionStatus.INDEXING);
        assertThat(document.isProcessing()).isTrue();
        
        document.updateIngestionStatus(IngestionStatus.DELETING);
        assertThat(document.isProcessing()).isTrue();
        
        // When & Then - Non-processing statuses
        document.updateIngestionStatus(IngestionStatus.PENDING);
        assertThat(document.isProcessing()).isFalse();
        
        document.updateIngestionStatus(IngestionStatus.COMPLETED);
        assertThat(document.isProcessing()).isFalse();
        
        document.updateIngestionStatusToError("Some error");
        assertThat(document.isProcessing()).isFalse();
    }

    @Test
    @DisplayName("Status check methods work correctly")
    void statusCheckMethodsWorkCorrectly() {
        // Given
        SourceDocument document = createTestDocument();
        
        // When & Then - COMPLETED status
        document.updateIngestionStatus(IngestionStatus.COMPLETED);
        assertThat(document.isCompleted()).isTrue();
        assertThat(document.hasError()).isFalse();
        
        // When & Then - ERROR status
        document.updateIngestionStatusToError("Test error");
        assertThat(document.isCompleted()).isFalse();
        assertThat(document.hasError()).isTrue();
        
        // When & Then - PENDING status
        document.updateIngestionStatus(IngestionStatus.PENDING);
        assertThat(document.isCompleted()).isFalse();
        assertThat(document.hasError()).isFalse();
    }

    private SourceDocument createTestDocument() {
        return SourceDocument.builder()
                .originalFilename("test.pdf")
                .fileStoragePath("/test.pdf")
                .fileType("PDF")
                .fileSize(1024L)
                .fileChecksum("test-checksum")
                .ingestionStatus(IngestionStatus.PENDING)
                .build();
    }
}