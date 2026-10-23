package com.medical.agent.service.papercase;

import com.medical.agent.common.dto.HealthRecordDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperCaseExtractionResult {
    private String rawText;
    private String patientName;
    private String gender;
    private Integer age;
    private String chiefComplaint;
    private String presentIllness;
    private String physicalExam;
    private String preliminaryDiagnosis;
    private HealthRecordDTO structuredMetrics;
    private String rawJson;
}
