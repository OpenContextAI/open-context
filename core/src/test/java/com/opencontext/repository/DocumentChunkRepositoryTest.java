package com.opencontext.repository;

import com.opencontext.entity.DocumentChunk;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DirtiesContext
@DisplayName("DocumentChunkRepository Integration Tests")
class DocumentChunkRepositoryTest {

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
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private SourceDocumentRepository sourceDocumentRepository;

    @Test
    @DisplayName("Can save and retrieve DocumentChunk by ID")
    @Transactional
    void canSaveAndRetrieveDocumentChunkById() {
        // Given
        SourceDocument sourceDocument = createAndSaveSourceDocument("test.pdf", "checksum-1");
        DocumentChunk chunk = createTestChunk(sourceDocument, null, 1);
        
        // When
        DocumentChunk savedChunk = documentChunkRepository.save(chunk);
        Optional<DocumentChunk> foundChunk = documentChunkRepository.findById(savedChunk.getId());
        
        // Then
        assertThat(foundChunk).isPresent();
        assertThat(foundChunk.get().getSourceDocument()).isEqualTo(sourceDocument);
        assertThat(foundChunk.get().getSequenceInDocument()).isEqualTo(1);
        assertThat(foundChunk.get().getParentChunk()).isNull();
    }

    @Test
    @DisplayName("Can find chunks by source document ID")
    @Transactional
    void canFindChunksBySourceDocumentId() {
        // Given
        SourceDocument doc1 = createAndSaveSourceDocument("doc1.pdf", "checksum-1");
        SourceDocument doc2 = createAndSaveSourceDocument("doc2.pdf", "checksum-2");
        
        DocumentChunk chunk1 = createTestChunk(doc1, null, 1);
        DocumentChunk chunk2 = createTestChunk(doc1, null, 2);
        DocumentChunk chunk3 = createTestChunk(doc2, null, 1);
        
        documentChunkRepository.saveAll(List.of(chunk1, chunk2, chunk3));
        
        // When
        List<DocumentChunk> chunksForDoc1 = documentChunkRepository.findBySourceDocument(doc1);
        List<DocumentChunk> chunksForDoc2 = documentChunkRepository.findBySourceDocument(doc2);
        
        // Then
        assertThat(chunksForDoc1).hasSize(2);
        assertThat(chunksForDoc1).allMatch(chunk -> chunk.getSourceDocument().equals(doc1));
        
        assertThat(chunksForDoc2).hasSize(1);
        assertThat(chunksForDoc2.get(0).getSourceDocument()).isEqualTo(doc2);
    }

    @Test
    @DisplayName("Can find chunks by parent chunk (hierarchical relationship)")
    @Transactional
    void canFindChunksByParentChunk() {
        // Given
        SourceDocument sourceDocument = createAndSaveSourceDocument("test.pdf", "checksum-1");
        DocumentChunk parentChunk = createTestChunk(sourceDocument, null, 1);
        documentChunkRepository.save(parentChunk);
        
        DocumentChunk child1 = createTestChunk(sourceDocument, parentChunk, 2);
        DocumentChunk child2 = createTestChunk(sourceDocument, parentChunk, 3);
        DocumentChunk orphanChunk = createTestChunk(sourceDocument, null, 4);
        
        documentChunkRepository.saveAll(List.of(child1, child2, orphanChunk));
        
        // When
        List<DocumentChunk> childChunks = documentChunkRepository.findByParentChunk(parentChunk);
        List<DocumentChunk> rootChunks = documentChunkRepository.findByParentChunkIsNull();
        
        // Then
        assertThat(childChunks).hasSize(2);
        assertThat(childChunks).allMatch(chunk -> chunk.getParentChunk().equals(parentChunk));
        
        assertThat(rootChunks).hasSize(2); // parentChunk and orphanChunk
        assertThat(rootChunks).allMatch(chunk -> chunk.getParentChunk() == null);
    }

    @Test
    @DisplayName("Can find root chunks (chunks without parent)")
    @Transactional
    void canFindRootChunks() {
        // Given
        SourceDocument sourceDocument = createAndSaveSourceDocument("test.pdf", "checksum-1");
        
        DocumentChunk rootChunk1 = createTestChunk(sourceDocument, null, 1);
        DocumentChunk rootChunk2 = createTestChunk(sourceDocument, null, 2);
        
        documentChunkRepository.save(rootChunk1);
        documentChunkRepository.save(rootChunk2);
        
        DocumentChunk childChunk = createTestChunk(sourceDocument, rootChunk1, 3);
        documentChunkRepository.save(childChunk);
        
        // When
        List<DocumentChunk> rootChunks = documentChunkRepository.findByParentChunkIsNull();
        
        // Then
        assertThat(rootChunks).hasSize(2);
        assertThat(rootChunks).allMatch(DocumentChunk::isRootChunk);
    }

