package com.medical.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.common.dto.RagEvidenceDTO;
import com.medical.agent.common.dto.RagQueryContextDTO;
import com.medical.agent.common.dto.VideoResourceDTO;
import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.model.entity.FollowUpPlan;
import com.medical.agent.model.entity.HealthRecord;
import com.medical.agent.model.entity.HealthReport;
import com.medical.agent.model.entity.HealthReportReferenceSnapshot;
import com.medical.agent.model.entity.HealthReportVideoSnapshot;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.RiskAssessment;
import com.medical.agent.common.enums.FollowUpStatus;
import com.medical.agent.model.repository.FollowUpPlanRepository;
import com.medical.agent.model.repository.HealthRecordRepository;
import com.medical.agent.model.repository.HealthReportReferenceSnapshotRepository;
import com.medical.agent.model.repository.HealthReportRepository;
import com.medical.agent.model.repository.HealthReportVideoSnapshotRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.repository.RiskAssessmentRepository;
import com.medical.agent.service.report.agent.HealthReportSupervisorService;
import com.medical.agent.service.report.agent.ReportGenerationState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HealthReportService {

    public static final String TYPE_PATIENT_WEEKLY = "patient_weekly";
    public static final String TYPE_DOCTOR_ASSESSMENT = "doctor_assessment";

    private final PatientRepository patientRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final FollowUpPlanRepository followUpPlanRepository;
    private final HealthRecordRepository healthRecordRepository;
    private final HealthReportRepository healthReportRepository;
    private final HealthReportVideoSnapshotRepository healthReportVideoSnapshotRepository;
    private final HealthReportReferenceSnapshotRepository healthReportReferenceSnapshotRepository;
    private final VideoResourceService videoResourceService;
    private final HealthReportSupervisorService healthReportSupervisorService;
    private final ObjectMapper objectMapper;

    public HealthReportService(PatientRepository patientRepository,
                               RiskAssessmentRepository riskAssessmentRepository,
                               FollowUpPlanRepository followUpPlanRepository,
                               HealthRecordRepository healthRecordRepository,
                               HealthReportRepository healthReportRepository,
                               HealthReportVideoSnapshotRepository healthReportVideoSnapshotRepository,
                               HealthReportReferenceSnapshotRepository healthReportReferenceSnapshotRepository,
                               VideoResourceService videoResourceService,
                               HealthReportSupervisorService healthReportSupervisorService,
                               ObjectMapper objectMapper) {
        this.patientRepository = patientRepository;
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.followUpPlanRepository = followUpPlanRepository;
        this.healthRecordRepository = healthRecordRepository;
        this.healthReportRepository = healthReportRepository;
        this.healthReportVideoSnapshotRepository = healthReportVideoSnapshotRepository;
        this.healthReportReferenceSnapshotRepository = healthReportReferenceSnapshotRepository;
        this.videoResourceService = videoResourceService;
        this.healthReportSupervisorService = healthReportSupervisorService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> generatePatientWeekly(Long patientId) {
        return generate(patientId, TYPE_PATIENT_WEEKLY);
    }

    @Transactional
    public Map<String, Object> generateDoctorAssessment(Long patientId) {
        return generate(patientId, TYPE_DOCTOR_ASSESSMENT);
    }

    public List<Map<String, Object>> listByPatient(Long patientId, String type) {
        List<HealthReport> reports;
        if (type == null || type.isBlank()) {
            reports = healthReportRepository.findByPatientIdOrderByGeneratedAtDesc(patientId);
        } else {
            reports = healthReportRepository.findByPatientIdAndTypeOrderByGeneratedAtDesc(patientId, type.trim());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (HealthReport report : reports) {
            result.add(toMap(report));
        }
        return result;
    }

    private Map<String, Object> generate(Long patientId, String type) {
        ReportGenerationState generationState = healthReportSupervisorService.generate(patientId, type);

        HealthReport report = HealthReport.builder()
                .patientId(patientId)
                .type(type)
                .title(generationState.getTitle())
                .content(generationState.getContent())
                .agentTraceJson(toAgentTraceJson(generationState.getAgentSteps()))
                .build();
        report = healthReportRepository.save(report);
        saveReferenceSnapshots(report, generationState.getDisplayReferences());
        saveVideoSnapshots(report, patientId);
        Map<String, Object> result = toMap(report);
        result.put("agentSteps", generationState.getAgentSteps());
        return result;
    }

    private void saveVideoSnapshots(HealthReport report, Long patientId) {
        List<VideoResourceDTO> recommendedVideos = videoResourceService.getRecommendedVideos(patientId, 4);
        if (recommendedVideos.isEmpty()) {
            return;
        }

        List<HealthReportVideoSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < recommendedVideos.size(); i++) {
            snapshots.add(videoResourceService.toSnapshotEntity(report.getId(), recommendedVideos.get(i), i));
        }
        healthReportVideoSnapshotRepository.saveAll(snapshots);
    }

    private void saveReferenceSnapshots(HealthReport report, List<RagEvidenceDTO> references) {
        if (references == null || references.isEmpty()) {
            return;
        }
        List<HealthReportReferenceSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < references.size(); i++) {
            RagEvidenceDTO ref = references.get(i);
            snapshots.add(HealthReportReferenceSnapshot.builder()
                    .reportId(report.getId())
                    .refType(ref.getSourceType())
                    .refId(ref.getRefId())
                    .videoId(ref.getVideoId())
                    .diseaseKey(ref.getDiseaseKey())
                    .title(ref.getTitle())
                    .section(ref.getSection())
                    .source(ref.getSource())
                    .publishedAt(ref.getPublishedAt())
                    .contentSnippet(ref.getContentSnippet())
                    .score(ref.getScore())
                    .sortOrder(i)
                    .build());
        }
        healthReportReferenceSnapshotRepository.saveAll(snapshots);
    }

    private Map<String, Object> toMap(HealthReport report) {
        List<VideoResourceDTO> recommendedVideos = healthReportVideoSnapshotRepository
                .findByReportIdOrderBySortOrderAscIdAsc(report.getId())
                .stream()
                .map(videoResourceService::toDTO)
                .toList();
        List<RagEvidenceDTO> references = healthReportReferenceSnapshotRepository
                .findByReportIdOrderBySortOrderAscIdAsc(report.getId())
                .stream()
                .map(this::toReferenceDTO)
                .toList();

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", report.getId());
        map.put("patientId", report.getPatientId());
        map.put("type", report.getType());
        map.put("title", report.getTitle());
        map.put("content", report.getContent());
        map.put("generatedAt", report.getGeneratedAt() != null ? report.getGeneratedAt().toString() : null);
        map.put("recommendedVideos", recommendedVideos);
        map.put("references", references);
        map.put("agentSteps", parseAgentTraceJson(report.getAgentTraceJson()));
        map.putAll(buildWorkflowMeta(report));
        return map;
    }

    private Map<String, Object> buildWorkflowMeta(HealthReport report) {
        Map<String, Object> meta = new LinkedHashMap<>();
        boolean stale = false;
        List<String> staleReasons = new ArrayList<>();
        String workflowStatusCode = "report_current";
        String workflowStatusLabel = "报告当前有效";
        String nextAction = "可直接查看详情";
        String freshnessLabel = "当前有效";
        String basisUpdatedAt = null;

        if (TYPE_DOCTOR_ASSESSMENT.equals(report.getType())) {
            Long patientId = report.getPatientId();
            RiskAssessment latestAssessment = riskAssessmentRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                    .stream()
                    .findFirst()
                    .orElse(null);
            List<FollowUpPlan> followUpPlans = followUpPlanRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
            List<HealthRecord> healthRecords = healthRecordRepository.findByPatientIdOrderByRecordDateDesc(patientId);

            FollowUpPlan latestFollowUp = followUpPlans.stream()
                    .max(Comparator.comparing(this::resolveFollowUpUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(null);
            HealthRecord latestHealthRecord = healthRecords.stream()
                    .max(Comparator.comparing(this::resolveHealthRecordUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(null);

            LocalDateTime latestAssessmentAt = latestAssessment == null ? null : latestAssessment.getCreatedAt();
            LocalDateTime latestFollowUpAt = latestFollowUp == null ? null : resolveFollowUpUpdatedAt(latestFollowUp);
            LocalDateTime latestHealthRecordAt = latestHealthRecord == null ? null : resolveHealthRecordUpdatedAt(latestHealthRecord);
            LocalDateTime generatedAt = report.getGeneratedAt();

            if (isAfter(latestAssessmentAt, generatedAt)) {
                stale = true;
                staleReasons.add("风险评估已更新");
            }
            if (isAfter(latestFollowUpAt, generatedAt)) {
                stale = true;
                staleReasons.add("随访计划或状态已变化");
            }
            if (isAfter(latestHealthRecordAt, generatedAt)) {
                stale = true;
                staleReasons.add("健康记录已回流");
            }

            LocalDateTime basisTime = latestOf(latestAssessmentAt, latestFollowUpAt, latestHealthRecordAt);
            basisUpdatedAt = basisTime == null ? null : basisTime.toString();

            if (latestAssessment == null) {
                workflowStatusCode = "awaiting_assessment";
                workflowStatusLabel = "待风险评估";
                nextAction = "先补录或导入数据";
                freshnessLabel = "待评估";
            } else if (latestFollowUp == null) {
                workflowStatusCode = "awaiting_followup";
                workflowStatusLabel = "待随访计划";
                nextAction = "核对评估后是否已生成计划";
                freshnessLabel = "待补计划";
            } else if (latestFollowUp.getStatus() == FollowUpStatus.COMPLETED && stale) {
                workflowStatusCode = "awaiting_report_refresh";
                workflowStatusLabel = "待更新医生报告";
                nextAction = "建议重新生成报告";
                freshnessLabel = "待更新";
            } else if (latestFollowUp.getStatus() == FollowUpStatus.COMPLETED) {
                workflowStatusCode = "closed_loop";
                workflowStatusLabel = "主链已闭环";
                nextAction = "可查看本次闭环结果";
                freshnessLabel = "当前有效";
            } else if (latestFollowUp.getStatus() == FollowUpStatus.CANCELLED) {
                workflowStatusCode = "followup_cancelled";
                workflowStatusLabel = "随访已取消";
                nextAction = "建议人工确认后续安排";
                freshnessLabel = stale ? "待更新" : "当前有效";
            } else {
                workflowStatusCode = "followup_active";
                workflowStatusLabel = "随访执行中";
                nextAction = stale ? "建议更新报告" : "继续随访执行";
                freshnessLabel = stale ? "待更新" : "当前有效";
            }
        }

        meta.put("stale", stale);
        meta.put("staleReasons", staleReasons);
        meta.put("basisUpdatedAt", basisUpdatedAt);
        meta.put("workflowStatusCode", workflowStatusCode);
        meta.put("workflowStatusLabel", workflowStatusLabel);
        meta.put("nextAction", nextAction);
        meta.put("freshnessLabel", freshnessLabel);
        return meta;
    }

    private LocalDateTime resolveFollowUpUpdatedAt(FollowUpPlan plan) {
        if (plan == null) {
            return null;
        }
        return plan.getUpdatedAt() != null ? plan.getUpdatedAt() : plan.getCreatedAt();
    }

    private LocalDateTime resolveHealthRecordUpdatedAt(HealthRecord record) {
        if (record == null) {
            return null;
        }
        if (record.getRecordedAt() != null) {
            return record.getRecordedAt();
        }
        if (record.getCreatedAt() != null) {
            return record.getCreatedAt();
        }
        return record.getRecordDate() == null ? null : record.getRecordDate().atStartOfDay();
    }

    private boolean isAfter(LocalDateTime left, LocalDateTime right) {
        return left != null && right != null && left.isAfter(right);
    }

    private LocalDateTime latestOf(LocalDateTime... values) {
        LocalDateTime latest = null;
        for (LocalDateTime value : values) {
            if (value == null) {
                continue;
            }
            if (latest == null || value.isAfter(latest)) {
                latest = value;
            }
        }
        return latest;
    }

    private RagEvidenceDTO toReferenceDTO(HealthReportReferenceSnapshot snapshot) {
        return RagEvidenceDTO.builder()
                .sourceType(snapshot.getRefType())
                .refId(snapshot.getRefId())
                .videoId(snapshot.getVideoId())
                .diseaseKey(snapshot.getDiseaseKey())
                .title(snapshot.getTitle())
                .section(snapshot.getSection())
                .source(snapshot.getSource())
                .publishedAt(snapshot.getPublishedAt())
                .contentSnippet(snapshot.getContentSnippet())
                .score(snapshot.getScore())
                .build();
    }

    private String toAgentTraceJson(List<Map<String, Object>> agentSteps) {
        if (agentSteps == null || agentSteps.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(agentSteps);
        } catch (Exception e) {
            return null;
        }
    }

    private List<Map<String, Object>> parseAgentTraceJson(String agentTraceJson) {
        if (agentTraceJson == null || agentTraceJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(agentTraceJson, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
