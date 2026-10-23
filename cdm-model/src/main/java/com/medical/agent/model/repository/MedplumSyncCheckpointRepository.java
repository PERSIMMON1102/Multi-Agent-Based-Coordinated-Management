package com.medical.agent.model.repository;

import com.medical.agent.model.entity.MedplumSyncCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MedplumSyncCheckpointRepository extends JpaRepository<MedplumSyncCheckpoint, Long> {

    Optional<MedplumSyncCheckpoint> findByPatientIdAndSyncScope(Long patientId, String syncScope);
}
