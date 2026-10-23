package com.medical.agent.model.repository;

import com.medical.agent.model.entity.PatientMedicalHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientMedicalHistoryRepository extends JpaRepository<PatientMedicalHistory, Long> {

    Optional<PatientMedicalHistory> findByPatientId(Long patientId);
}
