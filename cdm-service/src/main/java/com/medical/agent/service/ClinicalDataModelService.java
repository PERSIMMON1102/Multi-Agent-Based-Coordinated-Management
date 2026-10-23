package com.medical.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.medical.agent.common.dto.HealthRecordDTO;
import com.medical.agent.model.entity.ClinicalAttachment;
import com.medical.agent.model.entity.ClinicalEncounter;
import com.medical.agent.model.entity.EncounterDiagnosis;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.model.entity.MeasurementSource;
import com.medical.agent.model.entity.PaperCaseRecord;
import com.medical.agent.model.repository.ClinicalAttachmentRepository;
import com.medical.agent.model.repository.ClinicalEncounterRepository;
import com.medical.agent.model.repository.EncounterDiagnosisRepository;
import com.medical.agent.model.repository.HealthRecordRepository;
import com.medical.agent.model.repository.MeasurementSourceRepository;
import com.medical.agent.service.papercase.PaperCaseExtractionResult;
import com.medical.agent.service.papercase.PaperCaseIcdMatchResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

@Service
public class ClinicalDataModelService {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final ClinicalEncounterRepository clinicalEncounterRepository;
    private final EncounterDiagnosisRepository encounterDiagnosisRepository;
    private final ClinicalAttachmentRepository clinicalAttachmentRepository;
    private final HealthRecordRepository healthRecordRepository;
    private final MeasurementSourceRepository measurementSourceRepository;

    public ClinicalDataModelService(ClinicalEncounterRepository clinicalEncounterRepository,
                                    EncounterDiagnosisRepository encounterDiagnosisRepository,
                                    ClinicalAttachmentRepository clinicalAttachmentRepository,
                                    HealthRecordRepository healthRecordRepository,
                                    MeasurementSourceRepository measurementSourceRepository) {
        this.clinicalEncounterRepository = clinicalEncounterRepository;
        this.encounterDiagnosisRepository = encounterDiagnosisRepository;
        this.clinicalAttachmentRepository = clinicalAttachmentRepository;
        this.healthRecordRepository = healthRecordRepository;
        this.measurementSourceRepository = measurementSourceRepository;
    }

    @Transactional
    public ClinicalEncounter upsertPaperCaseEncounter(PaperCaseRecord record,
                                                      PaperCaseExtractionResult extraction,
                                                      PaperCaseIcdMatchResult icdMatch) {
        ClinicalEncounter encounter = clinicalEncounterRepository
                .findFirstBySourceKindAndSourceRefId("paper_case_record", record.getId())
                .orElseGet(ClinicalEncounter::new);
        encounter.setPatientId(record.getPatientId());
        encounter.setEncounterType("paper_case");
        encounter.setEncounterDate(record.getRecordDate() != null ? record.getRecordDate() : LocalDate.now());
        encounter.setChiefComplaint(extraction.getChiefComplaint());
        encounter.setPresentIllness(extraction.getPresentIllness());
        encounter.setPhysicalExam(extraction.getPhysicalExam());
        encounter.setSourceKind("paper_case_record");
        encounter.setSourceRefId(record.getId());
        encounter.setSourceFileUrl(record.getSourceFileUrl());
        encounter = clinicalEncounterRepository.save(encounter);

        EncounterDiagnosis diagnosis = encounterDiagnosisRepository.findByEncounterIdOrderByIdAsc(encounter.getId())
                .stream()
                .filter(item -> Boolean.TRUE.equals(item.getPrimaryDiagnosis()))
                .findFirst()
                .orElseGet(EncounterDiagnosis::new);
        diagnosis.setEncounterId(encounter.getId());
        diagnosis.setDiagnosisText(firstNonBlank(extraction.getPreliminaryDiagnosis(), icdMatch == null ? null : icdMatch.getTitle()));
        diagnosis.setPrimaryDiagnosis(true);
        diagnosis.setIcd11Code(icdMatch == null ? null : icdMatch.getCode());
        diagnosis.setIcd11Title(icdMatch == null ? null : icdMatch.getTitle());
        diagnosis.setCodingStatus(icdMatch == null ? null : icdMatch.getCodingStatus());
        diagnosis.setCodingSource(icdMatch == null ? null : icdMatch.getCodingSource());
        diagnosis.setCodingConfidence(icdMatch == null ? null : icdMatch.getConfidence());
        encounterDiagnosisRepository.save(diagnosis);

        ClinicalAttachment attachment = clinicalAttachmentRepository.findByEncounterIdOrderByIdAsc(encounter.getId())
                .stream()
                .filter(item -> "paper_case_document".equalsIgnoreCase(item.getAttachmentType()))
                .findFirst()
                .orElseGet(ClinicalAttachment::new);
        attachment.setEncounterId(encounter.getId());
        attachment.setAttachmentType("paper_case_document");
        attachment.setContentType(record.getSourceFileType());
        attachment.setFileName(record.getOriginalFileName());
        attachment.setFileUrl(record.getSourceFileUrl());
        attachment.setOcrText(record.getOcrText());
        attachment.setDocumentNarrative(buildNarrative(record));
        attachment.setSourceKind("paper_case_record");
        attachment.setSourceRefId(record.getId());
        clinicalAttachmentRepository.save(attachment);
        return encounter;
    }

