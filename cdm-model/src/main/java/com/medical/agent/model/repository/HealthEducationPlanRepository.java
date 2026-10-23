package com.medical.agent.model.repository;

import com.medical.agent.model.entity.HealthEducationPlan;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HealthEducationPlanRepository extends JpaRepository<HealthEducationPlan, Long> {

    List<HealthEducationPlan> findByPatientIdOrderByGeneratedAtDesc(Long patientId);

    List<HealthEducationPlan> findByPatientIdOrderByGeneratedAtDesc(Long patientId, Pageable pageable);
}
