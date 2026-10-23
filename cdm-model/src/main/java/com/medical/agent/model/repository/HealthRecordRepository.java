package com.medical.agent.model.repository;

import com.medical.agent.model.entity.HealthRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HealthRecordRepository extends JpaRepository<HealthRecord, Long> {
    List<HealthRecord> findByPatientIdOrderByRecordDateDesc(Long patientId);
    Optional<HealthRecord> findFirstByPatientIdOrderByRecordDateDesc(Long patientId);
}
