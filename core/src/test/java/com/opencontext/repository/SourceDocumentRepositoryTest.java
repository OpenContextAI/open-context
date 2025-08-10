package com.opencontext.repository;

import com.opencontext.entity.SourceDocument;
import com.opencontext.enums.IngestionStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DirtiesContext
@DisplayName("SourceDocumentRepository Integration Tests")
class SourceDocumentRepositoryTest {

    static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void initContainer() {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("test_db")
                .withUsername("test_user")
                .withPassword("test_password");
        postgres.start();
    }

    @AfterAll
    static void cleanupContainer() {
        if (postgres != null) {
            postgres.close();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private SourceDocumentRepository sourceDocumentRepository;

    @Test
    @DisplayName("Can save and retrieve SourceDocument by ID")
    @Transactional
    void canSaveAndRetrieveSourceDocumentById() {
        // Given
        SourceDocument document = createTestDocument("test.pdf", "unique-checksum-1");
        
        // When
        SourceDocument savedDocument = sourceDocumentRepository.save(document);
        Optional<SourceDocument> foundDocument = sourceDocumentRepository.findById(savedDocument.getId());
        
        // Then
        assertThat(foundDocument).isPresent();
        assertThat(foundDocument.get().getOriginalFilename()).isEqualTo("test.pdf");
        assertThat(foundDocument.get().getFileChecksum()).isEqualTo("unique-checksum-1");
        assertThat(foundDocument.get().getIngestionStatus()).isEqualTo(IngestionStatus.PENDING);
    }

    @Test
    @DisplayName("Can find document by file checksum")
    @Transactional
    void canFindDocumentByFileChecksum() {
        // Given
        String uniqueChecksum = "unique-checksum-for-find-test";
        SourceDocument document = createTestDocument("findme.pdf", uniqueChecksum);
        sourceDocumentRepository.save(document);
        
        // When
        Optional<SourceDocument> foundDocument = sourceDocumentRepository.findByFileChecksum(uniqueChecksum);
        
        // Then
        assertThat(foundDocument).isPresent();
        assertThat(foundDocument.get().getOriginalFilename()).isEqualTo("findme.pdf");
        assertThat(foundDocument.get().getFileChecksum()).isEqualTo(uniqueChecksum);
    }

    @Test
    @DisplayName("Returns empty when searching for non-existent checksum")
    @Transactional
    void returnsEmptyForNonExistentChecksum() {
        // Given
        String nonExistentChecksum = "non-existent-checksum";
        
        // When
        Optional<SourceDocument> foundDocument = sourceDocumentRepository.findByFileChecksum(nonExistentChecksum);
        
        // Then
        assertThat(foundDocument).isEmpty();
    }

    @Test
    @DisplayName("Can find documents by ingestion status")
    @Transactional
    void canFindDocumentsByIngestionStatus() {
        // Given
        SourceDocument pendingDoc1 = createTestDocument("pending1.pdf", "checksum-1");
        SourceDocument pendingDoc2 = createTestDocument("pending2.pdf", "checksum-2");
        SourceDocument completedDoc = createTestDocument("completed.pdf", "checksum-3");
        completedDoc.updateIngestionStatus(IngestionStatus.COMPLETED);
        
        sourceDocumentRepository.saveAll(List.of(pendingDoc1, pendingDoc2, completedDoc));
        
        // When
        List<SourceDocument> pendingDocuments = sourceDocumentRepository.findByIngestionStatus(IngestionStatus.PENDING);
        List<SourceDocument> completedDocuments = sourceDocumentRepository.findByIngestionStatus(IngestionStatus.COMPLETED);
        
        // Then
        assertThat(pendingDocuments).hasSize(2);
        assertThat(pendingDocuments).extracting(SourceDocument::getOriginalFilename)
                .containsExactlyInAnyOrder("pending1.pdf", "pending2.pdf");
        
        assertThat(completedDocuments).hasSize(1);
        assertThat(completedDocuments.get(0).getOriginalFilename()).isEqualTo("completed.pdf");
    }

    @Test
    @DisplayName("Can count documents by ingestion status")
    @Transactional
    void canCountDocumentsByIngestionStatus() {
        // Given
        SourceDocument pendingDoc = createTestDocument("pending.pdf", "checksum-pending");
        SourceDocument errorDoc = createTestDocument("error.pdf", "checksum-error");
        errorDoc.updateIngestionStatusToError("Test error");
        
        sourceDocumentRepository.saveAll(List.of(pendingDoc, errorDoc));
        
        // When
        long pendingCount = sourceDocumentRepository.countByIngestionStatus(IngestionStatus.PENDING);
        long errorCount = sourceDocumentRepository.countByIngestionStatus(IngestionStatus.ERROR);
        long completedCount = sourceDocumentRepository.countByIngestionStatus(IngestionStatus.COMPLETED);
        
        // Then
        assertThat(pendingCount).isEqualTo(1L);
        assertThat(errorCount).isEqualTo(1L);
        assertThat(completedCount).isEqualTo(0L);
    }

    @Test
    @DisplayName("Can find documents by file type")
    @Transactional
    void canFindDocumentsByFileType() {
        // Given
        SourceDocument pdfDoc = createTestDocument("doc1.pdf", "checksum-pdf");
        pdfDoc = SourceDocument.builder()
                .originalFilename("doc1.pdf")
                .fileStoragePath("/doc1.pdf")
                .fileType("PDF")
                .fileSize(1024L)
                .fileChecksum("checksum-pdf")
                .build();
        
        SourceDocument markdownDoc = SourceDocument.builder()
                .originalFilename("doc2.md")
                .fileStoragePath("/doc2.md")
                .fileType("MARKDOWN")
                .fileSize(512L)
                .fileChecksum("checksum-md")
                .build();
        
        sourceDocumentRepository.saveAll(List.of(pdfDoc, markdownDoc));
        
        // When
        List<SourceDocument> pdfDocuments = sourceDocumentRepository.findByFileTypeIgnoreCase("PDF");
        List<SourceDocument> markdownDocuments = sourceDocumentRepository.findByFileTypeIgnoreCase("MARKDOWN");
        
        // Then
        assertThat(pdfDocuments).hasSize(1);
        assertThat(pdfDocuments.get(0).getOriginalFilename()).isEqualTo("doc1.pdf");
        
        assertThat(markdownDocuments).hasSize(1);
        assertThat(markdownDocuments.get(0).getOriginalFilename()).isEqualTo("doc2.md");
    }

    @Test
    @DisplayName("Can check if document exists by checksum")
    @Transactional
    void canCheckIfDocumentExistsByChecksum() {
        // Given
        String existingChecksum = "existing-checksum";
        String nonExistingChecksum = "non-existing-checksum";
        SourceDocument document = createTestDocument("existing.pdf", existingChecksum);
        sourceDocumentRepository.save(document);
        
        // When
        boolean exists = sourceDocumentRepository.existsByFileChecksum(existingChecksum);
        boolean doesNotExist = sourceDocumentRepository.existsByFileChecksum(nonExistingChecksum);
        
        // Then
        assertThat(exists).isTrue();
        assertThat(doesNotExist).isFalse();
    }

    @Test
    @DisplayName("Can find documents created within time range")
    @Transactional
    void canFindDocumentsCreatedWithinTimeRange() {
        // Given
        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now().plusHours(1);
        
        SourceDocument recentDocument = createTestDocument("recent.pdf", "recent-checksum");
        sourceDocumentRepository.save(recentDocument);
        
        // When
        List<SourceDocument> documentsInRange = sourceDocumentRepository.findByCreatedAtBetween(startTime, endTime);
        
        // Then
        assertThat(documentsInRange).hasSize(1);
        assertThat(documentsInRange.get(0).getOriginalFilename()).isEqualTo("recent.pdf");
    }

    private SourceDocument createTestDocument(String filename, String checksum) {
        return SourceDocument.builder()
                .originalFilename(filename)
                .fileStoragePath("/" + filename)
                .fileType("PDF")
                .fileSize(1024L)
                .fileChecksum(checksum)
                .ingestionStatus(IngestionStatus.PENDING)
                .build();
    }
}