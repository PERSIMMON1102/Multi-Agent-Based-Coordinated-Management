package com.medical.agent.model.repository;

import com.medical.agent.model.entity.KnowledgeChunk;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    @Query("""
            SELECT k FROM KnowledgeChunk k
            WHERE (COALESCE(:disease, '') = '' OR COALESCE(k.disease, '') LIKE CONCAT('%', :disease, '%'))
              AND (
                  COALESCE(:keyword, '') = '' OR
                  COALESCE(k.title, '') LIKE CONCAT('%', :keyword, '%') OR
                  COALESCE(k.section, '') LIKE CONCAT('%', :keyword, '%') OR
                  COALESCE(k.content, '') LIKE CONCAT('%', :keyword, '%') OR
                  COALESCE(k.source, '') LIKE CONCAT('%', :keyword, '%') OR
                  COALESCE(k.contentTags, '') LIKE CONCAT('%', :keyword, '%')
              )
            """)
    Page<KnowledgeChunk> search(@Param("disease") String disease,
                                @Param("keyword") String keyword,
                                Pageable pageable);

    List<KnowledgeChunk> findByEmbeddingStatusOrderByCreatedAtAsc(String embeddingStatus);

    List<KnowledgeChunk> findByDiseaseAndTitleAndSourceAndVersionTagAndPublishedAtOrderByIdAsc(String disease,
                                                                                                String title,
                                                                                                String source,
                                                                                                String versionTag,
                                                                                                LocalDate publishedAt);
}
