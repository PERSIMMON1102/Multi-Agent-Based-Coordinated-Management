package com.medical.agent.model.repository;

import com.medical.agent.model.entity.FollowUpRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FollowUpRecordRepository extends JpaRepository<FollowUpRecord, Long> {
    List<FollowUpRecord> findByPlanIdOrderByFollowDateDesc(Long planId);
    List<FollowUpRecord> findByPatientIdOrderByFollowDateDesc(Long patientId);
}
