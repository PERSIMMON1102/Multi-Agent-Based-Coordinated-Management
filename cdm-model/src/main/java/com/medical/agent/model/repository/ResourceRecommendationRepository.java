package com.medical.agent.model.repository;

import com.medical.agent.model.entity.ResourceRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceRecommendationRepository extends JpaRepository<ResourceRecommendation, Long> {
    List<ResourceRecommendation> findByPatientIdOrderByCreatedAtDesc(Long patientId);
    List<ResourceRecommendation> findByAssessmentId(Long assessmentId);
}
