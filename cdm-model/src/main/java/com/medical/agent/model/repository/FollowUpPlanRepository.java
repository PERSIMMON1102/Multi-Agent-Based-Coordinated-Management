package com.medical.agent.model.repository;

import com.medical.agent.common.enums.FollowUpStatus;
import com.medical.agent.model.entity.FollowUpPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.medical.agent.common.enums.DiseaseType;

@Repository
public interface FollowUpPlanRepository extends JpaRepository<FollowUpPlan, Long> {
    List<FollowUpPlan> findByPatientIdOrderByCreatedAtDesc(Long patientId);
    List<FollowUpPlan> findByStatusAndNextFollowDateBefore(FollowUpStatus status, LocalDate date);
    List<FollowUpPlan> findByStatus(FollowUpStatus status);
    List<FollowUpPlan> findByStatusIn(List<FollowUpStatus> statuses);
    List<FollowUpPlan> findByPatientIdIn(List<Long> patientIds);
    List<FollowUpPlan> findByPatientIdInAndUpdatedAtBetween(List<Long> patientIds,
                                                            LocalDateTime start,
                                                            LocalDateTime end);
    List<FollowUpPlan> findByPatientIdAndDiseaseTypeAndStatusInOrderByCreatedAtDesc(
            Long patientId,
            DiseaseType diseaseType,
            List<FollowUpStatus> statuses);
}
