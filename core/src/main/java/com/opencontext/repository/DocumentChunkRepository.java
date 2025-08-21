package com.opencontext.repository;

import com.opencontext.entity.DocumentChunk;
import com.opencontext.entity.SourceDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for DocumentChunk entity operations.
 * Provides data access methods for hierarchical chunk structure management
 * and chunk relationship queries.
 */
@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    /**
     * Finds all chunks belonging to a specific source document.
     * 
     * @param sourceDocument the source document to find chunks for
     * @return list of chunks belonging to the source document
     */
    List<DocumentChunk> findBySourceDocument(SourceDocument sourceDocument);

    /**
     * Finds all chunks belonging to a source document, ordered by sequence.
     * 
     * @param sourceDocument the source document to find chunks for
     * @return list of chunks ordered by sequence in document
     */
    List<DocumentChunk> findBySourceDocumentOrderBySequenceInDocumentAsc(SourceDocument sourceDocument);

    /**
     * Finds all child chunks of a specific parent chunk.
     * 
     * @param parentChunk the parent chunk to find children for
     * @return list of child chunks
     */
    List<DocumentChunk> findByParentChunk(DocumentChunk parentChunk);

    /**
     * Finds all root chunks (chunks without parent) for a source document.
     * 
     * @param sourceDocument the source document to find root chunks for
     * @return list of root chunks
     */
    @Query("SELECT dc FROM DocumentChunk dc WHERE dc.sourceDocument = :sourceDocument AND dc.parentChunk IS NULL ORDER BY dc.sequenceInDocument ASC")
    List<DocumentChunk> findRootChunksBySourceDocument(@Param("sourceDocument") SourceDocument sourceDocument);

    /**
     * Finds all root chunks (chunks without parent).
     * 
     * @return list of all root chunks
     */
    List<DocumentChunk> findByParentChunkIsNull();

    /**
     * Finds all child chunks ordered by sequence.
     * 
     * @param parentChunk the parent chunk to find children for
     * @return list of child chunks ordered by sequence
     */
    List<DocumentChunk> findByParentChunkOrderBySequenceInDocumentAsc(DocumentChunk parentChunk);

    /**
     * Counts the total number of chunks for a source document.
     * 
     * @param sourceDocument the source document to count chunks for
     * @return count of chunks belonging to the source document
     */
    long countBySourceDocument(SourceDocument sourceDocument);

    /**
     * Counts the number of child chunks for a parent chunk.
     * 
     * @param parentChunk the parent chunk to count children for
     * @return count of child chunks
     */
    long countByParentChunk(DocumentChunk parentChunk);

    /**
     * Checks if any chunks exist for a source document.
     * 
     * @param sourceDocument the source document to check
     * @return true if chunks exist for the source document
     */
    boolean existsBySourceDocument(SourceDocument sourceDocument);

    /**
     * Finds chunks by depth/hierarchy level using recursive query.
     * This uses a Common Table Expression (CTE) to traverse the hierarchy.
     * 
     * @param sourceDocument the source document to search within
     * @param hierarchyLevel the hierarchy level to filter by (1 = root, 2 = first level children, etc.)
     * @return list of chunks at the specified hierarchy level
     */
    @Query(value = """
        WITH RECURSIVE chunk_hierarchy AS (
            SELECT id, source_document_id, parent_chunk_id, sequence_in_document, 1 as level
            FROM document_chunks 
            WHERE source_document_id = :#{#sourceDocument.id} AND parent_chunk_id IS NULL
            
            UNION ALL
            
            SELECT dc.id, dc.source_document_id, dc.parent_chunk_id, dc.sequence_in_document, ch.level + 1
            FROM document_chunks dc
            INNER JOIN chunk_hierarchy ch ON dc.parent_chunk_id = ch.id
        )
        SELECT dc.* FROM document_chunks dc
        INNER JOIN chunk_hierarchy ch ON dc.id = ch.id
        WHERE ch.level = :hierarchyLevel
        ORDER BY dc.sequence_in_document ASC
        """, nativeQuery = true)
    List<DocumentChunk> findChunksByHierarchyLevel(@Param("sourceDocument") SourceDocument sourceDocument, 
                                                    @Param("hierarchyLevel") int hierarchyLevel);

    /**
     * Finds the maximum sequence number for a given source document and parent chunk.
     * Used to determine the next sequence number when adding new chunks.
     * 
     * @param sourceDocument the source document
     * @param parentChunk the parent chunk (can be null for root chunks)
     * @return the maximum sequence number, or 0 if no chunks exist
     */
    @Query("SELECT COALESCE(MAX(dc.sequenceInDocument), 0) FROM DocumentChunk dc WHERE dc.sourceDocument = :sourceDocument AND dc.parentChunk = :parentChunk")
    Integer findMaxSequenceForParent(@Param("sourceDocument") SourceDocument sourceDocument, 
                                    @Param("parentChunk") DocumentChunk parentChunk);

    /**
     * Deletes all chunks belonging to a specific source document.
     * This is used when a source document is being reprocessed.
     * 
     * @param sourceDocument the source document whose chunks should be deleted
     */
    void deleteBySourceDocument(SourceDocument sourceDocument);

    /**
     * Deletes all chunks belonging to a specific source document by ID.
     * This is used when a source document is being deleted.
     * 
     * @param sourceDocumentId the source document ID whose chunks should be deleted
     * @return the number of deleted chunks
     */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("DELETE FROM DocumentChunk dc WHERE dc.sourceDocumentId = :sourceDocumentId")
    int deleteBySourceDocumentId(@Param("sourceDocumentId") UUID sourceDocumentId);
}