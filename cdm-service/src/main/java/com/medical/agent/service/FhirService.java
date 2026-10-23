package com.medical.agent.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.medical.agent.model.entity.ClinicalAttachment;
import com.medical.agent.model.entity.ClinicalEncounter;
import com.medical.agent.model.entity.EncounterDiagnosis;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PatientMedicalHistory;
import com.medical.agent.model.repository.ClinicalAttachmentRepository;
import com.medical.agent.model.repository.ClinicalEncounterRepository;
import com.medical.agent.model.repository.EncounterDiagnosisRepository;
import com.medical.agent.model.repository.HealthRecordRepository;
import com.medical.agent.model.repository.PaperCaseRecordRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.repository.PatientMedicalHistoryRepository;
import com.medical.agent.service.fhir.FhirCodingRegistry;
import com.medical.agent.service.support.PatientMedicalHistoryPromptSupport;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * FHIR数据转换服务 - 将患者数据转换为FHIR R4标准格式
 */
@Service
public class FhirService {

    private static final String EXT_BASE = "https://cdm.local/fhir/StructureDefinition/";
    private static final String EXT_EMERGENCY_CONTACT = EXT_BASE + "emergency-contact";
    private static final String EXT_EMERGENCY_PHONE = EXT_BASE + "emergency-phone";
    private static final String EXT_ASSIGNED_DOCTOR_ID = EXT_BASE + "assigned-doctor-id";
    private static final String EXT_PROFILE_COMPLETED = EXT_BASE + "profile-completed";
    private static final String EXT_MEDICAL_HISTORY_SUMMARY = EXT_BASE + "medical-history-summary";
    private static final String LOCAL_IDENTIFIER_SYSTEM = "https://cdm.local/fhir/identifier/";
    private static final String PATIENT_FHIR_IDENTIFIER_SYSTEM = LOCAL_IDENTIFIER_SYSTEM + "patient-fhir-id";
    private static final String PATIENT_USERNAME_IDENTIFIER_SYSTEM = LOCAL_IDENTIFIER_SYSTEM + "patient-username";
    private static final String PATIENT_IDENTIFIER_SYSTEM = LOCAL_IDENTIFIER_SYSTEM + "patient-id";

    private final FhirContext fhirContext;
    private final PatientRepository patientRepository;
    private final HealthRecordRepository healthRecordRepository;
    private final PatientMedicalHistoryRepository patientMedicalHistoryRepository;
    private final PaperCaseRecordRepository paperCaseRecordRepository;
    private final ClinicalEncounterRepository clinicalEncounterRepository;
    private final EncounterDiagnosisRepository encounterDiagnosisRepository;
    private final ClinicalAttachmentRepository clinicalAttachmentRepository;
    private final FhirCodingRegistry codingRegistry;

    public FhirService(PatientRepository patientRepository,
                       HealthRecordRepository healthRecordRepository,
                       PatientMedicalHistoryRepository patientMedicalHistoryRepository,
                       PaperCaseRecordRepository paperCaseRecordRepository,
                       ClinicalEncounterRepository clinicalEncounterRepository,
                       EncounterDiagnosisRepository encounterDiagnosisRepository,
                       ClinicalAttachmentRepository clinicalAttachmentRepository) {
        this(patientRepository,
                healthRecordRepository,
                patientMedicalHistoryRepository,
                paperCaseRecordRepository,
                clinicalEncounterRepository,
                encounterDiagnosisRepository,
                clinicalAttachmentRepository,
                new FhirCodingRegistry());
    }