    @Test
    @DisplayName("Can find chunks ordered by sequence in document")
    @Transactional
    void canFindChunksOrderedBySequence() {
        // Given
        SourceDocument sourceDocument = createAndSaveSourceDocument("test.pdf", "checksum-1");
        
        DocumentChunk chunk3 = createTestChunk(sourceDocument, null, 3);
        DocumentChunk chunk1 = createTestChunk(sourceDocument, null, 1);
        DocumentChunk chunk2 = createTestChunk(sourceDocument, null, 2);
        
        // Save in random order
        documentChunkRepository.saveAll(List.of(chunk3, chunk1, chunk2));
        
        // When
        List<DocumentChunk> orderedChunks = documentChunkRepository.findBySourceDocumentOrderBySequenceInDocumentAsc(sourceDocument);
        
        // Then
        assertThat(orderedChunks).hasSize(3);
        assertThat(orderedChunks.get(0).getSequenceInDocument()).isEqualTo(1);
        assertThat(orderedChunks.get(1).getSequenceInDocument()).isEqualTo(2);
        assertThat(orderedChunks.get(2).getSequenceInDocument()).isEqualTo(3);
    }

    @Test
    @DisplayName("Can count chunks by source document")
    @Transactional
    void canCountChunksBySourceDocument() {
        // Given
        SourceDocument doc1 = createAndSaveSourceDocument("doc1.pdf", "checksum-1");
        SourceDocument doc2 = createAndSaveSourceDocument("doc2.pdf", "checksum-2");
        
        DocumentChunk chunk1 = createTestChunk(doc1, null, 1);
        DocumentChunk chunk2 = createTestChunk(doc1, null, 2);
        DocumentChunk chunk3 = createTestChunk(doc2, null, 1);
        
        documentChunkRepository.saveAll(List.of(chunk1, chunk2, chunk3));
        
        // When
        long countForDoc1 = documentChunkRepository.countBySourceDocument(doc1);
        long countForDoc2 = documentChunkRepository.countBySourceDocument(doc2);
        
        // Then
        assertThat(countForDoc1).isEqualTo(2L);
        assertThat(countForDoc2).isEqualTo(1L);
    }

    @Test
    @DisplayName("Can check if chunks exist for source document")
    @Transactional
    void canCheckIfChunksExistForSourceDocument() {
        // Given
        SourceDocument docWithChunks = createAndSaveSourceDocument("with-chunks.pdf", "checksum-1");
        SourceDocument docWithoutChunks = createAndSaveSourceDocument("without-chunks.pdf", "checksum-2");
        
        DocumentChunk chunk = createTestChunk(docWithChunks, null, 1);
        documentChunkRepository.save(chunk);
        
        // When
        boolean hasChunks = documentChunkRepository.existsBySourceDocument(docWithChunks);
        boolean hasNoChunks = documentChunkRepository.existsBySourceDocument(docWithoutChunks);
        
        // Then
        assertThat(hasChunks).isTrue();
        assertThat(hasNoChunks).isFalse();
    }

    @Test
    @DisplayName("Cascading delete works when source document is deleted")
    @Transactional
    void cascadingDeleteWorksWhenSourceDocumentIsDeleted() {
        // Given
        SourceDocument sourceDocument = createAndSaveSourceDocument("test.pdf", "checksum-1");
        DocumentChunk chunk1 = createTestChunk(sourceDocument, null, 1);
        DocumentChunk chunk2 = createTestChunk(sourceDocument, null, 2);
        
        documentChunkRepository.saveAll(List.of(chunk1, chunk2));
        
        UUID sourceDocumentId = sourceDocument.getId();
        long initialChunkCount = documentChunkRepository.countBySourceDocument(sourceDocument);
        assertThat(initialChunkCount).isEqualTo(2L);
        
        // When
        sourceDocumentRepository.delete(sourceDocument);
        sourceDocumentRepository.flush(); // Ensure deletion is executed
        
        // Then
        boolean sourceExists = sourceDocumentRepository.existsById(sourceDocumentId);
        long remainingChunkCount = documentChunkRepository.count();
        
        assertThat(sourceExists).isFalse();
        assertThat(remainingChunkCount).isEqualTo(0L); // All chunks should be deleted due to CASCADE
    }

    private SourceDocument createAndSaveSourceDocument(String filename, String checksum) {
        SourceDocument document = SourceDocument.builder()
                .originalFilename(filename)
                .fileStoragePath("/" + filename)
                .fileType("PDF")
                .fileSize(1024L)
                .fileChecksum(checksum)
                .ingestionStatus(IngestionStatus.PENDING)
                .build();
        return sourceDocumentRepository.save(document);
    }

    private DocumentChunk createTestChunk(SourceDocument sourceDocument, DocumentChunk parentChunk, int sequence) {
        return DocumentChunk.builder()
                .sourceDocument(sourceDocument)
                .parentChunk(parentChunk)
                .sequenceInDocument(sequence)
                .build();
    }
}