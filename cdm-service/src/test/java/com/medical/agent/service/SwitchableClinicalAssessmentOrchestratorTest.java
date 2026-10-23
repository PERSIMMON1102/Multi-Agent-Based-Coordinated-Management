package com.medical.agent.service;

import com.medical.agent.common.enums.DiseaseType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SwitchableClinicalAssessmentOrchestratorTest {

    @Test
    void shouldUseReactProviderWhenConfigured() {
        JadeClinicalAssessmentOrchestrator jade = mock(JadeClinicalAssessmentOrchestrator.class);
        ReActClinicalAssessmentOrchestrator react = mock(ReActClinicalAssessmentOrchestrator.class);
        SwitchableClinicalAssessmentOrchestrator orchestrator = new SwitchableClinicalAssessmentOrchestrator(jade, react);
        ReflectionTestUtils.setField(orchestrator, "provider", "react");

        orchestrator.triggerAssessment(11L, DiseaseType.HYPERTENSION, Map.of("systolicBp", 160));

        verify(react, times(1)).triggerAssessment(11L, DiseaseType.HYPERTENSION, Map.of("systolicBp", 160), Map.of());
        verifyNoInteractions(jade);
    }

    @Test
    void shouldUseJadeProviderWhenConfiguredAndAvailable() {
        JadeClinicalAssessmentOrchestrator jade = mock(JadeClinicalAssessmentOrchestrator.class);
        ReActClinicalAssessmentOrchestrator react = mock(ReActClinicalAssessmentOrchestrator.class);
        SwitchableClinicalAssessmentOrchestrator orchestrator = new SwitchableClinicalAssessmentOrchestrator(jade, react);
        ReflectionTestUtils.setField(orchestrator, "provider", "jade");

        orchestrator.triggerAssessment(12L, DiseaseType.DIABETES, Map.of("fastingGlucose", 9.1));

        verify(jade, times(1)).triggerAssessment(12L, DiseaseType.DIABETES, Map.of("fastingGlucose", 9.1), Map.of());
        verifyNoInteractions(react);
    }

    @Test
    void shouldFallbackToReactWhenJadeProviderIsUnavailable() {
        ReActClinicalAssessmentOrchestrator react = mock(ReActClinicalAssessmentOrchestrator.class);
        SwitchableClinicalAssessmentOrchestrator orchestrator = new SwitchableClinicalAssessmentOrchestrator(null, react);
        ReflectionTestUtils.setField(orchestrator, "provider", "jade");

        orchestrator.triggerAssessment(13L, DiseaseType.CKD, Map.of("egfr", 48.0));

        verify(react, times(1)).triggerAssessment(13L, DiseaseType.CKD, Map.of("egfr", 48.0), Map.of());
    }

    @Test
    void shouldFallbackToReactWhenProviderIsUnsupported() {
        JadeClinicalAssessmentOrchestrator jade = mock(JadeClinicalAssessmentOrchestrator.class);
        ReActClinicalAssessmentOrchestrator react = mock(ReActClinicalAssessmentOrchestrator.class);
        SwitchableClinicalAssessmentOrchestrator orchestrator = new SwitchableClinicalAssessmentOrchestrator(jade, react);
        ReflectionTestUtils.setField(orchestrator, "provider", "legacy");

        orchestrator.triggerAssessment(14L, DiseaseType.CHD, Map.of("ldlCholesterol", 4.1));

        verify(react, times(1)).triggerAssessment(14L, DiseaseType.CHD, Map.of("ldlCholesterol", 4.1), Map.of());
        verifyNoInteractions(jade);
    }
}
