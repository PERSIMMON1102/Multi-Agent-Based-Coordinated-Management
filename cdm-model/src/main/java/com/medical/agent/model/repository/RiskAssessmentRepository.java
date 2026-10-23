package com.medical.agent.model.repository;

import com.medical.agent.common.enums.DiseaseType;
import com.medical.agent.model.entity.RiskAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, Long> {
    List<RiskAssessment> findByPatientIdOrderByCreatedAtDesc(Long patientId);
    List<RiskAssessment> findByPatientIdAndDiseaseTypeOrderByCreatedAtDesc(Long patientId, DiseaseType diseaseType);
    Optional<RiskAssessment> findFirstByPatientIdAndDiseaseTypeOrderByCreatedAtDesc(Long patientId, DiseaseType diseaseType);
    List<RiskAssessment> findByPatientIdInOrderByCreatedAtDesc(List<Long> patientIds);
    List<RiskAssessment> findByPatientIdInAndCreatedAtBetweenOrderByCreatedAtAsc(List<Long> patientIds,
                                                                                LocalDateTime start,
                                                                                LocalDateTime end);
}
