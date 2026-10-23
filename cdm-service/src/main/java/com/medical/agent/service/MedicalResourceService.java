package com.medical.agent.service;

import com.medical.agent.common.dto.MedicalResourceDTO;
import com.medical.agent.model.entity.MedicalResource;
import com.medical.agent.model.entity.ResourceRecommendation;
import com.medical.agent.model.repository.MedicalResourceRepository;
import com.medical.agent.model.repository.ResourceRecommendationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MedicalResourceService {

    private final MedicalResourceRepository resourceRepository;
    private final ResourceRecommendationRepository recommendationRepository;

    public MedicalResourceService(MedicalResourceRepository resourceRepository,
                                   ResourceRecommendationRepository recommendationRepository) {
        this.resourceRepository = resourceRepository;
        this.recommendationRepository = recommendationRepository;
    }

    public List<MedicalResourceDTO> findNearby(double lat, double lng, double radiusKm) {
        return resourceRepository.findNearby(lat, lng, radiusKm, 20)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<MedicalResourceDTO> findBySpecialty(String specialty) {
        return resourceRepository.findBySpecialtyContaining(specialty)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<MedicalResourceDTO> getAll() {
        return resourceRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<ResourceRecommendation> getRecommendationsByPatientId(Long patientId) {
        return recommendationRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
    }

    private MedicalResourceDTO toDTO(MedicalResource r) {
        return MedicalResourceDTO.builder()
                .id(r.getId())
                .name(r.getName())
                .type(r.getType())
                .address(r.getAddress())
                .latitude(r.getLatitude())
                .longitude(r.getLongitude())
                .specialty(r.getSpecialty())
                .phone(r.getPhone())
                .build();
    }
}
