package com.medical.agent.model.repository;

import com.medical.agent.model.entity.PaperCaseRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaperCaseRecordRepository extends JpaRepository<PaperCaseRecord, Long> {
    List<PaperCaseRecord> findByPatientIdOrderByCreatedAtDesc(Long patientId);
    List<PaperCaseRecord> findByPatientIdInOrderByCreatedAtDesc(List<Long> patientIds);
    Optional<PaperCaseRecord> findFirstByPatientIdOrderByCreatedAtDesc(Long patientId);
    List<PaperCaseRecord> findByIcdCodingStatusOrderByCreatedAtDesc(String icdCodingStatus);
    List<PaperCaseRecord> findByPatientIdInAndIcdCodingStatusOrderByCreatedAtDesc(List<Long> patientIds, String icdCodingStatus);
    List<PaperCaseRecord> findByMedplumSyncStatusOrderByUpdatedAtDesc(String medplumSyncStatus);
    List<PaperCaseRecord> findByPatientIdInAndMedplumSyncStatusOrderByUpdatedAtDesc(List<Long> patientIds, String medplumSyncStatus);
}
