package com.medical.agent.model.repository;

import com.medical.agent.model.entity.SmartFollowUpMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SmartFollowUpMessageRepository extends JpaRepository<SmartFollowUpMessage, Long> {

    List<SmartFollowUpMessage> findAllByOrderByCreatedAtDesc();

    List<SmartFollowUpMessage> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<SmartFollowUpMessage> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    List<SmartFollowUpMessage> findByPatientIdOrderByCreatedAtDesc(Long patientId, Pageable pageable);
}
