package com.medical.agent.model.repository;

import com.medical.agent.model.entity.HealthReportVideoSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HealthReportVideoSnapshotRepository extends JpaRepository<HealthReportVideoSnapshot, Long> {

    List<HealthReportVideoSnapshot> findByReportIdOrderBySortOrderAscIdAsc(Long reportId);

    List<HealthReportVideoSnapshot> findByReportIdIn(List<Long> reportIds);

    void deleteByReportIdIn(List<Long> reportIds);

    long countByObjectKey(String objectKey);

    long countByCoverObjectKey(String coverObjectKey);
}
