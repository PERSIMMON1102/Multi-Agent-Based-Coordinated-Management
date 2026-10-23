package com.medical.agent.model.repository;

import com.medical.agent.model.entity.ClinicalAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClinicalAttachmentRepository extends JpaRepository<ClinicalAttachment, Long> {

    List<ClinicalAttachment> findByEncounterIdOrderByIdAsc(Long encounterId);
}
