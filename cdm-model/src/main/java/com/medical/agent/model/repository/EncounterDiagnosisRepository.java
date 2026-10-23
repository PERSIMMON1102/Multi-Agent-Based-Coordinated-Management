package com.medical.agent.model.repository;

import com.medical.agent.model.entity.EncounterDiagnosis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EncounterDiagnosisRepository extends JpaRepository<EncounterDiagnosis, Long> {

    List<EncounterDiagnosis> findByEncounterIdOrderByIdAsc(Long encounterId);
}