    @Autowired
    public FhirService(PatientRepository patientRepository,
                       HealthRecordRepository healthRecordRepository,
                       PatientMedicalHistoryRepository patientMedicalHistoryRepository,
                       PaperCaseRecordRepository paperCaseRecordRepository,
                       ClinicalEncounterRepository clinicalEncounterRepository,
                       EncounterDiagnosisRepository encounterDiagnosisRepository,
                       ClinicalAttachmentRepository clinicalAttachmentRepository,
                       FhirCodingRegistry codingRegistry) {
        this.fhirContext = FhirContext.forR4();
        this.patientRepository = patientRepository;
        this.healthRecordRepository = healthRecordRepository;
        this.patientMedicalHistoryRepository = patientMedicalHistoryRepository;
        this.paperCaseRecordRepository = paperCaseRecordRepository;
        this.clinicalEncounterRepository = clinicalEncounterRepository;
        this.encounterDiagnosisRepository = encounterDiagnosisRepository;
        this.clinicalAttachmentRepository = clinicalAttachmentRepository;
        this.codingRegistry = codingRegistry;
    }

    /**
     * 将患者信息转换为FHIR Patient资源JSON
     */
    public String toFhirPatientJson(Long patientId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("患者不存在: " + patientId));

        org.hl7.fhir.r4.model.Patient fhirPatient = new org.hl7.fhir.r4.model.Patient();
        String stablePatientIdentifier = resolvePatientFhirId(patient);
        fhirPatient.setId(stablePatientIdentifier);
        if (stablePatientIdentifier != null && !stablePatientIdentifier.isBlank()) {
            fhirPatient.addIdentifier()
                    .setSystem(PATIENT_FHIR_IDENTIFIER_SYSTEM)
                    .setValue(stablePatientIdentifier);
        }
        if (patient.getUsername() != null && !patient.getUsername().isBlank()) {
            fhirPatient.addIdentifier()
                    .setSystem(PATIENT_USERNAME_IDENTIFIER_SYSTEM)
                    .setValue(patient.getUsername().trim());
        }
        if (patient.getId() != null) {
            fhirPatient.addIdentifier()
                    .setSystem(PATIENT_IDENTIFIER_SYSTEM)
                    .setValue(String.valueOf(patient.getId()));
        }
        if (patient.getIdCard() != null && !patient.getIdCard().isBlank()) {
            fhirPatient.addIdentifier()
                    .setSystem("urn:oid:2.16.156.10011.1.2")
                    .setValue(patient.getIdCard());
        }
        if (patient.getName() != null && !patient.getName().isBlank()) {
            String normalizedName = patient.getName().trim();
            String family = normalizedName.substring(0, 1);
            String given = normalizedName.length() > 1 ? normalizedName.substring(1) : normalizedName;
            fhirPatient.addName().setFamily(family).addGiven(given);
        }

        if ("男".equals(patient.getGender())) {
            fhirPatient.setGender(Enumerations.AdministrativeGender.MALE);
        } else if ("女".equals(patient.getGender())) {
            fhirPatient.setGender(Enumerations.AdministrativeGender.FEMALE);
        }

        if (patient.getBirthDate() != null) {
            fhirPatient.setBirthDate(java.sql.Date.valueOf(patient.getBirthDate()));
        }

        if (patient.getPhone() != null) {
            fhirPatient.addTelecom()
                    .setSystem(ContactPoint.ContactPointSystem.PHONE)
                    .setValue(patient.getPhone());
        }

        if (patient.getAddress() != null) {
            fhirPatient.addAddress().setText(patient.getAddress());
        }

        addPatientExtensions(fhirPatient, patientId, patient);

