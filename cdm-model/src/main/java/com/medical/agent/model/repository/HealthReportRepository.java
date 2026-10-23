package com.medical.agent.model.repository;

import com.medical.agent.model.entity.HealthReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HealthReportRepository extends JpaRepository<HealthReport, Long> {

    List<HealthReport> findByPatientIdOrderByGeneratedAtDesc(Long patientId);

    List<HealthReport> findByPatientIdAndTypeOrderByGeneratedAtDesc(Long patientId, String type);

    List<HealthReport> findByPatientIdInOrderByGeneratedAtDesc(List<Long> patientIds);

    List<HealthReport> findByPatientIdInAndGeneratedAtBetweenOrderByGeneratedAtAsc(List<Long> patientIds,
                                                                                    LocalDateTime start,
                                                                                    LocalDateTime end);

    void deleteByPatientIdAndType(Long patientId, String type);
}
