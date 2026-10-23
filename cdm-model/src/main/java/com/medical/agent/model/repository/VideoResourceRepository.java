package com.medical.agent.model.repository;

import com.medical.agent.model.entity.VideoResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoResourceRepository extends JpaRepository<VideoResource, Long> {

    long countByObjectKey(String objectKey);

    long countByCoverObjectKey(String coverObjectKey);

    long countByActiveTrue();

    long countByActiveTrueAndHasTranscriptTrue();

    List<VideoResource> findByIdIn(List<Long> ids);
}
