package com.medical.agent.model.repository;

import com.medical.agent.model.entity.ClinicalDataJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface ClinicalDataJobRepository extends JpaRepository<ClinicalDataJob, Long> {

    List<ClinicalDataJob> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    List<ClinicalDataJob> findByPatientIdInOrderByCreatedAtDesc(List<Long> patientIds);

    List<ClinicalDataJob> findBySourceKindAndSourceRefIdOrderByCreatedAtDesc(String sourceKind, Long sourceRefId);

    List<ClinicalDataJob> findBySourceKindAndSourceRefIdInOrderByCreatedAtDesc(String sourceKind, List<Long> sourceRefIds);

    Optional<ClinicalDataJob> findFirstBySourceKindAndSourceRefIdOrderByCreatedAtDesc(String sourceKind, Long sourceRefId);

    List<ClinicalDataJob> findByCorrelationIdOrderByCreatedAtDesc(String correlationId);

    Optional<ClinicalDataJob> findFirstByIdempotencyKeyOrderByCreatedAtDesc(String idempotencyKey);

    List<ClinicalDataJob> findByJobStatusOrderByCreatedAtAsc(String jobStatus);

    List<ClinicalDataJob> findByPatientIdInAndJobStatusOrderByCreatedAtAsc(List<Long> patientIds, String jobStatus);

    List<ClinicalDataJob> findByJobStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(String jobStatus, LocalDateTime nextRetryAt);

    List<ClinicalDataJob> findByPatientIdInAndJobStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(List<Long> patientIds, String jobStatus, LocalDateTime nextRetryAt);

    List<ClinicalDataJob> findByJobStatusAndStartedAtLessThanEqualOrderByStartedAtAsc(String jobStatus, LocalDateTime startedAt);

    List<ClinicalDataJob> findByPatientIdInAndCorrelationIdOrderByCreatedAtDesc(List<Long> patientIds, String correlationId);

    long countByJobStatus(String jobStatus);

    long countByJobType(String jobType);

    long countByJobTypeAndJobStatus(String jobType, String jobStatus);

    @Query("select distinct j.jobType from ClinicalDataJob j where j.jobType is not null and j.jobType <> ''")
    List<String> findDistinctJobTypes();
}
