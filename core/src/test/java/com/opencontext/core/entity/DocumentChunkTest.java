package com.opencontext.core.entity;

import com.opencontext.core.enums.IngestionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DocumentChunk Entity Unit Tests")
class DocumentChunkTest {

    @Test
    @DisplayName("Can create DocumentChunk using builder pattern")
    void canCreateDocumentChunkWithBuilder() {
        // Given
        SourceDocument sourceDocument = createTestSourceDocument();
        int sequence = 1;
        
        // When
        DocumentChunk chunk = DocumentChunk.builder()
                .sourceDocument(sourceDocument)
                .sequenceInDocument(sequence)
                .build();
        
        // Then
        assertThat(chunk.getSourceDocument()).isEqualTo(sourceDocument);
        assertThat(chunk.getSequenceInDocument()).isEqualTo(sequence);
        assertThat(chunk.getParentChunk()).isNull();
    }

    @Test
    @DisplayName("Root chunk correctly identifies itself as root")
    void rootChunkIdentifiesAsRoot() {
        // Given
        SourceDocument sourceDocument = createTestSourceDocument();
        
        // When
        DocumentChunk rootChunk = DocumentChunk.builder()
                .sourceDocument(sourceDocument)
                .parentChunk(null)
                .sequenceInDocument(0)
                .build();
        
        // Then
        assertThat(rootChunk.isRootChunk()).isTrue();
        assertThat(rootChunk.getParentChunk()).isNull();
    }

    @Test
    @DisplayName("Child chunk correctly identifies parent relationship")
    void childChunkIdentifiesParentRelationship() {
        // Given
        SourceDocument sourceDocument = createTestSourceDocument();
        DocumentChunk parentChunk = createTestChunk(sourceDocument, null, 0);
        
        // When
        DocumentChunk childChunk = DocumentChunk.builder()
                .sourceDocument(sourceDocument)
                .parentChunk(parentChunk)
                .sequenceInDocument(1)
                .build();
        
        // Then
        assertThat(childChunk.isRootChunk()).isFalse();
        assertThat(childChunk.getParentChunk()).isEqualTo(parentChunk);
    }

    @Test
    @DisplayName("Can add child chunks and check hasChildren")
    void canAddChildChunksAndCheckHasChildren() {
        // Given
        SourceDocument sourceDocument = createTestSourceDocument();
        DocumentChunk parent = createTestChunk(sourceDocument, null, 0);
        DocumentChunk child1 = createTestChunk(sourceDocument, parent, 1);
        DocumentChunk child2 = createTestChunk(sourceDocument, parent, 2);
        
        // When
        parent.addChildChunk(child1);
        parent.addChildChunk(child2);
        
        // Then
        assertThat(parent.hasChildren()).isTrue();
        assertThat(parent.getChildChunks()).hasSize(2);
        assertThat(parent.getChildChunks()).containsExactly(child1, child2);
        assertThat(child1.hasChildren()).isFalse();
        assertThat(child2.hasChildren()).isFalse();
    }

    @Test
    @DisplayName("Hierarchy level calculation works correctly")
    void hierarchyLevelCalculationWorks() {
        // Given
        SourceDocument sourceDocument = createTestSourceDocument();
        DocumentChunk root = createTestChunk(sourceDocument, null, 0);
        DocumentChunk level1 = createTestChunk(sourceDocument, root, 1);
        DocumentChunk level2 = createTestChunk(sourceDocument, level1, 2);
        
        // When & Then
        assertThat(root.getHierarchyLevel()).isEqualTo(1);
        assertThat(level1.getHierarchyLevel()).isEqualTo(2);
        assertThat(level2.getHierarchyLevel()).isEqualTo(3);
    }

    @Test
    @DisplayName("getAncestors returns correct ancestor hierarchy")
    void getAncestorsReturnsCorrectHierarchy() {
        // Given - Create a hierarchy: root -> level1 -> level2 -> level3
        SourceDocument sourceDocument = createTestSourceDocument();
        DocumentChunk root = createTestChunk(sourceDocument, null, 0);
        DocumentChunk level1 = createTestChunk(sourceDocument, root, 1);
        DocumentChunk level2 = createTestChunk(sourceDocument, level1, 2);
        DocumentChunk level3 = createTestChunk(sourceDocument, level2, 3);
        
        // When
        List<DocumentChunk> ancestorsOfLevel3 = level3.getAncestors();
        List<DocumentChunk> ancestorsOfLevel2 = level2.getAncestors();
        List<DocumentChunk> ancestorsOfRoot = root.getAncestors();
        
        // Then
        assertThat(ancestorsOfLevel3).hasSize(3);
        assertThat(ancestorsOfLevel3).containsExactly(root, level1, level2);
        
        assertThat(ancestorsOfLevel2).hasSize(2);
        assertThat(ancestorsOfLevel2).containsExactly(root, level1);
        
        assertThat(ancestorsOfRoot).isEmpty();
    }

    @Test
    @DisplayName("Empty child list returns hasChildren as false")
    void emptyChildListReturnsHasChildrenAsFalse() {
        // Given
        SourceDocument sourceDocument = createTestSourceDocument();
        DocumentChunk leafChunk = createTestChunk(sourceDocument, null, 0);
        
        // When & Then
        assertThat(leafChunk.hasChildren()).isFalse();
        assertThat(leafChunk.getChildChunks()).isEmpty();
    }

    private SourceDocument createTestSourceDocument() {
        return SourceDocument.builder()
                .originalFilename("test-document.pdf")
                .fileStoragePath("/test-document.pdf")
                .fileType("PDF")
                .fileSize(1024L)
                .fileChecksum("test-checksum")
                .ingestionStatus(IngestionStatus.PENDING)
                .build();
    }

    private DocumentChunk createTestChunk(SourceDocument sourceDocument, DocumentChunk parent, int sequence) {
        return DocumentChunk.builder()
                .sourceDocument(sourceDocument)
                .parentChunk(parent)
                .sequenceInDocument(sequence)
                .build();
    }
}