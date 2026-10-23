package com.medical.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.common.enums.FollowUpStatus;
import com.medical.agent.common.enums.RiskLevel;
import com.medical.agent.model.entity.AiChatMessage;
import com.medical.agent.model.entity.AiChatSession;
import com.medical.agent.model.entity.FollowUpPlan;
import com.medical.agent.model.entity.HealthReport;
import com.medical.agent.model.entity.HealthReportVideoSnapshot;
import com.medical.agent.model.entity.Notification;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.PaperCaseRecord;
import com.medical.agent.model.entity.RiskAssessment;
import com.medical.agent.model.entity.VideoResource;
import com.medical.agent.model.entity.VideoWatchSession;
import com.medical.agent.model.entity.ClinicalDataJob;
import com.medical.agent.model.repository.AiChatMessageRepository;
import com.medical.agent.model.repository.AiChatSessionRepository;
import com.medical.agent.model.repository.ClinicalDataJobRepository;
import com.medical.agent.model.repository.FollowUpPlanRepository;
import com.medical.agent.model.repository.HealthReportRepository;
import com.medical.agent.model.repository.HealthReportVideoSnapshotRepository;
import com.medical.agent.model.repository.NotificationRepository;
import com.medical.agent.model.repository.PaperCaseRecordRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.repository.RiskAssessmentRepository;
import com.medical.agent.model.repository.VideoResourceRepository;
import com.medical.agent.model.repository.VideoWatchSessionRepository;
import com.medical.agent.service.cache.CacheNames;
import com.medical.agent.service.clinicaljob.ClinicalDataJobService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DoctorDashboardService {

    private static final DateTimeFormatter DATE_KEY = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter HOUR_KEY = DateTimeFormatter.ofPattern("HH:00");

    private final PatientRepository patientRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final FollowUpPlanRepository followUpPlanRepository;
    private final AiChatSessionRepository aiChatSessionRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final HealthReportRepository healthReportRepository;
    private final HealthReportVideoSnapshotRepository healthReportVideoSnapshotRepository;
    private final NotificationRepository notificationRepository;
    private final VideoResourceRepository videoResourceRepository;
    private final VideoWatchSessionRepository videoWatchSessionRepository;
    private final PaperCaseRecordRepository paperCaseRecordRepository;
    private final ClinicalDataJobRepository clinicalDataJobRepository;
    private final ObjectMapper objectMapper;

    public DoctorDashboardService(PatientRepository patientRepository,
                                  RiskAssessmentRepository riskAssessmentRepository,
                                  FollowUpPlanRepository followUpPlanRepository,
                                  AiChatSessionRepository aiChatSessionRepository,
                                  AiChatMessageRepository aiChatMessageRepository,
                                  HealthReportRepository healthReportRepository,
                                  HealthReportVideoSnapshotRepository healthReportVideoSnapshotRepository,
                                  NotificationRepository notificationRepository,
                                  VideoResourceRepository videoResourceRepository,
                                  VideoWatchSessionRepository videoWatchSessionRepository,
                                  PaperCaseRecordRepository paperCaseRecordRepository,
                                  ClinicalDataJobRepository clinicalDataJobRepository,
                                  ObjectMapper objectMapper) {
        this.patientRepository = patientRepository;
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.followUpPlanRepository = followUpPlanRepository;
        this.aiChatSessionRepository = aiChatSessionRepository;
        this.aiChatMessageRepository = aiChatMessageRepository;
        this.healthReportRepository = healthReportRepository;
        this.healthReportVideoSnapshotRepository = healthReportVideoSnapshotRepository;
        this.notificationRepository = notificationRepository;
        this.videoResourceRepository = videoResourceRepository;
        this.videoWatchSessionRepository = videoWatchSessionRepository;
        this.paperCaseRecordRepository = paperCaseRecordRepository;
        this.clinicalDataJobRepository = clinicalDataJobRepository;
        this.objectMapper = objectMapper;
    }

    @Cacheable(cacheNames = CacheNames.DOCTOR_DASHBOARD_OVERVIEW, key = "#doctorId == null ? 'all' : #doctorId.toString()", sync = true)
    public Map<String, Object> getOverview(Long doctorId) {
        List<Patient> patients = loadPatients(doctorId);
        List<Long> patientIds = patients.stream().map(Patient::getId).toList();
        Map<Long, Patient> patientMap = patients.stream().collect(Collectors.toMap(Patient::getId, patient -> patient));

        List<RiskAssessment> assessments = patientIds.isEmpty()
                ? List.of()
                : riskAssessmentRepository.findByPatientIdInOrderByCreatedAtDesc(patientIds);
        List<FollowUpPlan> followUpPlans = patientIds.isEmpty()
                ? List.of()
                : followUpPlanRepository.findByPatientIdIn(patientIds);
        List<HealthReport> reports = patientIds.isEmpty()
                ? List.of()
                : healthReportRepository.findByPatientIdInOrderByGeneratedAtDesc(patientIds);
        List<PaperCaseRecord> paperCaseRecords = patientIds.isEmpty()
                ? List.of()
                : paperCaseRecordRepository.findByPatientIdInOrderByCreatedAtDesc(patientIds);

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime startOfTrend = today.minusDays(6).atStartOfDay();

        Map<Long, RiskAssessment> latestAssessmentByPatient = latestAssessmentByPatient(assessments);
        List<RiskAssessment> latestAssessments = latestAssessmentByPatient.values().stream()
                .sorted(Comparator.comparing(RiskAssessment::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .toList();
        List<RiskAssessment> highRiskAssessments = latestAssessments.stream()
                .filter(this::isHighRisk)
                .toList();

        List<RiskAssessment> recentRiskAssessments = patientIds.isEmpty()
                ? List.of()
                : riskAssessmentRepository.findByPatientIdInAndCreatedAtBetweenOrderByCreatedAtAsc(patientIds, startOfTrend, now);

        List<Notification> allNotifications = notificationRepository.findTop50ByOrderByCreatedAtDesc();
        List<Notification> scopedNotifications = allNotifications.stream()
                .filter(notification -> belongsToDoctor(notification, patientIds))
                .toList();
        List<Notification> todayNotifications = scopedNotifications.stream()
                .filter(notification -> isWithin(notification.getCreatedAt(), startOfToday, now))
                .toList();

        List<AiChatSession> todaySessions = doctorId == null
                ? List.of()
                : aiChatSessionRepository.findByOwnerRoleAndOwnerUserIdAndLastMessageAtBetween("doctor", doctorId, startOfToday, now);
        List<AiChatSession> recentSessions = doctorId == null
                ? List.of()
                : aiChatSessionRepository.findTop20ByOwnerRoleAndOwnerUserIdOrderByLastMessageAtDesc("doctor", doctorId);

        List<String> recentSessionIds = recentSessions.stream().map(AiChatSession::getId).toList();
        List<AiChatMessage> todayChatMessages = recentSessionIds.isEmpty()
                ? List.of()
                : aiChatMessageRepository.findBySessionIdInAndCreatedAtBetween(recentSessionIds, startOfToday, now);

        List<HealthReport> todayReports = reports.stream()
                .filter(report -> isWithin(report.getGeneratedAt(), startOfToday, now))
                .toList();
        List<HealthReportVideoSnapshot> videoSnapshots = reports.isEmpty()
                ? List.of()
                : healthReportVideoSnapshotRepository.findByReportIdIn(reports.stream().map(HealthReport::getId).toList());
        List<VideoWatchSession> watchSessions = patientIds.isEmpty()
                ? List.of()
                : videoWatchSessionRepository.findByPatientIdIn(patientIds);
        Map<String, Object> paperCaseOps = buildPaperCaseSection(patientMap, paperCaseRecords);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("generatedAt", now.toString());
        response.put("doctorId", doctorId);
        response.put("kpis", buildKpis(
                patients.size(),
                highRiskAssessments.size(),
                todayNotifications.size(),
                followUpPlans,
                todayReports,
                todaySessions.size(),
                paperCaseOps
        ));
        response.put("risk", buildRiskSection(patientMap, latestAssessments, highRiskAssessments, recentRiskAssessments, followUpPlans));
        response.put("followUp", buildFollowUpSection(patientMap, followUpPlans, today));
        response.put("paperCaseOps", paperCaseOps);
        response.put("agentActivity", buildAgentActivitySection(patientMap, todaySessions, todayChatMessages, todayReports, todayNotifications, recentSessions, scopedNotifications));
        response.put("videos", buildVideoSection(videoSnapshots, watchSessions));
        return response;
    }

    private List<Patient> loadPatients(Long doctorId) {
        if (doctorId == null) {
            return patientRepository.findAll();
        }
        return patientRepository.findByAssignedDoctorId(doctorId);
    }

    private Map<String, Object> buildKpis(int managedPatients,
                                          int highRiskPatients,
                                          int alertsToday,
                                          List<FollowUpPlan> followUpPlans,
                                          List<HealthReport> todayReports,
                                          int todaySessions,
                                          Map<String, Object> paperCaseOps) {
        long overdueCount = followUpPlans.stream().filter(this::isOverduePlan).count();
        long pendingCount = followUpPlans.stream().filter(plan -> plan.getStatus() == FollowUpStatus.PENDING).count();
        Number paperCaseAttentionPatients = numberValue(paperCaseOps.get("attentionPatientCount"));
        Number paperCasePendingReviews = numberValue(paperCaseOps.get("pendingReviewCount"));
        Number paperCaseFailedSyncs = numberValue(paperCaseOps.get("failedSyncCount"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("managedPatients", managedPatients);
        result.put("highRiskPatients", highRiskPatients);
        result.put("alertsToday", alertsToday);
        result.put("pendingFollowUps", pendingCount);
        result.put("overdueFollowUps", overdueCount);
        result.put("reportsToday", todayReports.size());
        result.put("doctorChatsToday", todaySessions);
        result.put("paperCaseAttentionPatients", paperCaseAttentionPatients.longValue());
        result.put("paperCasePendingReviews", paperCasePendingReviews.longValue());
        result.put("paperCaseFailedSyncs", paperCaseFailedSyncs.longValue());
        return result;
    }

    private Map<String, Object> buildPaperCaseSection(Map<Long, Patient> patientMap,
                                                      List<PaperCaseRecord> paperCaseRecords) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (paperCaseRecords.isEmpty()) {
            result.put("totalCount", 0L);
            result.put("attentionCount", 0L);
            result.put("attentionPatientCount", 0L);
            result.put("pendingReviewCount", 0L);
            result.put("failedSyncCount", 0L);
            result.put("recentAttentionPatients", List.of());
            return result;
        }

        Map<Long, PaperCasePatientOpsAccumulator> patientOps = new LinkedHashMap<>();
        for (PaperCaseRecord record : paperCaseRecords) {
            if (record.getPatientId() == null || record.getId() == null) {
                continue;
            }
            PaperCasePatientOpsAccumulator accumulator = patientOps.computeIfAbsent(
                    record.getPatientId(),
                    ignored -> new PaperCasePatientOpsAccumulator()
            );
            accumulator.totalCount++;
            accumulator.recordIds.add(record.getId());
            accumulator.latestCreatedAt = max(accumulator.latestCreatedAt, record.getCreatedAt());
            PaperCaseRecordFlags flags = accumulator.recordFlags.computeIfAbsent(record.getId(), ignored -> new PaperCaseRecordFlags());
            flags.recordDate = record.getRecordDate();
            flags.diagnosis = firstNonBlank(record.getPreliminaryDiagnosis(), flags.diagnosis);
            flags.fileName = firstNonBlank(record.getOriginalFileName(), flags.fileName);
            if (equalsIgnoreCase(record.getIcdCodingStatus(), "pending_review")) {
                accumulator.pendingReviewCount++;
                flags.pendingReview = true;
                flags.attention = true;
            }
            if (equalsIgnoreCase(record.getMedplumSyncStatus(), "failed")) {
                accumulator.failedSyncCount++;
                flags.failedSync = true;
                flags.attention = true;
            }
        }

        List<Long> recordIds = patientOps.values().stream()
                .flatMap(item -> item.recordIds.stream())
                .toList();
        Map<Long, ClinicalDataJob> latestJobsByRecordId = recordIds.isEmpty()
                ? Map.of()
                : clinicalDataJobRepository.findBySourceKindAndSourceRefIdInOrderByCreatedAtDesc(
                        ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD,
                        recordIds
                ).stream()
                .filter(job -> job.getSourceRefId() != null)
                .collect(Collectors.toMap(
                        ClinicalDataJob::getSourceRefId,
                        job -> job,
                        (existing, ignored) -> existing,
                        LinkedHashMap::new
                ));

        patientOps.values().forEach(item -> item.applyLatestJobs(latestJobsByRecordId));

        long totalCount = patientOps.values().stream().mapToLong(item -> item.totalCount).sum();
        long pendingReviewCount = patientOps.values().stream().mapToLong(item -> item.pendingReviewCount).sum();
        long failedSyncCount = patientOps.values().stream().mapToLong(item -> item.failedSyncCount).sum();
        long attentionCount = patientOps.values().stream().mapToLong(PaperCasePatientOpsAccumulator::attentionRecordCount).sum();
        List<Map<String, Object>> recentAttentionPatients = patientOps.entrySet().stream()
                .filter(entry -> entry.getValue().hasAttention())
                .sorted((left, right) -> compareNullableDateTime(
                        right.getValue().latestCreatedAt,
                        left.getValue().latestCreatedAt
                ))
                .limit(6)
                .map(entry -> {
                    Long patientId = entry.getKey();
                    Patient patient = patientMap.get(patientId);
                    PaperCasePatientOpsAccumulator accumulator = entry.getValue();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("patientId", patientId);
                    item.put("patientName", patient == null ? "未知患者" : patient.getName());
                    item.put("attentionCount", accumulator.attentionRecordCount());
                    item.put("pendingReviewCount", accumulator.pendingReviewCount);
                    item.put("failedSyncCount", accumulator.failedSyncCount);
                    item.put("totalCount", accumulator.totalCount);
                    item.put("latestRecordDate", accumulator.latestRecordDateText());
                    item.put("latestDiagnosis", accumulator.latestDiagnosis());
                    item.put("latestFileName", accumulator.latestFileName());
                    return item;
                })
                .toList();

        result.put("totalCount", totalCount);
        result.put("attentionCount", attentionCount);
        result.put("attentionPatientCount", (long) recentAttentionPatients.size());
        result.put("pendingReviewCount", pendingReviewCount);
        result.put("failedSyncCount", failedSyncCount);
        result.put("recentAttentionPatients", recentAttentionPatients);
        return result;
    }

    private Map<String, Object> buildRiskSection(Map<Long, Patient> patientMap,
                                                 List<RiskAssessment> latestAssessments,
                                                 List<RiskAssessment> highRiskAssessments,
                                                 List<RiskAssessment> recentRiskAssessments,
                                                 List<FollowUpPlan> followUpPlans) {
        Map<RiskLevel, Long> distribution = latestAssessments.stream()
                .collect(Collectors.groupingBy(RiskAssessment::getRiskLevel, LinkedHashMap::new, Collectors.counting()));

        List<Map<String, Object>> distributionData = Arrays.stream(RiskLevel.values())
                .map(level -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("riskLevel", level.name());
                    item.put("label", level.getDisplayName());
                    item.put("value", distribution.getOrDefault(level, 0L));
                    return item;
                })
                .toList();

        Map<String, Set<Long>> highRiskTrendMap = initDayBucketSet(7);
        for (RiskAssessment assessment : recentRiskAssessments) {
            if (!isHighRisk(assessment) || assessment.getCreatedAt() == null) {
                continue;
            }
            highRiskTrendMap.computeIfAbsent(assessment.getCreatedAt().toLocalDate().format(DATE_KEY), key -> new LinkedHashSet<>())
                    .add(assessment.getPatientId());
        }

        List<Map<String, Object>> highRiskTrend = highRiskTrendMap.entrySet().stream().map(entry -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", entry.getKey());
            item.put("count", entry.getValue().size());
            return item;
        }).toList();

        Map<String, Map<RiskLevel, Long>> matrix = new LinkedHashMap<>();
        for (RiskAssessment assessment : latestAssessments) {
            String diseaseKey = assessment.getDiseaseType() == null ? "UNKNOWN" : assessment.getDiseaseType().name();
            matrix.computeIfAbsent(diseaseKey, key -> new LinkedHashMap<>());
            matrix.get(diseaseKey).merge(assessment.getRiskLevel(), 1L, Long::sum);
        }

        List<Map<String, Object>> diseaseRiskMatrix = matrix.entrySet().stream().map(entry -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("diseaseType", entry.getKey());
            item.put("label", displayDisease(entry.getKey()));
            for (RiskLevel level : RiskLevel.values()) {
                item.put(level.name().toLowerCase(Locale.ROOT), entry.getValue().getOrDefault(level, 0L));
            }
            return item;
        }).toList();

        Map<Long, FollowUpPlan> nextFollowUpByPatient = nearestFollowUpByPatient(followUpPlans);
        List<Map<String, Object>> highRiskPatients = highRiskAssessments.stream()
                .limit(8)
                .map(assessment -> {
                    Patient patient = patientMap.get(assessment.getPatientId());
                    FollowUpPlan followUpPlan = nextFollowUpByPatient.get(assessment.getPatientId());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("patientId", assessment.getPatientId());
                    item.put("patientName", patient == null ? "未知患者" : patient.getName());
                    item.put("diseaseType", assessment.getDiseaseType() == null ? null : assessment.getDiseaseType().name());
                    item.put("diseaseLabel", assessment.getDiseaseType() == null ? "未知病种" : assessment.getDiseaseType().getDisplayName());
                    item.put("riskLevel", assessment.getRiskLevel() == null ? null : assessment.getRiskLevel().name());
                    item.put("riskLabel", assessment.getRiskLevel() == null ? "未知" : assessment.getRiskLevel().getDisplayName());
                    item.put("createdAt", assessment.getCreatedAt() == null ? null : assessment.getCreatedAt().toString());
                    item.put("followUpStatus", followUpPlan == null || followUpPlan.getStatus() == null ? null : followUpPlan.getStatus().name());
                    item.put("followUpLabel", followUpPlan == null || followUpPlan.getStatus() == null ? "未建计划" : followUpPlan.getStatus().getDisplayName());
                    item.put("nextFollowDate", followUpPlan == null || followUpPlan.getNextFollowDate() == null ? null : followUpPlan.getNextFollowDate().toString());
                    return item;
                })
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("distribution", distributionData);
        result.put("highRiskTrend", highRiskTrend);
        result.put("diseaseRiskMatrix", diseaseRiskMatrix);
        result.put("highRiskPatients", highRiskPatients);
        return result;
    }

    private Map<String, Object> buildFollowUpSection(Map<Long, Patient> patientMap,
                                                     List<FollowUpPlan> followUpPlans,
                                                     LocalDate today) {
        long completed = followUpPlans.stream().filter(plan -> plan.getStatus() == FollowUpStatus.COMPLETED).count();
        long pending = followUpPlans.stream().filter(plan -> plan.getStatus() == FollowUpStatus.PENDING).count();
        long inProgress = followUpPlans.stream().filter(plan -> plan.getStatus() == FollowUpStatus.IN_PROGRESS).count();
        long overdue = followUpPlans.stream().filter(this::isOverduePlan).count();

        List<Map<String, Object>> statusCounts = List.of(
                statusItem(FollowUpStatus.PENDING, pending),
                statusItem(FollowUpStatus.IN_PROGRESS, inProgress),
                statusItem(FollowUpStatus.COMPLETED, completed),
                statusItem(FollowUpStatus.OVERDUE, overdue)
        );

        List<Map<String, Object>> completionTrend = new ArrayList<>();
        for (int offset = 6; offset >= 0; offset--) {
            LocalDate target = today.minusDays(offset);
            long completedCount = followUpPlans.stream()
                    .filter(plan -> plan.getStatus() == FollowUpStatus.COMPLETED)
                    .filter(plan -> plan.getUpdatedAt() != null && plan.getUpdatedAt().toLocalDate().isEqual(target))
                    .count();
            long overdueCount = followUpPlans.stream()
                    .filter(plan -> plan.getStatus() != FollowUpStatus.COMPLETED && plan.getStatus() != FollowUpStatus.CANCELLED)
                    .filter(plan -> plan.getNextFollowDate() != null && plan.getNextFollowDate().isEqual(target))
                    .count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", target.format(DATE_KEY));
            item.put("completed", completedCount);
            item.put("due", overdueCount);
            completionTrend.add(item);
        }

        List<Map<String, Object>> overdueTasks = followUpPlans.stream()
                .filter(this::isOverduePlan)
                .sorted(Comparator.comparing(FollowUpPlan::getNextFollowDate, Comparator.nullsLast(LocalDate::compareTo)))
                .limit(8)
                .map(plan -> {
                    Patient patient = patientMap.get(plan.getPatientId());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", plan.getId());
                    item.put("patientId", plan.getPatientId());
                    item.put("patientName", patient == null ? "未知患者" : patient.getName());
                    item.put("diseaseType", plan.getDiseaseType() == null ? null : plan.getDiseaseType().name());
                    item.put("diseaseLabel", plan.getDiseaseType() == null ? "未知病种" : plan.getDiseaseType().getDisplayName());
                    item.put("status", plan.getStatus() == null ? null : plan.getStatus().name());
                    item.put("statusLabel", plan.getStatus() == null ? "未知" : plan.getStatus().getDisplayName());
                    item.put("nextFollowDate", plan.getNextFollowDate() == null ? null : plan.getNextFollowDate().toString());
                    return item;
                })
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("statusCounts", statusCounts);
        result.put("completionTrend", completionTrend);
        result.put("overdueTasks", overdueTasks);
        return result;
    }

    private Map<String, Object> buildAgentActivitySection(Map<Long, Patient> patientMap,
                                                          List<AiChatSession> todaySessions,
                                                          List<AiChatMessage> todayMessages,
                                                          List<HealthReport> todayReports,
                                                          List<Notification> todayNotifications,
                                                          List<AiChatSession> recentSessions,
                                                          List<Notification> scopedNotifications) {
        int toolCalls = 0;
        int degradedResponses = 0;
        for (AiChatMessage message : todayMessages) {
            if (!"assistant".equalsIgnoreCase(message.getRole())) {
                continue;
            }
            toolCalls += countTools(message.getAgentTraceJson());
            if (isDegradedResponse(message.getContent())) {
                degradedResponses++;
            }
        }

        Map<String, Integer> hourlyChats = initHourBucket(8);
        Map<String, Integer> hourlyReports = initHourBucket(8);
        Map<String, Integer> hourlyAlerts = initHourBucket(8);

        for (AiChatSession session : todaySessions) {
            incrementHourBucket(hourlyChats, session.getLastMessageAt());
        }
        for (HealthReport report : todayReports) {
            incrementHourBucket(hourlyReports, report.getGeneratedAt());
        }
        for (Notification notification : todayNotifications) {
            incrementHourBucket(hourlyAlerts, notification.getCreatedAt());
        }

        List<Map<String, Object>> hourlyTrend = hourlyChats.keySet().stream().map(hour -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("hour", hour);
            item.put("chatSessions", hourlyChats.getOrDefault(hour, 0));
            item.put("reports", hourlyReports.getOrDefault(hour, 0));
            item.put("alerts", hourlyAlerts.getOrDefault(hour, 0));
            return item;
        }).toList();

        List<Map<String, Object>> recentEvents = new ArrayList<>();
        recentSessions.stream().limit(6).forEach(session -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("time", session.getLastMessageAt() == null ? null : session.getLastMessageAt().toString());
            item.put("type", "chat");
            item.put("title", "医生端 AI 健康顾问会话");
            item.put("summary", session.getTitle() == null ? "智能体完成了一轮医生问答" : session.getTitle());
            recentEvents.add(item);
        });
        todayReports.stream().sorted(Comparator.comparing(HealthReport::getGeneratedAt).reversed()).limit(6).forEach(report -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("time", report.getGeneratedAt() == null ? null : report.getGeneratedAt().toString());
            item.put("type", "report");
            item.put("title", report.getTitle() == null ? "生成健康报告" : report.getTitle());
            item.put("summary", "系统已生成新的慢病管理报告");
            Patient patient = patientMap.get(report.getPatientId());
            if (patient != null) {
                item.put("patientName", patient.getName());
            }
            recentEvents.add(item);
        });
        scopedNotifications.stream().limit(6).forEach(notification -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("time", notification.getCreatedAt() == null ? null : notification.getCreatedAt().toString());
            item.put("type", "alert");
            item.put("title", notification.getTitle());
            item.put("summary", notification.getMessage());
            Patient patient = patientMap.get(notification.getPatientId());
            if (patient != null) {
                item.put("patientName", patient.getName());
            }
            recentEvents.add(item);
        });
        recentEvents.sort((left, right) -> compareTime(right.get("time"), left.get("time")));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("doctorChatsToday", todaySessions.size());
        summary.put("reportsToday", todayReports.size());
        summary.put("alertsToday", todayNotifications.size());
        summary.put("toolCallsToday", toolCalls);
        summary.put("degradedResponsesToday", degradedResponses);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("hourlyTrend", hourlyTrend);
        result.put("recentEvents", recentEvents.stream().limit(10).toList());
        return result;
    }

    private Map<String, Object> buildVideoSection(List<HealthReportVideoSnapshot> videoSnapshots,
                                                  List<VideoWatchSession> watchSessions) {
        Map<String, Long> topVideoCounts = videoSnapshots.stream()
                .collect(Collectors.groupingBy(snapshot -> videoKey(snapshot), LinkedHashMap::new, Collectors.counting()));

        Map<String, HealthReportVideoSnapshot> videoExampleMap = new LinkedHashMap<>();
        for (HealthReportVideoSnapshot snapshot : videoSnapshots) {
            videoExampleMap.putIfAbsent(videoKey(snapshot), snapshot);
        }

        Set<Long> videoIds = new LinkedHashSet<>();
        videoSnapshots.stream()
                .map(HealthReportVideoSnapshot::getVideoId)
                .filter(Objects::nonNull)
                .forEach(videoIds::add);
        watchSessions.stream()
                .map(VideoWatchSession::getVideoId)
                .filter(Objects::nonNull)
                .forEach(videoIds::add);
        Map<Long, VideoResource> videoResourceMap = videoIds.isEmpty()
                ? Map.of()
                : videoResourceRepository.findAllById(videoIds).stream()
                .collect(Collectors.toMap(VideoResource::getId, video -> video));

        Map<Long, List<VideoWatchSession>> watchByVideoId = watchSessions.stream()
                .collect(Collectors.groupingBy(VideoWatchSession::getVideoId, LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> topVideos = buildTopVideos(topVideoCounts, videoExampleMap, videoResourceMap, watchByVideoId);

        Map<String, Long> diseaseDistribution = new LinkedHashMap<>();
        if (watchSessions.isEmpty()) {
            for (HealthReportVideoSnapshot snapshot : videoSnapshots) {
                if (snapshot.getDiseaseType() == null || snapshot.getDiseaseType().isBlank()) {
                    continue;
                }
                diseaseDistribution.merge(snapshot.getDiseaseType(), 1L, Long::sum);
            }
        } else {
            for (VideoWatchSession session : watchSessions) {
                VideoResource video = videoResourceMap.get(session.getVideoId());
                if (video == null || video.getDiseaseType() == null || video.getDiseaseType().isBlank()) {
                    continue;
                }
                diseaseDistribution.merge(video.getDiseaseType(), 1L, Long::sum);
            }
        }
        List<Map<String, Object>> diseaseHotspots = diseaseDistribution.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("diseaseType", entry.getKey());
                    item.put("label", displayDisease(entry.getKey()));
                    item.put("count", entry.getValue());
                    return item;
                })
                .limit(6)
                .toList();

        long activeVideos = videoResourceRepository.countByActiveTrue();
        long transcriptReadyVideos = videoResourceRepository.countByActiveTrueAndHasTranscriptTrue();
        long playCount = watchSessions.size();
        long uniqueViewerCount = watchSessions.stream()
                .map(VideoWatchSession::getPatientId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long completedCount = watchSessions.stream()
                .filter(session -> Boolean.TRUE.equals(session.getCompleted()))
                .count();
        double avgWatchSeconds = watchSessions.stream()
                .map(VideoWatchSession::getWatchedSeconds)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0D);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("recommendedVideoCount", videoSnapshots.size());
        summary.put("playCount", playCount);
        summary.put("uniqueViewerCount", uniqueViewerCount);
        summary.put("activeVideoCount", activeVideos);
        summary.put("transcriptCoverage", activeVideos == 0 ? 0D : round((double) transcriptReadyVideos / activeVideos * 100D));
        summary.put("avgWatchSeconds", round(avgWatchSeconds));
        summary.put("completionRate", playCount == 0 ? 0D : round((double) completedCount / playCount * 100D));
        summary.put("completionRateNote", playCount == 0
                ? "当前暂无真实播放数据，完成率将在患者端产生播放行为后更新。"
                : "完成率按观看进度达到 90% 以上计算。");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("topVideos", topVideos);
        result.put("sourceConversion", buildSourceConversion(videoSnapshots, watchSessions));
        result.put("diseaseHotspots", diseaseHotspots);
        return result;
    }

    private List<Map<String, Object>> buildSourceConversion(List<HealthReportVideoSnapshot> videoSnapshots,
                                                            List<VideoWatchSession> watchSessions) {
        List<String> sourceOrder = List.of(
                "report_recommendation",
                "education_recommendation",
                "video_center",
                "doctor_share"
        );
        Map<String, Long> recommendedCountBySource = new LinkedHashMap<>();
        recommendedCountBySource.put("report_recommendation", (long) videoSnapshots.size());
        for (String sourceType : sourceOrder) {
            recommendedCountBySource.putIfAbsent(sourceType, 0L);
        }

        Map<String, List<VideoWatchSession>> grouped = watchSessions.stream()
                .collect(Collectors.groupingBy(session -> normalizeSourceType(session.getSourceType()), LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (String sourceType : sourceOrder) {
            List<VideoWatchSession> sessions = grouped.getOrDefault(sourceType, List.of());
            long completedCount = sessions.stream().filter(session -> Boolean.TRUE.equals(session.getCompleted())).count();
            long uniqueViewerCount = sessions.stream()
                    .map(VideoWatchSession::getPatientId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sourceType", sourceType);
            item.put("label", displaySourceType(sourceType));
            item.put("recommendedCount", recommendedCountBySource.getOrDefault(sourceType, 0L));
            item.put("playCount", (long) sessions.size());
            item.put("completedCount", completedCount);
            item.put("uniqueViewerCount", uniqueViewerCount);
            item.put("completionRate", sessions.isEmpty() ? 0D : round((double) completedCount / sessions.size() * 100D));
            rows.add(item);
        }
        return rows;
    }

    private List<Map<String, Object>> buildTopVideos(Map<String, Long> recommendationCounts,
                                                     Map<String, HealthReportVideoSnapshot> videoExampleMap,
                                                     Map<Long, VideoResource> videoResourceMap,
                                                     Map<Long, List<VideoWatchSession>> watchByVideoId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<Long> handledVideoIds = new LinkedHashSet<>();
        for (Map.Entry<String, HealthReportVideoSnapshot> entry : videoExampleMap.entrySet()) {
            HealthReportVideoSnapshot snapshot = entry.getValue();
            if (snapshot.getVideoId() != null) {
                handledVideoIds.add(snapshot.getVideoId());
            }
            List<VideoWatchSession> sessions = snapshot.getVideoId() == null
                    ? List.of()
                    : watchByVideoId.getOrDefault(snapshot.getVideoId(), List.of());
            long playCount = sessions.size();
            long uniqueViewerCount = sessions.stream()
                    .map(VideoWatchSession::getPatientId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();
            long completedCount = sessions.stream()
                    .filter(session -> Boolean.TRUE.equals(session.getCompleted()))
                    .count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", snapshot.getTitle() == null || snapshot.getTitle().isBlank() ? "未命名视频" : snapshot.getTitle());
            item.put("recommendCount", recommendationCounts.getOrDefault(entry.getKey(), 0L));
            item.put("playCount", playCount);
            item.put("uniqueViewerCount", uniqueViewerCount);
            item.put("completionRate", playCount == 0 ? 0D : round((double) completedCount / playCount * 100D));
            item.put("diseaseType", snapshot.getDiseaseType());
            item.put("category", snapshot.getCategory());
            item.put("durationSeconds", snapshot.getDurationSeconds());
            rows.add(item);
        }

        for (Map.Entry<Long, List<VideoWatchSession>> entry : watchByVideoId.entrySet()) {
            if (entry.getKey() == null || handledVideoIds.contains(entry.getKey())) {
                continue;
            }
            VideoResource video = videoResourceMap.get(entry.getKey());
            if (video == null) {
                continue;
            }
            List<VideoWatchSession> sessions = entry.getValue();
            long playCount = sessions.size();
            long uniqueViewerCount = sessions.stream()
                    .map(VideoWatchSession::getPatientId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();
            long completedCount = sessions.stream()
                    .filter(session -> Boolean.TRUE.equals(session.getCompleted()))
                    .count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", video.getTitle() == null || video.getTitle().isBlank() ? "未命名视频" : video.getTitle());
            item.put("recommendCount", 0L);
            item.put("playCount", playCount);
            item.put("uniqueViewerCount", uniqueViewerCount);
            item.put("completionRate", playCount == 0 ? 0D : round((double) completedCount / playCount * 100D));
            item.put("diseaseType", video.getDiseaseType());
            item.put("category", video.getCategory());
            item.put("durationSeconds", video.getDurationSeconds());
            rows.add(item);
        }
        rows.sort((left, right) -> {
            long playCompare = Long.compare(
                    ((Number) right.getOrDefault("playCount", 0)).longValue(),
                    ((Number) left.getOrDefault("playCount", 0)).longValue()
            );
            if (playCompare != 0) {
                return Long.compare(
                        ((Number) right.getOrDefault("playCount", 0)).longValue(),
                        ((Number) left.getOrDefault("playCount", 0)).longValue()
                );
            }
            return Long.compare(
                    ((Number) right.getOrDefault("recommendCount", 0)).longValue(),
                    ((Number) left.getOrDefault("recommendCount", 0)).longValue()
            );
        });
        return rows.stream().limit(5).toList();
    }

    private Map<Long, RiskAssessment> latestAssessmentByPatient(List<RiskAssessment> assessments) {
        Map<Long, RiskAssessment> result = new LinkedHashMap<>();
        for (RiskAssessment assessment : assessments) {
            result.putIfAbsent(assessment.getPatientId(), assessment);
        }
        return result;
    }

    private Map<Long, FollowUpPlan> nearestFollowUpByPatient(List<FollowUpPlan> plans) {
        Map<Long, FollowUpPlan> result = new LinkedHashMap<>();
        List<FollowUpPlan> sorted = plans.stream()
                .sorted(Comparator.comparing(FollowUpPlan::getNextFollowDate, Comparator.nullsLast(LocalDate::compareTo)))
                .toList();
        for (FollowUpPlan plan : sorted) {
            result.putIfAbsent(plan.getPatientId(), plan);
        }
        return result;
    }

    private Map<String, Object> statusItem(FollowUpStatus status, long value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("status", status.name());
        item.put("label", status.getDisplayName());
        item.put("value", value);
        return item;
    }

    private boolean isHighRisk(RiskAssessment assessment) {
        return assessment != null && (assessment.getRiskLevel() == RiskLevel.HIGH || assessment.getRiskLevel() == RiskLevel.CRITICAL);
    }

    private boolean isOverduePlan(FollowUpPlan plan) {
        if (plan == null || plan.getNextFollowDate() == null) {
            return false;
        }
        if (plan.getStatus() == FollowUpStatus.COMPLETED || plan.getStatus() == FollowUpStatus.CANCELLED) {
            return false;
        }
        return plan.getNextFollowDate().isBefore(LocalDate.now());
    }

    private boolean belongsToDoctor(Notification notification, List<Long> patientIds) {
        if (notification == null) {
            return false;
        }
        if (notification.getPatientId() == null) {
            return true;
        }
        return patientIds.contains(notification.getPatientId());
    }

    private boolean isWithin(LocalDateTime value, LocalDateTime start, LocalDateTime end) {
        return value != null && !value.isBefore(start) && !value.isAfter(end);
    }

    private Map<String, Set<Long>> initDayBucketSet(int days) {
        Map<String, Set<Long>> result = new LinkedHashMap<>();
        for (int offset = days - 1; offset >= 0; offset--) {
            result.put(LocalDate.now().minusDays(offset).format(DATE_KEY), new LinkedHashSet<>());
        }
        return result;
    }

    private Map<String, Integer> initHourBucket(int hours) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int offset = hours - 1; offset >= 0; offset--) {
            result.put(LocalDateTime.now().minusHours(offset).format(HOUR_KEY), 0);
        }
        return result;
    }

    private void incrementHourBucket(Map<String, Integer> bucket, LocalDateTime time) {
        if (time == null) {
            return;
        }
        String key = time.format(HOUR_KEY);
        if (bucket.containsKey(key)) {
            bucket.put(key, bucket.get(key) + 1);
        }
    }

    private int countTools(String traceJson) {
        if (traceJson == null || traceJson.isBlank()) {
            return 0;
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(traceJson, new TypeReference<Map<String, Object>>() {});
            Object usedTools = payload.get("usedTools");
            if (usedTools instanceof Collection<?> collection) {
                return collection.size();
            }
            return 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private boolean isDegradedResponse(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String normalized = content.toLowerCase(Locale.ROOT);
        return normalized.contains("信息有限")
                || normalized.contains("信息不足")
                || normalized.contains("无法进行有效的风险评估")
                || normalized.contains("无法提供针对性的风险分析");
    }

    private Number numberValue(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        return 0L;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && left.equalsIgnoreCase(right);
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    private LocalDateTime max(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private int compareNullableDateTime(LocalDateTime left, LocalDateTime right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    private int compareTime(Object left, Object right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.toString().compareTo(right.toString());
    }

    private String displayDisease(String diseaseType) {
        if (diseaseType == null || diseaseType.isBlank()) {
            return "未知病种";
        }
        return switch (diseaseType.toUpperCase(Locale.ROOT)) {
            case "GENERAL" -> "通用";
            case "DIABETES" -> "2型糖尿病";
            case "HYPERTENSION" -> "高血压";
            case "COPD" -> "慢阻肺";
            case "CKD" -> "慢性肾病";
            case "CHD" -> "冠心病";
            case "STROKE" -> "脑卒中";
            default -> diseaseType;
        };
    }

    private String videoKey(HealthReportVideoSnapshot snapshot) {
        if (snapshot == null) {
            return "unknown";
        }
        if (snapshot.getVideoId() != null) {
            return "video-" + snapshot.getVideoId();
        }
        return Objects.toString(snapshot.getTitle(), "unknown");
    }

    private String normalizeSourceType(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return "video_center";
        }
        return sourceType.trim().toLowerCase(Locale.ROOT);
    }

    private String displaySourceType(String sourceType) {
        return switch (normalizeSourceType(sourceType)) {
            case "report_recommendation" -> "报告推荐";
            case "education_recommendation" -> "宣教推荐";
            case "doctor_share" -> "医生分享";
            case "video_center" -> "视频中心";
            default -> "其他来源";
        };
    }

    private double round(double value) {
        return Math.round(value * 10D) / 10D;
    }

    private static final class PaperCasePatientOpsAccumulator {
        private final Set<Long> recordIds = new LinkedHashSet<>();
        private final Map<Long, PaperCaseRecordFlags> recordFlags = new LinkedHashMap<>();
        private long totalCount;
        private long pendingReviewCount;
        private long failedSyncCount;
        private LocalDateTime latestCreatedAt;

        void applyLatestJobs(Map<Long, ClinicalDataJob> latestJobsByRecordId) {
            for (Long recordId : recordIds) {
                ClinicalDataJob job = latestJobsByRecordId.get(recordId);
                if (job == null) {
                    continue;
                }
                PaperCaseRecordFlags flags = recordFlags.computeIfAbsent(recordId, ignored -> new PaperCaseRecordFlags());
                if (ClinicalDataJobService.STATUS_REVIEW_PENDING.equalsIgnoreCase(job.getJobStatus())
                        || ClinicalDataJobService.STATUS_FAILED.equalsIgnoreCase(job.getJobStatus())
                        || ClinicalDataJobService.STATUS_DEAD_LETTER.equalsIgnoreCase(job.getJobStatus())) {
                    flags.attention = true;
                }
            }
        }

        long attentionRecordCount() {
            return recordFlags.values().stream().filter(flags -> flags.attention).count();
        }

        boolean hasAttention() {
            return attentionRecordCount() > 0;
        }

        String latestRecordDateText() {
            return recordFlags.values().stream()
                    .map(flags -> flags.recordDate)
                    .filter(Objects::nonNull)
                    .max(LocalDate::compareTo)
                    .map(LocalDate::toString)
                    .orElse(null);
        }

        String latestDiagnosis() {
            return recordFlags.values().stream()
                    .map(flags -> flags.diagnosis)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse(null);
        }

        String latestFileName() {
            return recordFlags.values().stream()
                    .map(flags -> flags.fileName)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse(null);
        }
    }

    private static final class PaperCaseRecordFlags {
        private boolean attention;
        private boolean pendingReview;
        private boolean failedSync;
        private LocalDate recordDate;
        private String diagnosis;
        private String fileName;
    }
}