    @Transactional
    public ClinicalEncounter upsertPaperCaseEncounter(PaperCaseRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("paper case record must not be null");
        }
        PaperCaseExtractionResult extraction = PaperCaseExtractionResult.builder()
                .patientName(record.getPatientName())
                .gender(record.getGender())
                .age(record.getAge())
                .chiefComplaint(record.getChiefComplaint())
                .presentIllness(record.getPresentIllness())
                .physicalExam(record.getPhysicalExam())
                .preliminaryDiagnosis(record.getPreliminaryDiagnosis())
                .rawText(record.getOcrText())
                .build();
        PaperCaseIcdMatchResult icdMatch = PaperCaseIcdMatchResult.builder()
                .code(record.getIcdCode())
                .title(record.getIcdTitle())
                .confidence(record.getIcdConfidence())
                .codingStatus(record.getIcdCodingStatus())
                .codingSource(record.getIcdCodingSource())
                .build();
        return upsertPaperCaseEncounter(record, extraction, icdMatch);
    }

    @Transactional
    public void recordMeasurementSource(HealthRecordDTO createdRecord,
                                        Long encounterId,
                                        String sourceKind,
                                        Long sourceRefId,
                                        String vendor,
                                        String deviceType,
                                        String deviceId,
                                        Map<String, Object> rawPayload,
                                        Map<String, Object> normalizedPayload) {
        if (createdRecord == null || createdRecord.getId() == null) {
            return;
        }
        measurementSourceRepository.save(MeasurementSource.builder()
                .healthRecordId(createdRecord.getId())
                .encounterId(encounterId)
                .sourceKind(sourceKind)
                .sourceRefId(sourceRefId)
                .vendor(vendor)
                .deviceType(deviceType)
                .deviceId(deviceId)
                .rawPayloadJson(writeJson(rawPayload))
                .normalizedPayloadJson(writeJson(normalizedPayload))
                .build());
    }

    public HealthRecord getHealthRecord(Long healthRecordId) {
        return healthRecordId == null ? null : healthRecordRepository.findById(healthRecordId).orElse(null);
    }

    private String buildNarrative(PaperCaseRecord record) {
        StringBuilder sb = new StringBuilder();
        append(sb, "主诉", record.getChiefComplaint());
        append(sb, "现病史", record.getPresentIllness());
        append(sb, "体格检查", record.getPhysicalExam());
        append(sb, "初步诊断", record.getPreliminaryDiagnosis());
        append(sb, "OCR 原文", record.getOcrText());
        return sb.isEmpty() ? null : sb.toString();
    }

    private void append(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append("\n");
        }
        sb.append(label).append("：").append(value.trim());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String writeJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize clinical data payload", e);
        }
    }
}
