package com.medical.agent.service.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.medical.agent.common.dto.HealthRecordDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class HuaweiMeasurementMapper implements DeviceMeasurementMapper {

    private final DeviceImportNormalizer normalizer;

    public HuaweiMeasurementMapper(DeviceImportNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    @Override
    public boolean supports(String vendor) {
        return "huawei".equalsIgnoreCase(vendor);
    }

    @Override
    public HealthRecordDTO toHealthRecord(Long patientId, DeviceMeasurement measurement) {
        JsonNode properties = measurement.properties();
        LocalDateTime recordedAt = normalizer.parseDateTime(measurement.eventTime());
        LocalDate recordDate = recordedAt != null ? recordedAt.toLocalDate() : LocalDate.now();

        Double weight = normalizer.numberOf(properties, "weight", "weight_kg", "body_weight");
        Double height = normalizer.numberOf(properties, "height", "height_cm");
        Double bmi = normalizer.numberOf(properties, "bmi", "body_mass_index");
        if (bmi == null && height != null && weight != null && height > 0) {
            double meters = height / 100.0;
            bmi = Math.round((weight / (meters * meters)) * 10.0) / 10.0;
        }

        return HealthRecordDTO.builder()
                .patientId(patientId)
                .recordDate(recordDate)
                .recordedAt(recordedAt)
                .systolicBp(normalizer.integerOf(properties, "systolic_bp", "systolicBp", "sbp", "high_pressure"))
                .diastolicBp(normalizer.integerOf(properties, "diastolic_bp", "diastolicBp", "dbp", "low_pressure"))
                .restingHeartRate(normalizer.integerOf(properties, "heart_rate", "restingHeartRate", "pulse", "hr"))
                .fastingGlucose(normalizer.numberOf(properties, "fasting_glucose", "fastingGlucose", "blood_glucose", "glucose"))
                .postprandialGlucose(normalizer.numberOf(properties, "postprandial_glucose", "postprandialGlucose", "post_meal_glucose"))
                .hba1c(normalizer.numberOf(properties, "hba1c", "glycated_hemoglobin"))
                .weight(weight)
                .height(height)
                .bmi(bmi)
                .totalCholesterol(normalizer.numberOf(properties, "total_cholesterol", "totalCholesterol"))
                .triglycerides(normalizer.numberOf(properties, "triglycerides", "triglyceride", "tg"))
                .ldlCholesterol(normalizer.numberOf(properties, "ldl_cholesterol", "ldlCholesterol", "ldl"))
                .hdlCholesterol(normalizer.numberOf(properties, "hdl_cholesterol", "hdlCholesterol", "hdl"))
                .serumCreatinine(normalizer.numberOf(properties, "serum_creatinine", "creatinine", "scr"))
                .egfr(normalizer.numberOf(properties, "egfr"))
                .uacr(normalizer.numberOf(properties, "uacr"))
                .hemoglobin(normalizer.numberOf(properties, "hemoglobin", "hb"))
                .dataSource("iot_import")
                .sourceVendor("huawei")
                .sourceDeviceType(measurement.deviceType())
                .sourceDeviceId(measurement.deviceId())
                .build();
    }

    @Override
    public boolean hasImportableMetric(HealthRecordDTO dto) {
        return dto.getSystolicBp() != null
                || dto.getDiastolicBp() != null
                || dto.getRestingHeartRate() != null
                || dto.getFastingGlucose() != null
                || dto.getPostprandialGlucose() != null
                || dto.getHba1c() != null
                || dto.getWeight() != null
                || dto.getHeight() != null
                || dto.getBmi() != null
                || dto.getTotalCholesterol() != null
                || dto.getTriglycerides() != null
                || dto.getLdlCholesterol() != null
                || dto.getHdlCholesterol() != null
                || dto.getSerumCreatinine() != null
                || dto.getEgfr() != null
                || dto.getUacr() != null
                || dto.getHemoglobin() != null;
    }
}
