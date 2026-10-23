package com.medical.agent.model.repository;

import com.medical.agent.model.entity.ClinicalEncounter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClinicalEncounterRepository extends JpaRepository<ClinicalEncounter, Long> {

    List<ClinicalEncounter> findByPatientIdOrderByEncounterDateDescCreatedAtDesc(Long patientId);

    Optional<ClinicalEncounter> findFirstBySourceKindAndSourceRefId(String sourceKind, Long sourceRefId);
}
