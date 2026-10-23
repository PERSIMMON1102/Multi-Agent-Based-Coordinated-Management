package com.medical.agent.model.repository;

import com.medical.agent.model.entity.MeasurementSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeasurementSourceRepository extends JpaRepository<MeasurementSource, Long> {

    List<MeasurementSource> findByHealthRecordIdOrderByIdAsc(Long healthRecordId);
}