        IParser parser = fhirContext.newJsonParser().setPrettyPrint(true);
        return parser.encodeResourceToString(fhirPatient);
    }

    /**
     * 将健康记录转换为FHIR Observation资源Bundle JSON
     */
    public String toFhirObservationsJson(Long patientId) {
        List<HealthRecord> records = healthRecordRepository.findByPatientIdOrderByRecordDateDesc(patientId);
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("患者不存在: " + patientId));
        String patientFhirId = resolvePatientFhirId(patient);

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.COLLECTION);

        for (HealthRecord record : records) {
            List<Observation> observations = toObservations(record, patientFhirId);
            for (Observation obs : observations) {
                bundle.addEntry().setResource(obs);
            }
        }

        IParser parser = fhirContext.newJsonParser().setPrettyPrint(true);
        return parser.encodeResourceToString(bundle);
    }

    public String toFhirPaperCasesJson(Long patientId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("患者不存在: " + patientId));
        String patientFhirId = resolvePatientFhirId(patient);
        List<ClinicalEncounter> encounters = clinicalEncounterRepository.findByPatientIdOrderByEncounterDateDescCreatedAtDesc(patientId);

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.COLLECTION);

        for (ClinicalEncounter encounterRecord : encounters) {
            Encounter encounter = toEncounter(encounterRecord, patientFhirId);
            bundle.addEntry().setResource(encounter);

            for (ClinicalAttachment attachment : clinicalAttachmentRepository.findByEncounterIdOrderByIdAsc(encounterRecord.getId())) {
                DocumentReference documentReference = toDocumentReference(attachment, patientFhirId, encounter.getIdElement().getIdPart());
                bundle.addEntry().setResource(documentReference);
            }

            for (EncounterDiagnosis diagnosis : encounterDiagnosisRepository.findByEncounterIdOrderByIdAsc(encounterRecord.getId())) {
                Condition condition = toCondition(diagnosis, patientFhirId, encounter.getIdElement().getIdPart());
                if (condition != null) {
                    bundle.addEntry().setResource(condition);
                }
            }
        }

        IParser parser = fhirContext.newJsonParser().setPrettyPrint(true);
        return parser.encodeResourceToString(bundle);
    }

    private List<Observation> toObservations(HealthRecord record, String patientFhirId) {
        List<Observation> observations = new ArrayList<>();
        Reference patientRef = new Reference("Patient/" + patientFhirId);
        Date effectiveDate = resolveObservationDate(record);
        Reference encounterRef = resolveEncounterReference(record);

        if (record.getSystolicBp() != null && record.getDiastolicBp() != null) {
            Observation bp = createObservation(patientRef, "blood-pressure",
                    record.getSystolicBp().doubleValue(), buildObservationId("bp", record.getId()), effectiveDate);
            bp.addComponent()
                    .setCode(new CodeableConcept().addCoding(
                            codingRegistry.requireObservation("blood-pressure-systolic").toCoding()))
                    .setValue(buildQuantity(record.getSystolicBp(), codingRegistry.requireObservation("blood-pressure-systolic").unit()));
            bp.addComponent()
                    .setCode(new CodeableConcept().addCoding(
                            codingRegistry.requireObservation("blood-pressure-diastolic").toCoding()))
                    .setValue(buildQuantity(record.getDiastolicBp(), codingRegistry.requireObservation("blood-pressure-diastolic").unit()));
            observations.add(bp);
        }

        BeanWrapper wrapper = new BeanWrapperImpl(record);
        addConfiguredObservations(observations, wrapper, patientRef, effectiveDate, record.getId());

        if (encounterRef != null) {
            observations.forEach(observation -> applyEncounterReference(observation, encounterRef));
        }

        return observations;
    }

    private void addConfiguredObservations(List<Observation> observations,
                                           BeanWrapper wrapper,
                                           Reference patientRef,
                                           Date effectiveDate,
                                           Long recordId) {
        for (FhirCodingRegistry.ObservationFieldMapping mapping : codingRegistry.healthRecordObservationMappings()) {
            if (!wrapper.isReadableProperty(mapping.field())) {
                throw new IllegalStateException("health record observation mapping references unreadable property: " + mapping.field());
            }
            Object value = wrapper.getPropertyValue(mapping.field());
            if (value == null) {
                continue;
            }
            String observationId = buildObservationId(mapping.observationKey(), recordId);
            switch (mapping.valueType()) {
                case QUANTITY -> {
                    if (value instanceof Number number) {
                        observations.add(createObservation(patientRef, mapping.observationKey(), number, observationId, effectiveDate));
                    } else {
                        throw new IllegalStateException("expected numeric value for mapped property: " + mapping.field());
                    }
                }
                case BOOLEAN -> {
                    if (value instanceof Boolean bool) {
                        observations.add(createBooleanObservation(patientRef, mapping.observationKey(), bool, observationId, effectiveDate));
                    } else {
                        throw new IllegalStateException("expected boolean value for mapped property: " + mapping.field());
                    }
                }
                case STRING -> {
                    String text = value.toString().trim();
                    if (!text.isBlank()) {
                        observations.add(createStringObservation(patientRef, mapping.observationKey(), text, observationId, effectiveDate));
                    }
                }
            }
        }
    }

    private void addPatientExtensions(org.hl7.fhir.r4.model.Patient fhirPatient, Long patientId, Patient patient) {
        addStringExtension(fhirPatient, EXT_EMERGENCY_CONTACT, patient.getEmergencyContact());
        addStringExtension(fhirPatient, EXT_EMERGENCY_PHONE, patient.getEmergencyPhone());
        if (patient.getAssignedDoctorId() != null) {
            fhirPatient.addExtension(new Extension(EXT_ASSIGNED_DOCTOR_ID, new StringType(String.valueOf(patient.getAssignedDoctorId()))));
        }
        if (patient.getProfileCompleted() != null) {
            fhirPatient.addExtension(new Extension(EXT_PROFILE_COMPLETED, new BooleanType(patient.getProfileCompleted())));
        }

        PatientMedicalHistory history = patientMedicalHistoryRepository.findByPatientId(patientId).orElse(null);
        String medicalHistorySummary = PatientMedicalHistoryPromptSupport.buildSummary(history);
        addStringExtension(fhirPatient, EXT_MEDICAL_HISTORY_SUMMARY, medicalHistorySummary);
    }

    private Encounter toEncounter(ClinicalEncounter encounterRecord, String patientFhirId) {
        Encounter encounter = new Encounter();
        encounter.setId(buildPaperCaseEncounterId(encounterRecord.getId()));
        encounter.setStatus(Encounter.EncounterStatus.FINISHED);
        encounter.setSubject(new Reference("Patient/" + patientFhirId));
        encounter.addIdentifier()
                .setSystem(LOCAL_IDENTIFIER_SYSTEM + "paper-case-id")
                .setValue(String.valueOf(encounterRecord.getId()));
        encounter.addType(new CodeableConcept().addCoding(codingRegistry.encounterTypeAmbulatory()));
        Period period = new Period();
        Date encounterDate = encounterRecord.getEncounterDate() != null
                ? java.sql.Date.valueOf(encounterRecord.getEncounterDate())
                : encounterRecord.getCreatedAt() != null ? java.sql.Timestamp.valueOf(encounterRecord.getCreatedAt()) : new Date();
        period.setStart(encounterDate);
        period.setEnd(encounterDate);
        encounter.setPeriod(period);
        if (encounterRecord.getChiefComplaint() != null && !encounterRecord.getChiefComplaint().isBlank()) {
            encounter.setReasonCode(List.of(new CodeableConcept().setText(encounterRecord.getChiefComplaint().trim())));
        }
        return encounter;
    }

    private Condition toCondition(EncounterDiagnosis diagnosisRecord, String patientFhirId, String encounterId) {
        String diagnosis = firstNonBlank(diagnosisRecord.getDiagnosisText(), diagnosisRecord.getIcd11Title());
        if (diagnosis == null) {
            return null;
        }
        Condition condition = new Condition();
        condition.setId(buildPaperCaseConditionId(diagnosisRecord.getId()));
        condition.setSubject(new Reference("Patient/" + patientFhirId));
        condition.setEncounter(new Reference("Encounter/" + encounterId));
        condition.setClinicalStatus(new CodeableConcept().addCoding(codingRegistry.activeConditionClinicalStatus()));
        condition.setVerificationStatus(new CodeableConcept().addCoding(
                codingRegistry.conditionVerificationStatus(isConfirmedIcd(diagnosisRecord.getCodingStatus()))));
        condition.addCategory(new CodeableConcept().addCoding(codingRegistry.encounterDiagnosisCategory()));
        CodeableConcept code = new CodeableConcept();
        if (diagnosisRecord.getIcd11Code() != null && !diagnosisRecord.getIcd11Code().isBlank()) {
            code.addCoding(codingRegistry.icd11Coding(
                    diagnosisRecord.getIcd11Code().trim(),
                    firstNonBlank(diagnosisRecord.getIcd11Title(), diagnosis)));
        }
        code.setText(diagnosis);
        condition.setCode(code);
        condition.addIdentifier()
                .setSystem(LOCAL_IDENTIFIER_SYSTEM + "paper-case-condition")
                .setValue(String.valueOf(diagnosisRecord.getId()));
        return condition;
    }

    private DocumentReference toDocumentReference(ClinicalAttachment attachmentRecord, String patientFhirId, String encounterId) {
        DocumentReference documentReference = new DocumentReference();
        documentReference.setId(buildPaperCaseDocumentId(attachmentRecord.getId()));
        documentReference.setStatus(Enumerations.DocumentReferenceStatus.CURRENT);
        documentReference.setSubject(new Reference("Patient/" + patientFhirId));
        documentReference.setContext(new DocumentReference.DocumentReferenceContextComponent()
                .addEncounter(new Reference("Encounter/" + encounterId)));
        documentReference.setDate(attachmentRecord.getCreatedAt() != null
                ? java.sql.Timestamp.valueOf(attachmentRecord.getCreatedAt())
                : new Date());
        documentReference.addIdentifier()
                .setSystem(LOCAL_IDENTIFIER_SYSTEM + "paper-case-document")
                .setValue(String.valueOf(attachmentRecord.getId()));
        documentReference.setDescription(firstNonBlank(attachmentRecord.getFileName(), "纸质病历 OCR 文书"));
        documentReference.addCategory(new CodeableConcept().addCoding(codingRegistry.documentCategoryClinicalNote()));
        documentReference.setType(new CodeableConcept().addCoding(codingRegistry.documentTypeMedicalRecord()));
        documentReference.addContent(buildAttachment(attachmentRecord));
        return documentReference;
    }

    private DocumentReference.DocumentReferenceContentComponent buildAttachment(ClinicalAttachment attachmentRecord) {
        Attachment attachment = new Attachment();
        attachment.setContentType(firstNonBlank(attachmentRecord.getContentType(), "application/octet-stream"));
        attachment.setUrl(attachmentRecord.getFileUrl());
        attachment.setTitle(firstNonBlank(attachmentRecord.getFileName(), "paper-case"));
        String text = attachmentRecord.getDocumentNarrative();
        if (text != null) {
            attachment.setData(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return new DocumentReference.DocumentReferenceContentComponent(attachment);
    }

    private void appendNarrative(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append("\n");
        }
        sb.append(label).append("：").append(value.trim());
    }

    private boolean isConfirmedIcd(String codingStatus) {
        return codingStatus != null
                && ("manually_confirmed".equalsIgnoreCase(codingStatus)
                || "auto_coded".equalsIgnoreCase(codingStatus));
    }

    private String buildPaperCaseEncounterId(Long paperCaseId) {
        return sanitizeFhirId("paper-case-encounter-" + paperCaseId);
    }

    private Reference resolveEncounterReference(HealthRecord record) {
        if (record == null || record.getEncounterId() == null) {
            return null;
        }
        return new Reference("Encounter/" + buildPaperCaseEncounterId(record.getEncounterId()));
    }

    private void applyEncounterReference(Observation observation, Reference encounterRef) {
        if (observation == null || encounterRef == null) {
            return;
        }
        observation.setEncounter(new Reference(encounterRef.getReference()));
    }

    private String buildPaperCaseConditionId(Long paperCaseId) {
        return sanitizeFhirId("paper-case-condition-" + paperCaseId);
    }

    private String buildPaperCaseDocumentId(Long paperCaseId) {
        return sanitizeFhirId("paper-case-document-" + paperCaseId);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private void addStringExtension(org.hl7.fhir.r4.model.Patient fhirPatient, String url, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        fhirPatient.addExtension(new Extension(url, new StringType(value.trim())));
    }

    private Observation createObservation(Reference patientRef, String observationKey, Number value,
                                          String observationId, Date effectiveDate) {
        return createObservation(patientRef, codingRegistry.requireObservation(observationKey), value, observationId, effectiveDate);
    }

    private Observation createObservation(Reference patientRef, FhirCodingRegistry.ObservationDefinition definition,
                                          Number value, String observationId, Date effectiveDate) {
        Observation obs = new Observation();
        if (observationId != null && !observationId.isBlank()) {
            obs.setId(observationId);
        }
        obs.setStatus(Observation.ObservationStatus.FINAL);
        obs.setSubject(patientRef);
        obs.getCode().addCoding(definition.toCoding());
        obs.setValue(buildQuantity(value, definition.unit()));
        obs.setEffective(new DateTimeType(effectiveDate));
        return obs;
    }

    private Observation createBooleanObservation(Reference patientRef, String observationKey,
                                                 Boolean value, String observationId, Date effectiveDate) {
        FhirCodingRegistry.ObservationDefinition definition = codingRegistry.requireObservation(observationKey);
        Observation obs = new Observation();
        if (observationId != null && !observationId.isBlank()) {
            obs.setId(observationId);
        }
        obs.setStatus(Observation.ObservationStatus.FINAL);
        obs.setSubject(patientRef);
        obs.getCode().addCoding(definition.toCoding());
        obs.setValue(new BooleanType(Boolean.TRUE.equals(value)));
        obs.setEffective(new DateTimeType(effectiveDate));
        return obs;
    }

    private Observation createStringObservation(Reference patientRef, String observationKey,
                                                String value, String observationId, Date effectiveDate) {
        FhirCodingRegistry.ObservationDefinition definition = codingRegistry.requireObservation(observationKey);
        Observation obs = new Observation();
        if (observationId != null && !observationId.isBlank()) {
            obs.setId(observationId);
        }
        obs.setStatus(Observation.ObservationStatus.FINAL);
        obs.setSubject(patientRef);
        obs.getCode().addCoding(definition.toCoding());
        obs.setValue(new StringType(value));
        obs.setEffective(new DateTimeType(effectiveDate));
        return obs;
    }

    private Quantity buildQuantity(Number value, String unit) {
        Quantity quantity = new Quantity().setValue(value.doubleValue());
        if (unit != null && !unit.isBlank()) {
            quantity.setUnit(unit).setSystem("http://unitsofmeasure.org");
        }
        return quantity;
    }

    private String buildObservationId(String metricKey, Long recordId) {
        if (recordId == null || metricKey == null || metricKey.isBlank()) {
            return null;
        }
        return sanitizeFhirId("obs-" + recordId + "-" + metricKey);
    }

    private Date resolveObservationDate(HealthRecord record) {
        if (record == null) {
            return new Date();
        }
        if (record.getRecordedAt() != null) {
            return java.sql.Timestamp.valueOf(record.getRecordedAt());
        }
        if (record.getRecordDate() != null) {
            return java.sql.Date.valueOf(record.getRecordDate());
        }
        if (record.getCreatedAt() != null) {
            return java.sql.Timestamp.valueOf(record.getCreatedAt());
        }
        return new Date();
    }

    private String resolvePatientFhirId(Patient patient) {
        if (patient == null) {
            return null;
        }
        if (patient.getFhirId() != null && !patient.getFhirId().isBlank()) {
            return sanitizeFhirId(patient.getFhirId().trim());
        }
        if (patient.getId() != null) {
            return sanitizeFhirId("patient-" + patient.getId());
        }
        return null;
    }

    private String sanitizeFhirId(String rawId) {
        if (rawId == null) {
            return null;
        }
        String sanitized = rawId.trim().toLowerCase()
                .replaceAll("[^a-z0-9\\-.]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^[\\-.]+|[\\-.]+$", "");
        if (sanitized.isBlank()) {
            return null;
        }
        if (sanitized.length() > 64) {
            sanitized = sanitized.substring(0, 64);
        }
        return sanitized;
    }
}
