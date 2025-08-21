package com.opencontext.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing the hierarchical structure information of document chunks.
 * This entity stores only the relationships and structure, while the actual
 * content and search data are stored in Elasticsearch for performance.
 */
@Entity
@Table(name = "document_chunks")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DocumentChunk {

    /**
     * Primary key - unique identifier for each Structured Chunk.
     * This ID is identical to the chunkId in Elasticsearch for data consistency.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    /**
     * Chunk ID as string for consistency with Elasticsearch.
     */
    @Column(name = "chunk_id", nullable = false, unique = true, length = 255)
    private String chunkId;

    /**
     * Foreign key to the source document this chunk belongs to.
     * When parent document is deleted, related chunks are also deleted (CASCADE).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_id", nullable = false, foreignKey = @ForeignKey(name = "fk_chunk_source_document"))
    private SourceDocument sourceDocument;

    /**
     * Source document ID as UUID for direct access.
     */
    @Column(name = "source_document_uuid", nullable = false)
    private UUID sourceDocumentId;

    /**
     * Parent chunk ID as string for consistency with Elasticsearch.
     */
    @Column(name = "parent_chunk_id")
    private UUID parentChunkId;

    /**
     * Foreign key to the parent chunk for hierarchical structure.
     * This enables reconstruction of document's tree structure.
     * Root chunks have null parent_chunk_id.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_chunk_uuid", foreignKey = @ForeignKey(name = "fk_chunk_parent"))
    private DocumentChunk parentChunk;

    /**
     * Title or heading of the section this chunk belongs to.
     */
    @Column(name = "title", length = 500)
    private String title;

    /**
     * Hierarchy level of this chunk in the document structure.
     */
    @Column(name = "hierarchy_level", nullable = false)
    private Integer hierarchyLevel;

    /**
     * Type of the original document element (Title, Header, NarrativeText, etc.).
     */
    @Column(name = "element_type", length = 50)
    private String elementType;

    /**
     * Order sequence among sibling chunks with the same parent_chunk_id.
     * Used to maintain the original document structure and order.
     */
    @Column(name = "sequence_in_document", nullable = false)
    private Integer sequenceInDocument;

    /**
     * Timestamp when this chunk record was first created in database.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * One-to-many relationship to child chunks.
     * This enables easy navigation of the hierarchical structure.
     */
    @OneToMany(mappedBy = "parentChunk", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequenceInDocument ASC")
    @Builder.Default
    private List<DocumentChunk> childChunks = new ArrayList<>();

    /**
     * Adds a child chunk to this chunk, maintaining the hierarchical relationship.
     * 
     * @param childChunk the child chunk to add
     */
    public void addChildChunk(DocumentChunk childChunk) {
        childChunks.add(childChunk);
    }

    /**
     * Checks if this chunk is a root chunk (has no parent).
     * 
     * @return true if this chunk has no parent
     */
    public boolean isRootChunk() {
        return parentChunk == null;
    }

    /**
     * Checks if this chunk has any child chunks.
     * 
     * @return true if this chunk has children
     */
    public boolean hasChildren() {
        return childChunks != null && !childChunks.isEmpty();
    }

    /**
     * Gets the depth level of this chunk in the document hierarchy.
     * Root chunks are at level 1, their children at level 2, etc.
     * 
     * @return the depth level of this chunk
     */
    public int getHierarchyLevel() {
        if (isRootChunk()) {
            return 1;
        }
        return parentChunk.getHierarchyLevel() + 1;
    }

    /**
     * Gets all ancestor chunks from root to this chunk's parent.
     * 
     * @return list of ancestor chunks in order from root to parent
     */
    public List<DocumentChunk> getAncestors() {
        List<DocumentChunk> ancestors = new ArrayList<>();
        DocumentChunk current = this.parentChunk;
        while (current != null) {
            ancestors.add(0, current); // Add to beginning to maintain order
            current = current.getParentChunk();
        }
        return ancestors;
    }
}