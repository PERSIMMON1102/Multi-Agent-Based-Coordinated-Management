package com.medical.agent.service.fhir;

import com.medical.agent.common.dto.HealthRecordDTO;
import com.medical.agent.model.entity.HealthRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FhirCodingRegistryTest {

    private final FhirCodingRegistry registry = new FhirCodingRegistry();

    @Test
    void shouldReturnObservationDefinitionsFromCentralRegistry() {
        FhirCodingRegistry.ObservationDefinition fastingGlucose = registry.requireObservation("fasting-glucose");

        assertEquals(FhirCodingRegistry.LOINC_SYSTEM, fastingGlucose.system());
        assertEquals("1558-6", fastingGlucose.code());
        assertEquals("mmol/L", fastingGlucose.unit());
    }

    @Test
    void shouldReturnStandardConditionAndDocumentCodings() {
        assertEquals("AMB", registry.encounterTypeAmbulatory().getCode());
        assertEquals("encounter-diagnosis", registry.encounterDiagnosisCategory().getCode());
        assertEquals("clinical-note", registry.documentCategoryClinicalNote().getCode());
        assertEquals("11506-3", registry.documentTypeMedicalRecord().getCode());
        assertEquals("active", registry.activeConditionClinicalStatus().getCode());
        assertEquals("confirmed", registry.conditionVerificationStatus(true).getCode());
        assertEquals("provisional", registry.conditionVerificationStatus(false).getCode());
    }

    @Test
    void shouldLoadCentralHealthRecordObservationMappingsForEntityAndDto() {
        BeanWrapper entityWrapper = new BeanWrapperImpl(new HealthRecord());
        BeanWrapper dtoWrapper = new BeanWrapperImpl(new HealthRecordDTO());

        assertFalse(registry.healthRecordObservationMappings().isEmpty());

        FhirCodingRegistry.ObservationFieldMapping fastingGlucose = registry.healthRecordObservationMappings().stream()
                .filter(mapping -> "fastingGlucose".equals(mapping.field()))
                .findFirst()
                .orElseThrow();

        assertEquals("fasting-glucose", fastingGlucose.observationKey());
        assertEquals(FhirCodingRegistry.ObservationValueType.QUANTITY, fastingGlucose.valueType());

        for (FhirCodingRegistry.ObservationFieldMapping mapping : registry.healthRecordObservationMappings()) {
            assertTrue(entityWrapper.isReadableProperty(mapping.field()),
                    "unreadable health record property: " + mapping.field());
            assertTrue(dtoWrapper.isReadableProperty(mapping.field()),
                    "unreadable health record dto property: " + mapping.field());
            assertDoesNotThrow(() -> registry.requireObservation(mapping.observationKey()));
        }
    }
}
