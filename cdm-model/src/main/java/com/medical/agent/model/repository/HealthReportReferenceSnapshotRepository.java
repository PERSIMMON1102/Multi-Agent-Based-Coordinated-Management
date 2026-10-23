package com.medical.agent.model.repository;

import com.medical.agent.model.entity.HealthReportReferenceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HealthReportReferenceSnapshotRepository extends JpaRepository<HealthReportReferenceSnapshot, Long> {

    List<HealthReportReferenceSnapshot> findByReportIdOrderBySortOrderAscIdAsc(Long reportId);

    void deleteByReportIdIn(List<Long> reportIds);
}
