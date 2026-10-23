package com.medical.agent.model.repository;

import com.medical.agent.model.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {
    Optional<Patient> findByIdCard(String idCard);
    Optional<Patient> findByFhirId(String fhirId);
    Optional<Patient> findByUsername(String username);
    Optional<Patient> findByName(String name);
    List<Patient> findByNameContaining(String name);
    List<Patient> findByAssignedDoctorId(Long assignedDoctorId);
    List<Patient> findByNameContainingAndAssignedDoctorId(String name, Long assignedDoctorId);
}
