package com.medical.agent.model.repository;

import com.medical.agent.model.entity.MedicalResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicalResourceRepository extends JpaRepository<MedicalResource, Long> {
    List<MedicalResource> findBySpecialtyContaining(String specialty);
    List<MedicalResource> findByType(String type);

    @Query(value = "SELECT *, ( 6371 * acos( cos( radians(:lat) ) * cos( radians( latitude ) ) * " +
            "cos( radians( longitude ) - radians(:lng) ) + sin( radians(:lat) ) * " +
            "sin( radians( latitude ) ) ) ) AS distance FROM medical_resource " +
            "HAVING distance < :radius ORDER BY distance LIMIT :limit", nativeQuery = true)
    List<MedicalResource> findNearby(@Param("lat") double lat, @Param("lng") double lng,
                                      @Param("radius") double radiusKm, @Param("limit") int limit);
}
