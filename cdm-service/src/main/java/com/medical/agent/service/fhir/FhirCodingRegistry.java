package com.medical.agent.service.fhir;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.r4.model.Coding;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FhirCodingRegistry {

    public static final String LOINC_SYSTEM = "http://loinc.org";
    public static final String LOCAL_HEALTH_RECORD_SYSTEM = "https://cdm.local/fhir/CodeSystem/health-record";
    public static final String CONDITION_CATEGORY_SYSTEM = "http://terminology.hl7.org/CodeSystem/condition-category";
    public static final String CONDITION_CLINICAL_SYSTEM = "http://terminology.hl7.org/CodeSystem/condition-clinical";
    public static final String CONDITION_VERIFICATION_SYSTEM = "http://terminology.hl7.org/CodeSystem/condition-ver-status";
    public static final String ENCOUNTER_TYPE_SYSTEM = "http://terminology.hl7.org/CodeSystem/encounter-type";
    public static final String DOC_CATEGORY_SYSTEM = "http://terminology.hl7.org/CodeSystem/media-category";
    public static final String DOC_TYPE_SYSTEM = "http://loinc.org";
    public static final String ICD11_SYSTEM = "http://id.who.int/icd/release/11/mms";
    private static final String OBSERVATION_REGISTRY_RESOURCE = "/fhir/observation-codings.json";
    private static final String RESOURCE_REGISTRY_RESOURCE = "/fhir/resource-codings.json";
    private static final String HEALTH_RECORD_MAPPING_RESOURCE = "/fhir/health-record-observation-mappings.json";

    private final Map<String, ObservationDefinition> observationDefinitions = new LinkedHashMap<>();
    private final Map<String, ResourceDefinition> resourceDefinitions = new LinkedHashMap<>();
    private final List<ObservationFieldMapping> healthRecordObservationMappings = new java.util.ArrayList<>();

    public FhirCodingRegistry() {
        loadObservationDefinitions();
        loadResourceDefinitions();
        loadHealthRecordObservationMappings();
    }

    public ObservationDefinition requireObservation(String key) {
        ObservationDefinition definition = observationDefinitions.get(key);
        if (definition == null) {
            throw new IllegalArgumentException("unknown FHIR observation mapping: " + key);
        }
        return definition;
    }

    public List<ObservationFieldMapping> healthRecordObservationMappings() {
        return List.copyOf(healthRecordObservationMappings);
    }

    public Coding encounterTypeAmbulatory() {
        return requireResource("encounter-type-ambulatory").toCoding();
    }

    public Coding activeConditionClinicalStatus() {
        return requireResource("condition-clinical-active").toCoding();
    }

    public Coding conditionVerificationStatus(boolean confirmed) {
        return requireResource(confirmed ? "condition-verification-confirmed" : "condition-verification-provisional").toCoding();
    }

    public Coding encounterDiagnosisCategory() {
        return requireResource("condition-category-encounter-diagnosis").toCoding();
    }

    public Coding documentCategoryClinicalNote() {
        return requireResource("document-category-clinical-note").toCoding();
    }

    public Coding documentTypeMedicalRecord() {
        return requireResource("document-type-medical-record").toCoding();
    }

    public Coding icd11Coding(String code, String display) {
        return new Coding(ICD11_SYSTEM, code, display);
    }

    private void registerObservation(String key, String system, String code, String display, String unit) {
        observationDefinitions.put(key, new ObservationDefinition(system, code, display, unit));
    }

    private void loadObservationDefinitions() {
        ObjectMapper objectMapper = new ObjectMapper();
        loadDefinitions(objectMapper, OBSERVATION_REGISTRY_RESOURCE, new TypeReference<List<ObservationRegistryEntry>>() {
        }, entries -> {
            for (ObservationRegistryEntry entry : entries) {
                registerObservation(entry.key(), entry.system(), entry.code(), entry.display(), entry.unit());
            }
        });
    }

    private void loadResourceDefinitions() {
        ObjectMapper objectMapper = new ObjectMapper();
        loadDefinitions(objectMapper, RESOURCE_REGISTRY_RESOURCE, new TypeReference<List<ResourceRegistryEntry>>() {
        }, entries -> {
            for (ResourceRegistryEntry entry : entries) {
                resourceDefinitions.put(entry.key(), new ResourceDefinition(entry.system(), entry.code(), entry.display()));
            }
        });
    }

    private void loadHealthRecordObservationMappings() {
        ObjectMapper objectMapper = new ObjectMapper();
        loadDefinitions(objectMapper, HEALTH_RECORD_MAPPING_RESOURCE, new TypeReference<List<ObservationFieldMappingEntry>>() {
        }, entries -> {
            for (ObservationFieldMappingEntry entry : entries) {
                healthRecordObservationMappings.add(new ObservationFieldMapping(
                        entry.field(),
                        entry.observationKey(),
                        ObservationValueType.from(entry.valueType())
                ));
            }
        });
    }

    private <T> void loadDefinitions(ObjectMapper objectMapper,
                                     String resourcePath,
                                     TypeReference<List<T>> typeReference,
                                     RegistryConsumer<T> consumer) {
        try (InputStream inputStream = FhirCodingRegistry.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("missing FHIR registry resource: " + resourcePath);
            }
            List<T> entries = objectMapper.readValue(inputStream, typeReference);
            consumer.accept(entries);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to load FHIR registry: " + resourcePath, e);
        }
    }

    private ResourceDefinition requireResource(String key) {
        ResourceDefinition definition = resourceDefinitions.get(key);
        if (definition == null) {
            throw new IllegalArgumentException("unknown FHIR resource mapping: " + key);
        }
        return definition;
    }

    public record ObservationDefinition(String system, String code, String display, String unit) {
        public Coding toCoding() {
            return new Coding(system, code, display);
        }
    }

    public record ResourceDefinition(String system, String code, String display) {
        public Coding toCoding() {
            return new Coding(system, code, display);
        }
    }

    public record ObservationFieldMapping(String field, String observationKey, ObservationValueType valueType) {
    }

    public enum ObservationValueType {
        QUANTITY,
        BOOLEAN,
        STRING;

        static ObservationValueType from(String rawValue) {
            if (rawValue == null || rawValue.isBlank()) {
                throw new IllegalArgumentException("missing observation value type");
            }
            return switch (rawValue.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "quantity" -> QUANTITY;
                case "boolean" -> BOOLEAN;
                case "string" -> STRING;
                default -> throw new IllegalArgumentException("unsupported observation value type: " + rawValue);
            };
        }
    }

    @FunctionalInterface
    private interface RegistryConsumer<T> {
        void accept(List<T> entries);
    }

    private record ObservationRegistryEntry(String key,
                                            String system,
                                            String code,
                                            String display,
                                            String unit) {
    }

    private record ResourceRegistryEntry(String key,
                                         String system,
                                         String code,
                                         String display) {
    }

    private record ObservationFieldMappingEntry(String field,
                                                String observationKey,
                                                String valueType) {
    }
}
