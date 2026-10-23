package com.medical.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medical.agent.common.dto.HealthRecordDTO;
import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.model.entity.MedplumSyncCheckpoint;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.repository.HealthRecordRepository;
import com.medical.agent.model.repository.MedplumSyncCheckpointRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.fhir.FhirCodingRegistry;
import com.medical.agent.service.cache.CacheNames;
import com.medical.agent.service.security.PrivacyLogSanitizer;
import jakarta.annotation.PostConstruct;
import okhttp3.Credentials;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class MedplumService {

    private static final Logger log = LoggerFactory.getLogger(MedplumService.class);
    private static final int MAX_OBSERVATION_QUERY_LIMIT = 200;
    private static final MediaType FHIR_JSON_TYPE = MediaType.get("application/fhir+json; charset=utf-8");
    private static final String PATIENT_IDENTIFIER_SYSTEM = "https://cdm.local/fhir/identifier/patient-id";
    private static final String PATIENT_FHIR_IDENTIFIER_SYSTEM = "https://cdm.local/fhir/identifier/patient-fhir-id";
    private static final String PATIENT_USERNAME_IDENTIFIER_SYSTEM = "https://cdm.local/fhir/identifier/patient-username";
    private static final String OBSERVATION_IDENTIFIER_SYSTEM = "https://cdm.local/fhir/identifier/observation-id";
    private static final String ENCOUNTER_IDENTIFIER_SYSTEM = "https://cdm.local/fhir/identifier/paper-case-id";
    private static final String CONDITION_IDENTIFIER_SYSTEM = "https://cdm.local/fhir/identifier/paper-case-condition";
    private static final String DOCUMENT_REFERENCE_IDENTIFIER_SYSTEM = "https://cdm.local/fhir/identifier/paper-case-document";
    private static final String EXT_BASE = "https://cdm.local/fhir/StructureDefinition/";
    private static final String EXT_EMERGENCY_CONTACT = EXT_BASE + "emergency-contact";
    private static final String EXT_EMERGENCY_PHONE = EXT_BASE + "emergency-phone";
    private static final String EXT_ASSIGNED_DOCTOR_ID = EXT_BASE + "assigned-doctor-id";
    private static final String EXT_PROFILE_COMPLETED = EXT_BASE + "profile-completed";
    private static final String EXT_MEDICAL_HISTORY_SUMMARY = EXT_BASE + "medical-history-summary";
    private static final String CLOUD_ONLY_OBSERVATION_SYSTEM = "https://cdm.local/fhir/identifier/cloud-only-observation";
    private static final String SYNC_SCOPE_PATIENT = "patient";
    private static final String SYNC_SCOPE_OBSERVATION = "observation";
    private static final String SYNC_SCOPE_PAPER_CASE = "paper_case";
    private static final int MAX_TRANSACTION_BUNDLE_SIZE = 50;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FhirService fhirService;
    private final PatientRepository patientRepository;
    private final HealthRecordRepository healthRecordRepository;
    private final MedplumSyncCheckpointRepository medplumSyncCheckpointRepository;
    private final FhirCodingRegistry codingRegistry;
    private final PrivacyLogSanitizer sanitizer = new PrivacyLogSanitizer();
    private OkHttpClient httpClient;

    @Value("${medplum.enabled:false}")
    private boolean enabled;

    @Value("${medplum.base-url:https://api.medplum.com/}")
    private String baseUrl;

    @Value("${medplum.client-id:}")
    private String clientId;

    @Value("${medplum.client-secret:}")
    private String clientSecret;

    @Value("${medplum.project-id:}")
    private String projectId;

    @Value("${medplum.access-token:}")
    private String accessToken;

    @Value("${medplum.node-command:node}")
    private String nodeCommand;

    @Value("${medplum.bridge-script-path:}")
    private String bridgeScriptPath;

    @Value("${medplum.mcp-base-url:}")
    private String mcpBaseUrl;

    @Value("${medplum.timeout-seconds:45}")
    private long timeoutSeconds;

    @Value("${medplum.transaction-bundle-enabled:true}")
    private boolean transactionBundleEnabled;

    @Value("${medplum.incremental-sync-enabled:true}")
    private boolean incrementalSyncEnabled;

    @Value("${medplum.transaction-bundle-fallback-enabled:true}")
    private boolean transactionBundleFallbackEnabled;

    private volatile String cachedAccessToken;
    private volatile Instant cachedAccessTokenExpiresAt;
    private volatile String lastError;

    public MedplumService(FhirService fhirService,
                          PatientRepository patientRepository,
                          HealthRecordRepository healthRecordRepository,
                          MedplumSyncCheckpointRepository medplumSyncCheckpointRepository) {
        this(fhirService, patientRepository, healthRecordRepository, medplumSyncCheckpointRepository, new FhirCodingRegistry());
    }

    @Autowired
    public MedplumService(FhirService fhirService,
                          PatientRepository patientRepository,
                          HealthRecordRepository healthRecordRepository,
                          MedplumSyncCheckpointRepository medplumSyncCheckpointRepository,
                          FhirCodingRegistry codingRegistry) {
        this.fhirService = fhirService;
        this.patientRepository = patientRepository;
        this.healthRecordRepository = healthRecordRepository;
        this.medplumSyncCheckpointRepository = medplumSyncCheckpointRepository;
        this.codingRegistry = codingRegistry;
    }

    @PostConstruct
    public void init() {
        long effectiveTimeout = timeoutSeconds > 0 ? timeoutSeconds : 45;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Math.min(effectiveTimeout, 20), TimeUnit.SECONDS)
                .readTimeout(effectiveTimeout, TimeUnit.SECONDS)
                .writeTimeout(effectiveTimeout, TimeUnit.SECONDS)
                .build();
        log.info("[MedplumService] Config loaded: enabled={}, baseUrl={}, hasClientId={}, hasClientSecret={}, hasAccessToken={}, bridgeScriptExists={}, mcpBaseUrl={}",
                enabled,
                normalizeBaseUrl(),
                hasText(clientId),
                hasText(clientSecret),
                hasText(accessToken),
                bridgeScriptExists(),
                normalizeMcpBaseUrl());
    }

    @Cacheable(cacheNames = CacheNames.MEDPLUM_STATUS, key = "#a0", sync = true)
    public Map<String, Object> getStatus(boolean probeRemote) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", enabled);
        status.put("transport", "direct-fhir");
        status.put("baseUrl", normalizeBaseUrl());
        status.put("configured", isConfigured());
        status.put("clientIdConfigured", hasText(clientId));
        status.put("clientSecretConfigured", hasText(clientSecret));
        status.put("projectIdConfigured", hasText(projectId));
        status.put("accessTokenConfigured", hasText(accessToken));
        status.put("nodeCommand", nodeCommand);
        status.put("bridgeScriptPath", bridgeScriptPath);
        status.put("bridgeScriptExists", bridgeScriptExists());
        status.put("mcpBaseUrl", normalizeMcpBaseUrl());
        status.put("mcpConfigured", hasText(normalizeMcpBaseUrl()));
        status.put("localPatientCount", patientRepository.count());
        status.put("localHealthRecordCount", healthRecordRepository.count());
        status.put("lastError", lastError);

        if (!probeRemote) {
            status.put("authenticated", false);
            status.put("mcpReachable", false);
            status.put("mcpMessage", hasText(normalizeMcpBaseUrl()) ? "Medplum MCP bridge 已配置，未探测。" : "Medplum MCP bridge 未配置。");
            status.put("message", buildStatusMessage(isConfigured(), false));
            return status;
        }

        if (!isConfigured()) {
            status.put("authenticated", false);
            status.put("mcpReachable", probeMcpBridgeSafely(status));
            status.put("message", buildStatusMessage(false, false));
            return status;
        }

        try {
            boolean authenticated = probeAuthentication();
            status.put("authenticated", authenticated);
            status.put("message", buildStatusMessage(true, authenticated));
        } catch (RuntimeException e) {
            status.put("authenticated", false);
            status.put("message", e.getMessage());
        }
        status.put("mcpReachable", probeMcpBridgeSafely(status));
        return status;
    }

    public boolean isAutoSyncReady() {
        return enabled && isConfigured();
    }

    @CacheEvict(cacheNames = {
            CacheNames.MEDPLUM_STATUS,
            CacheNames.PATIENT_INTEGRATION_OVERVIEW,
            CacheNames.PATIENT_INTEGRATION_STATUS
    }, allEntries = true)
    public Map<String, Object> syncPatient(Long patientId, boolean includeObservations) {
        if (!enabled) {
            throw new BusinessException(400, "Medplum 集成未启用，请先将 MEDPLUM_ENABLED 设为 true。");
        }
        if (!isConfigured()) {
            throw new BusinessException(400, "Medplum 凭证未配置完整，请补充 MEDPLUM_ACCESS_TOKEN 或 MEDPLUM_CLIENT_ID / MEDPLUM_CLIENT_SECRET。");
        }

        String token = resolveAccessToken();
        return syncPatientInternal(token, patientId, includeObservations);
    }

    @CacheEvict(cacheNames = {
            CacheNames.MEDPLUM_STATUS,
            CacheNames.PATIENT_INTEGRATION_OVERVIEW,
            CacheNames.PATIENT_INTEGRATION_STATUS
    }, allEntries = true)
    public Map<String, Object> syncAllPatients(boolean includeObservations, boolean continueOnError) {
        if (!enabled) {
            throw new BusinessException(400, "Medplum 集成未启用，请先将 MEDPLUM_ENABLED 设为 true。");
        }
        if (!isConfigured()) {
            throw new BusinessException(400, "Medplum 凭证未配置完整，请补充 MEDPLUM_ACCESS_TOKEN 或 MEDPLUM_CLIENT_ID / MEDPLUM_CLIENT_SECRET。");
        }

        String token = resolveAccessToken();
        Map<String, Object> result = new LinkedHashMap<>();
        java.util.List<Map<String, Object>> successes = new java.util.ArrayList<>();
        java.util.List<Map<String, Object>> failures = new java.util.ArrayList<>();

        for (Patient patient : patientRepository.findAll()) {
            try {
                successes.add(syncPatientInternal(token, patient.getId(), includeObservations));
            } catch (RuntimeException e) {
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("patientId", patient.getId());
                failure.put("name", patient.getName());
                failure.put("message", e.getMessage());
                failures.add(failure);
                if (!continueOnError) {
                    break;
                }
            }
        }

        result.put("success", failures.isEmpty());
        result.put("transport", "direct-fhir");
        result.put("baseUrl", normalizeBaseUrl());
        result.put("totalPatients", patientRepository.count());
        result.put("syncedPatients", successes.size());
        result.put("failedPatients", failures.size());
        result.put("includeObservations", includeObservations);
        result.put("continueOnError", continueOnError);
        result.put("patients", successes);
        result.put("failures", failures);
        result.put("message", failures.isEmpty()
                ? "本地患者已全部同步到 Medplum Cloud。"
                : "部分患者同步失败，请查看 failures。");
        return result;
    }

    public Map<String, Object> getPatientByLocalId(Long localPatientId) {
        if (!enabled || !isConfigured()) {
            return Map.of(
                    "available", false,
                    "found", false,
                    "message", "Medplum 未启用或未配置完整。"
            );
        }
        if (localPatientId == null) {
            return Map.of(
                    "available", true,
                    "found", false,
                    "message", "未提供本地患者 ID。"
            );
        }

        Patient localPatient = patientRepository.findById(localPatientId).orElse(null);
        if (localPatient == null) {
            return Map.of(
                    "available", true,
                    "found", false,
                    "localPatientId", localPatientId,
                    "message", "本地患者不存在。"
            );
        }
        String token = resolveAccessToken();
        ObjectNode patient = findCloudPatient(token, localPatient);
        if (patient == null) {
            return Map.of(
                    "available", true,
                    "found", false,
                    "localPatientId", localPatientId,
                    "message", "Medplum 中未找到对应患者。"
            );
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", true);
        result.put("found", true);
        result.put("localPatientId", localPatientId);
        result.put("medplumPatientId", patient.path("id").asText(""));
        result.put("name", summarizeHumanName(patient));
        result.put("gender", patient.path("gender").asText(""));
        result.put("birthDate", patient.path("birthDate").asText(""));
        result.put("emergencyContact", extractExtensionString(patient, EXT_EMERGENCY_CONTACT));
        result.put("emergencyPhone", extractExtensionString(patient, EXT_EMERGENCY_PHONE));
        result.put("assignedDoctorId", extractExtensionString(patient, EXT_ASSIGNED_DOCTOR_ID));
        result.put("profileCompleted", extractExtensionBoolean(patient, EXT_PROFILE_COMPLETED));
        result.put("medicalHistorySummary", extractExtensionString(patient, EXT_MEDICAL_HISTORY_SUMMARY));
        result.put("resource", patient);
        result.put("message", "已从 Medplum Cloud 读取患者主档。");
        return result;
    }

    public Map<String, Object> getLatestObservationsForLocalPatient(Long localPatientId, int limit) {
        if (!enabled || !isConfigured()) {
            return Map.of(
                    "available", false,
                    "found", false,
                    "message", "Medplum 未启用或未配置完整。"
            );
        }
        if (localPatientId == null) {
            return Map.of(
                    "available", true,
                    "found", false,
                    "message", "未提供本地患者 ID。"
            );
        }

        int safeLimit = Math.min(Math.max(limit, 1), MAX_OBSERVATION_QUERY_LIMIT);
        String token = resolveAccessToken();
        Patient localPatient = patientRepository.findById(localPatientId).orElse(null);
        if (localPatient == null) {
            return Map.of(
                    "available", true,
                    "found", false,
                    "localPatientId", localPatientId,
                    "message", "本地患者不存在。"
            );
        }
        ObjectNode patient = findCloudPatient(token, localPatient);
        if (patient == null || !patient.path("id").isTextual()) {
            return Map.of(
                    "available", true,
                    "found", false,
                    "localPatientId", localPatientId,
                    "message", "Medplum 中未找到对应患者，无法读取 Observation。"
            );
        }

        String patientId = patient.path("id").asText();
        String query = "Observation?subject=" + encode("Patient/" + patientId) + "&_sort=-date&_count=" + safeLimit;
        JsonNode bundle = fetchBundle(token, query, "Observation");

        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode entry : bundle.path("entry")) {
            JsonNode resource = entry.path("resource");
            if (!resource.isObject()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", resource.path("id").asText(""));
            item.put("effectiveDateTime", resource.path("effectiveDateTime").asText(""));
            item.put("code", extractObservationCode(resource));
            item.put("value", extractObservationValue(resource));
            items.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", true);
        result.put("found", true);
        result.put("localPatientId", localPatientId);
        result.put("medplumPatientId", patientId);
        result.put("count", items.size());
        result.put("observations", items);
        result.put("message", items.isEmpty() ? "Medplum 中暂未找到 Observation。" : "已读取 Medplum 最新 Observation。");
        return result;
    }

    @CacheEvict(cacheNames = {
            CacheNames.MEDPLUM_STATUS,
            CacheNames.PATIENT_INTEGRATION_OVERVIEW,
            CacheNames.PATIENT_INTEGRATION_STATUS
    }, allEntries = true)
    public Map<String, Object> auditAndRepairPatientMappings(boolean continueOnError, int maxPatients) {
        if (!enabled) {
            throw new BusinessException(400, "Medplum 集成未启用，请先将 MEDPLUM_ENABLED 设为 true。");
        }
        if (!isConfigured()) {
            throw new BusinessException(400, "Medplum 凭证未配置完整，请补充 MEDPLUM_ACCESS_TOKEN 或 MEDPLUM_CLIENT_ID / MEDPLUM_CLIENT_SECRET。");
        }

        String token = resolveAccessToken();
        List<Patient> localPatients = patientRepository.findAll();
        int patientLimit = maxPatients > 0 ? Math.min(maxPatients, localPatients.size()) : localPatients.size();

        List<Map<String, Object>> items = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        int repairedCount = 0;

        for (int i = 0; i < patientLimit; i++) {
            Patient localPatient = localPatients.get(i);
            try {
                Map<String, Object> item = auditAndRepairSinglePatientMapping(token, localPatient);
                items.add(item);
                if (Boolean.TRUE.equals(item.get("repaired"))) {
                    repairedCount++;
                }
            } catch (RuntimeException ex) {
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("patientId", localPatient.getId());
                failure.put("name", localPatient.getName());
                failure.put("username", localPatient.getUsername());
                failure.put("message", ex.getMessage());
                failures.add(failure);
                if (!continueOnError) {
                    break;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", failures.isEmpty());
        result.put("inspectedPatients", items.size() + failures.size());
        result.put("repairedPatients", repairedCount);
        result.put("failedPatients", failures.size());
        result.put("continueOnError", continueOnError);
        result.put("maxPatients", maxPatients);
        result.put("patients", items);
        result.put("failures", failures);
        result.put("message", repairedCount > 0
                ? "Medplum 患者映射巡检完成，已自动修复部分重复映射。"
                : "Medplum 患者映射巡检完成，未发现需要修复的重复映射。");
        return result;
    }

    public Map<String, Object> getPaperCaseResourcesForLocalPatient(Long localPatientId, int limit) {
        if (!enabled || !isConfigured()) {
            return Map.of(
                    "available", false,
                    "found", false,
                    "message", "Medplum 未启用或未配置完整。"
            );
        }
        if (localPatientId == null) {
            return Map.of(
                    "available", true,
                    "found", false,
                    "message", "未提供本地患者 ID。"
            );
        }

        int safeLimit = Math.min(Math.max(limit, 1), MAX_OBSERVATION_QUERY_LIMIT);
        String token = resolveAccessToken();
        Patient localPatient = patientRepository.findById(localPatientId).orElse(null);
        if (localPatient == null) {
            return Map.of(
                    "available", true,
                    "found", false,
                    "localPatientId", localPatientId,
                    "message", "本地患者不存在。"
            );
        }
        ObjectNode patient = findCloudPatient(token, localPatient);
        if (patient == null || !patient.path("id").isTextual()) {
            return Map.of(
                    "available", true,
                    "found", false,
                    "localPatientId", localPatientId,
                    "message", "Medplum 中未找到对应患者，无法读取纸质病历资源。"
            );
        }

        String patientId = patient.path("id").asText();
        List<Map<String, Object>> encounters = summarizeResources(fetchBundle(
                token,
                "Encounter?subject=" + encode("Patient/" + patientId) + "&_sort=-date&_count=" + safeLimit,
                "Encounter"
        ), "Encounter");
        List<Map<String, Object>> conditions = summarizeResources(fetchBundle(
                token,
                "Condition?subject=" + encode("Patient/" + patientId) + "&_count=" + safeLimit,
                "Condition"
        ), "Condition");
        List<Map<String, Object>> documentReferences = summarizeResources(fetchBundle(
                token,
                "DocumentReference?subject=" + encode("Patient/" + patientId) + "&_count=" + safeLimit,
                "DocumentReference"
        ), "DocumentReference");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", true);
        result.put("found", true);
        result.put("localPatientId", localPatientId);
        result.put("medplumPatientId", patientId);
        result.put("encounters", encounters);
        result.put("conditions", conditions);
        result.put("documentReferences", documentReferences);
        result.put("message", "已读取 Medplum 纸质病历相关资源。");
        return result;
    }

    @CacheEvict(cacheNames = {
            CacheNames.MEDPLUM_STATUS,
            CacheNames.PATIENT_INTEGRATION_OVERVIEW,
            CacheNames.PATIENT_INTEGRATION_STATUS
    }, allEntries = true)
    public Map<String, Object> importCloudOnlyObservations(HealthRecordDTO dto, boolean ensurePatientSynced) {
        if (!enabled || !isConfigured()) {
            throw new BusinessException(400, "Medplum 未启用或未配置完整。");
        }
        if (dto == null || dto.getPatientId() == null) {
            throw new BusinessException(400, "patientId 不能为空");
        }

        if (ensurePatientSynced) {
            syncPatient(dto.getPatientId(), false);
        }

        String token = resolveAccessToken();
        Patient localPatient = requireLocalPatient(dto.getPatientId());
        ObjectNode patient = findCloudPatient(token, localPatient);
        if (patient == null || !patient.path("id").isTextual()) {
            throw new BusinessException(404, "Medplum 中未找到对应患者。");
        }

        String patientFhirId = patient.path("id").asText();
        String patientRef = "Patient/" + patientFhirId;
        String suffix = String.valueOf(Instant.now().toEpochMilli());
        String effectiveDate = resolveCloudOnlyEffectiveDate(dto);

        List<ObjectNode> observations = buildCloudOnlyObservations(dto, patientRef, effectiveDate, suffix);
        int imported = 0;
        for (ObjectNode observation : observations) {
            String identifierValue = observation.path("identifier").get(0).path("value").asText();
            upsertResourceByIdentifier(token, "Observation", CLOUD_ONLY_OBSERVATION_SYSTEM, identifierValue, observation);
            imported++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("patientId", dto.getPatientId());
        result.put("patientFhirId", patientFhirId);
        result.put("observationsSynced", imported);
        result.put("includeObservations", true);
        result.put("cloudOnly", true);
        result.put("message", "随机健康数据已仅写入 Medplum Cloud。");
        return result;
    }

    private String resolveCloudOnlyEffectiveDate(HealthRecordDTO dto) {
        if (dto != null && dto.getRecordedAt() != null) {
            return dto.getRecordedAt().toString();
        }
        return (dto != null && dto.getRecordDate() != null ? dto.getRecordDate() : LocalDate.now()) + "T08:00:00+08:00";
    }

    private Map<String, Object> syncPatientInternal(String token, Long patientId, boolean includeObservations) {
        try {
            Patient localPatient = requireLocalPatient(patientId);
            auditAndRepairSinglePatientMapping(token, localPatient);
            ObjectNode patientResource = (ObjectNode) objectMapper.readTree(fhirService.toFhirPatientJson(patientId));
            SyncPayload patientPayload = createSyncPayload(List.of(patientResource), PATIENT_IDENTIFIER_SYSTEM);
            String patientFhirId = syncPatientResource(token, patientId, patientPayload);

            int observationsSynced = 0;
            int observationsSkipped = 0;
            if (includeObservations) {
                JsonNode observationBundle = objectMapper.readTree(fhirService.toFhirObservationsJson(patientId));
                List<ObjectNode> observationResources = new ArrayList<>();
                for (JsonNode entry : observationBundle.path("entry")) {
                    JsonNode resource = entry.path("resource");
                    if (!"Observation".equals(resource.path("resourceType").asText())) {
                        continue;
                    }
                    ObjectNode observationResource = (ObjectNode) resource.deepCopy();
                    rewriteObservationSubject(observationResource, patientFhirId);
                    observationResources.add(observationResource);
                }
                SyncResult observationResult = syncResourceGroup(token, patientId, SYNC_SCOPE_OBSERVATION, observationResources, OBSERVATION_IDENTIFIER_SYSTEM);
                observationsSynced = observationResult.syncedCount();
                observationsSkipped = observationResult.skippedCount();
            }

            SyncResult paperCaseSync = syncPaperCaseResources(token, patientFhirId, patientId);

            lastError = null;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("transport", "direct-fhir");
            result.put("baseUrl", normalizeBaseUrl());
            result.put("patientId", patientId);
            result.put("patientFhirId", patientFhirId);
            result.put("observationsSynced", observationsSynced);
            result.put("observationsSkipped", observationsSkipped);
            result.put("paperCaseResourcesSynced", paperCaseSync.syncedCount());
            result.put("paperCaseResourcesSkipped", paperCaseSync.skippedCount());
            result.put("includeObservations", includeObservations);
            result.put("message", "患者 FHIR 资源与纸质病历资源已同步到 Medplum Cloud。");
            return result;
        } catch (IOException e) {
            lastError = "Medplum sync parse error: " + e.getMessage();
            throw new BusinessException(500, "FHIR 资源解析失败: " + e.getMessage());
        }
    }

    private List<ObjectNode> buildCloudOnlyObservations(HealthRecordDTO dto,
                                                        String patientRef,
                                                        String effectiveDate,
                                                        String suffix) {
        List<ObjectNode> observations = new ArrayList<>();
        if (dto.getSystolicBp() != null) {
            observations.add(createQuantityObservation(patientRef, "blood-pressure-systolic", dto.getSystolicBp(), effectiveDate, "systolic-bp", suffix));
        }
        if (dto.getDiastolicBp() != null) {
            observations.add(createQuantityObservation(patientRef, "blood-pressure-diastolic", dto.getDiastolicBp(), effectiveDate, "diastolic-bp", suffix));
        }
        addConfiguredCloudOnlyObservations(observations, new BeanWrapperImpl(dto), patientRef, effectiveDate, suffix);
        return observations;
    }

    private void addConfiguredCloudOnlyObservations(List<ObjectNode> observations,
                                                    BeanWrapper wrapper,
                                                    String patientRef,
                                                    String effectiveDate,
                                                    String suffix) {
        for (FhirCodingRegistry.ObservationFieldMapping mapping : codingRegistry.healthRecordObservationMappings()) {
            if (!wrapper.isReadableProperty(mapping.field())) {
                throw new IllegalStateException("health record observation mapping references unreadable DTO property: " + mapping.field());
            }
            Object value = wrapper.getPropertyValue(mapping.field());
            if (value == null) {
                continue;
            }
            switch (mapping.valueType()) {
                case QUANTITY -> {
                    if (value instanceof Number number) {
                        observations.add(createQuantityObservation(patientRef, mapping.observationKey(), number, effectiveDate, mapping.observationKey(), suffix));
                    } else {
                        throw new IllegalStateException("expected numeric DTO value for mapped property: " + mapping.field());
                    }
                }
                case BOOLEAN -> {
                    if (value instanceof Boolean bool) {
                        observations.add(createBooleanObservation(patientRef, mapping.observationKey(), bool, effectiveDate, mapping.observationKey(), suffix));
                    } else {
                        throw new IllegalStateException("expected boolean DTO value for mapped property: " + mapping.field());
                    }
                }
                case STRING -> {
                    String text = value.toString().trim();
                    if (!text.isBlank()) {
                        observations.add(createStringObservation(patientRef, mapping.observationKey(), text, effectiveDate, mapping.observationKey(), suffix));
                    }
                }
            }
        }
    }

    private ObjectNode createQuantityObservation(String patientRef,
                                                 String observationKey,
                                                 Number value,
                                                 String effectiveDate,
                                                 String metricKey,
                                                 String suffix) {
        FhirCodingRegistry.ObservationDefinition definition = codingRegistry.requireObservation(observationKey);
        ObjectNode observation = createBaseObservation(patientRef, definition, effectiveDate, metricKey, suffix);
        ObjectNode valueQuantity = objectMapper.createObjectNode();
        valueQuantity.put("value", value.doubleValue());
        if (definition.unit() != null && !definition.unit().isBlank()) {
            valueQuantity.put("unit", definition.unit());
            valueQuantity.put("system", "http://unitsofmeasure.org");
        }
        observation.set("valueQuantity", valueQuantity);
        return observation;
    }

    private ObjectNode createBooleanObservation(String patientRef,
                                                String observationKey,
                                                Boolean value,
                                                String effectiveDate,
                                                String metricKey,
                                                String suffix) {
        ObjectNode observation = createBaseObservation(patientRef, codingRegistry.requireObservation(observationKey), effectiveDate, metricKey, suffix);
        observation.put("valueBoolean", Boolean.TRUE.equals(value));
        return observation;
    }

    private ObjectNode createStringObservation(String patientRef,
                                               String observationKey,
                                               String value,
                                               String effectiveDate,
                                               String metricKey,
                                               String suffix) {
        ObjectNode observation = createBaseObservation(patientRef, codingRegistry.requireObservation(observationKey), effectiveDate, metricKey, suffix);
        observation.put("valueString", value);
        return observation;
    }

    private ObjectNode createBaseObservation(String patientRef,
                                             FhirCodingRegistry.ObservationDefinition definition,
                                             String effectiveDate,
                                             String metricKey,
                                             String suffix) {
        ObjectNode observation = objectMapper.createObjectNode();
        observation.put("resourceType", "Observation");
        observation.put("status", "final");
        observation.put("effectiveDateTime", effectiveDate);

        ArrayNode identifiers = objectMapper.createArrayNode();
        ObjectNode identifier = objectMapper.createObjectNode();
        identifier.put("system", CLOUD_ONLY_OBSERVATION_SYSTEM);
        identifier.put("value", "cloud-only-" + metricKey + "-" + suffix);
        identifiers.add(identifier);
        observation.set("identifier", identifiers);

        ObjectNode subject = objectMapper.createObjectNode();
        subject.put("reference", patientRef);
        observation.set("subject", subject);

        ObjectNode code = objectMapper.createObjectNode();
        ArrayNode coding = objectMapper.createArrayNode();
        ObjectNode codingItem = objectMapper.createObjectNode();
        codingItem.put("system", definition.system());
        codingItem.put("code", definition.code());
        codingItem.put("display", definition.display());
        coding.add(codingItem);
        code.set("coding", coding);
        code.put("text", definition.display());
        observation.set("code", code);
        return observation;
    }

    private String upsertPatient(String token, Long patientId, ObjectNode patientResource) {
        String stableIdentifier = extractIdentifierValue(patientResource, PATIENT_FHIR_IDENTIFIER_SYSTEM);
        if (hasText(stableIdentifier)) {
            return upsertResourceByIdentifier(
                    token,
                    "Patient",
                    PATIENT_FHIR_IDENTIFIER_SYSTEM,
                    stableIdentifier,
                    patientResource
                );
        }
        String usernameIdentifier = extractIdentifierValue(patientResource, PATIENT_USERNAME_IDENTIFIER_SYSTEM);
        if (hasText(usernameIdentifier)) {
            ObjectNode existingByUsername = findFirstResourceByIdentifier(
                    token,
                    "Patient",
                    PATIENT_USERNAME_IDENTIFIER_SYSTEM,
                    usernameIdentifier
            );
            if (existingByUsername != null && existingByUsername.path("id").isTextual()) {
                patientResource.remove("id");
                patientResource.put("id", existingByUsername.path("id").asText());
                return putResource(token, "Patient", existingByUsername.path("id").asText(), patientResource);
            }
        }
        return upsertResourceByIdentifier(
                token,
                "Patient",
                PATIENT_IDENTIFIER_SYSTEM,
                String.valueOf(patientId),
                patientResource
        );
    }

    private String upsertObservation(String token, String localObservationId, ObjectNode observationResource) {
        return upsertResourceByIdentifier(
                token,
                "Observation",
                OBSERVATION_IDENTIFIER_SYSTEM,
                localObservationId,
                observationResource
        );
    }

    private String upsertEncounter(String token, String localPaperCaseId, ObjectNode encounterResource) {
        return upsertResourceByIdentifier(
                token,
                "Encounter",
                ENCOUNTER_IDENTIFIER_SYSTEM,
                localPaperCaseId,
                encounterResource
        );
    }

    private String upsertCondition(String token, String localPaperCaseId, ObjectNode conditionResource) {
        return upsertResourceByIdentifier(
                token,
                "Condition",
                CONDITION_IDENTIFIER_SYSTEM,
                localPaperCaseId,
                conditionResource
        );
    }

    private String upsertDocumentReference(String token, String localPaperCaseId, ObjectNode documentReferenceResource) {
        return upsertResourceByIdentifier(
                token,
                "DocumentReference",
                DOCUMENT_REFERENCE_IDENTIFIER_SYSTEM,
                localPaperCaseId,
                documentReferenceResource
        );
    }

    private String upsertResourceByIdentifier(String token,
                                              String resourceType,
                                              String identifierSystem,
                                              String identifierValue,
                                              ObjectNode resource) {
        ensureIdentifier(resource, identifierSystem, identifierValue);
        String existingId = findResourceIdByIdentifier(token, resourceType, identifierSystem, identifierValue);
        resource.remove("id");

        if (existingId != null && !existingId.isBlank()) {
            resource.put("id", existingId);
            return putResource(token, resourceType, existingId, resource);
        }
        return postResource(token, resourceType, resource);
    }

    private ObjectNode findPatientByLocalId(String token, Long localPatientId) {
        JsonNode bundle = fetchBundle(
                token,
                "Patient?identifier=" + encode(PATIENT_IDENTIFIER_SYSTEM + "|" + localPatientId) + "&_count=1",
                "Patient"
        );
        return firstPatientFromBundle(bundle);
    }

    private List<ObjectNode> findPatientsByIdentifier(String token, String identifierSystem, String identifierValue) {
        if (!hasText(identifierSystem) || !hasText(identifierValue)) {
            return List.of();
        }
        JsonNode bundle = fetchBundle(
                token,
                "Patient?identifier=" + encode(identifierSystem + "|" + identifierValue.trim()) + "&_count=20",
                "Patient"
        );
        List<ObjectNode> patients = new ArrayList<>();
        JsonNode entries = bundle.path("entry");
        if (!entries.isArray()) {
            return patients;
        }
        for (JsonNode entry : entries) {
            JsonNode resource = entry.path("resource");
            if (resource instanceof ObjectNode objectNode) {
                patients.add(objectNode);
            }
        }
        return patients;
    }

    private Patient requireLocalPatient(Long localPatientId) {
        return patientRepository.findById(localPatientId)
                .orElseThrow(() -> new BusinessException(404, "本地患者不存在: " + localPatientId));
    }

    private ObjectNode findCloudPatient(String token, Patient localPatient) {
        if (localPatient == null) {
            return null;
        }

        List<ObjectNode> candidates = new ArrayList<>();
        String stableFhirId = normalizeStablePatientKey(localPatient.getFhirId());
        if (hasText(stableFhirId)) {
            addUniquePatients(candidates, findPatientsByIdentifier(token, PATIENT_FHIR_IDENTIFIER_SYSTEM, stableFhirId));
            ObjectNode byResourceId = findPatientByResourceId(token, stableFhirId);
            if (byResourceId != null) {
                addUniquePatients(candidates, List.of(byResourceId));
            }
        }

        if (hasText(localPatient.getUsername())) {
            addUniquePatients(candidates, findPatientsByIdentifier(token, PATIENT_USERNAME_IDENTIFIER_SYSTEM, localPatient.getUsername().trim()));
        }

        addUniquePatients(candidates, findPatientsByIdentifier(token, PATIENT_IDENTIFIER_SYSTEM, String.valueOf(localPatient.getId())));
        return chooseCanonicalPatient(candidates, localPatient);
    }

    private Map<String, Object> auditAndRepairSinglePatientMapping(String token, Patient localPatient) {
        if (localPatient == null || localPatient.getId() == null) {
            return Map.of(
                    "patientId", null,
                    "repaired", false,
                    "duplicateCount", 0,
                    "message", "本地患者为空，已跳过。"
            );
        }

        String stableFhirId = normalizeStablePatientKey(localPatient.getFhirId());
        String username = hasText(localPatient.getUsername()) ? localPatient.getUsername().trim() : null;
        String localId = String.valueOf(localPatient.getId());

        List<ObjectNode> candidates = new ArrayList<>();
        addUniquePatients(candidates, findPatientsByIdentifier(token, PATIENT_FHIR_IDENTIFIER_SYSTEM, stableFhirId));
        addUniquePatients(candidates, findPatientsByIdentifier(token, PATIENT_USERNAME_IDENTIFIER_SYSTEM, username));
        addUniquePatients(candidates, findPatientsByIdentifier(token, PATIENT_IDENTIFIER_SYSTEM, localId));

        ObjectNode canonical = chooseCanonicalPatient(candidates, localPatient);
        boolean repaired = false;
        int duplicateCount = Math.max(candidates.size() - (canonical == null ? 0 : 1), 0);

        if (canonical != null) {
            for (ObjectNode candidate : candidates) {
                if (candidate == null || candidate.path("id").asText("").isBlank()) {
                    continue;
                }
                if (candidate.path("id").asText().equals(canonical.path("id").asText())) {
                    continue;
                }
                boolean changed = false;
                changed |= removeIdentifier(candidate, PATIENT_FHIR_IDENTIFIER_SYSTEM, stableFhirId);
                changed |= removeIdentifier(candidate, PATIENT_USERNAME_IDENTIFIER_SYSTEM, username);
                changed |= removeIdentifier(candidate, PATIENT_IDENTIFIER_SYSTEM, localId);
                if (changed) {
                    putResource(token, "Patient", candidate.path("id").asText(), candidate);
                    repaired = true;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("patientId", localPatient.getId());
        result.put("name", localPatient.getName());
        result.put("username", localPatient.getUsername());
        result.put("stableFhirId", stableFhirId);
        result.put("canonicalPatientId", canonical != null ? canonical.path("id").asText("") : "");
        result.put("duplicateCount", duplicateCount);
        result.put("repaired", repaired);
        result.put("message", repaired
                ? "发现重复云端患者映射，已自动清理冲突 identifier。"
                : duplicateCount > 0
                ? "发现重复云端患者映射，但未执行修复。"
                : "未发现重复云端患者映射。");
        return result;
    }

    private void addUniquePatients(List<ObjectNode> target, List<ObjectNode> additions) {
        if (additions == null || additions.isEmpty()) {
            return;
        }
        Set<String> existingIds = new HashSet<>();
        for (ObjectNode patient : target) {
            if (patient != null && patient.path("id").isTextual()) {
                existingIds.add(patient.path("id").asText());
            }
        }
        for (ObjectNode patient : additions) {
            if (patient == null || !patient.path("id").isTextual()) {
                continue;
            }
            String patientId = patient.path("id").asText();
            if (existingIds.add(patientId)) {
                target.add(patient);
            }
        }
    }

    private ObjectNode chooseCanonicalPatient(List<ObjectNode> candidates, Patient localPatient) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        ObjectNode best = null;
        int bestScore = Integer.MIN_VALUE;
        for (ObjectNode candidate : candidates) {
            int score = cloudPatientMatchScore(candidate, localPatient);
            if (best == null || score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private boolean removeIdentifier(ObjectNode resource, String system, String value) {
        if (resource == null || !hasText(system) || !hasText(value)) {
            return false;
        }
        JsonNode identifiers = resource.path("identifier");
        if (!(identifiers instanceof ArrayNode arrayNode)) {
            return false;
        }
        ArrayNode retained = objectMapper.createArrayNode();
        boolean removed = false;
        for (JsonNode identifier : arrayNode) {
            String currentSystem = identifier.path("system").asText("");
            String currentValue = identifier.path("value").asText("");
            if (system.equals(currentSystem) && value.equals(currentValue)) {
                removed = true;
                continue;
            }
            retained.add(identifier);
        }
        if (removed) {
            resource.set("identifier", retained);
        }
        return removed;
    }

    private int cloudPatientMatchScore(ObjectNode cloudPatient, Patient localPatient) {
        if (cloudPatient == null || localPatient == null) {
            return Integer.MIN_VALUE;
        }
        int score = 0;
        String localStableKey = normalizeStablePatientKey(localPatient.getFhirId());
        String cloudStableKey = normalizeStablePatientKey(extractIdentifierValue(cloudPatient, PATIENT_FHIR_IDENTIFIER_SYSTEM));
        if (hasText(localStableKey) && localStableKey.equals(cloudStableKey)) {
            score += 100;
        }
        String localUsername = hasText(localPatient.getUsername()) ? localPatient.getUsername().trim() : null;
        String cloudUsername = extractIdentifierValue(cloudPatient, PATIENT_USERNAME_IDENTIFIER_SYSTEM);
        if (hasText(localUsername) && hasText(cloudUsername) && localUsername.equals(cloudUsername.trim())) {
            score += 60;
        }
        String cloudLocalId = extractIdentifierValue(cloudPatient, PATIENT_IDENTIFIER_SYSTEM);
        if (hasText(cloudLocalId) && String.valueOf(localPatient.getId()).equals(cloudLocalId.trim())) {
            score += 40;
        }
        if (hasText(localPatient.getPhone()) && localPatient.getPhone().equals(extractFirstTelecomValue(cloudPatient))) {
            score += 20;
        }
        if (localPatient.getBirthDate() != null && localPatient.getBirthDate().toString().equals(cloudPatient.path("birthDate").asText(null))) {
            score += 20;
        }
        if (hasText(localPatient.getName()) && localPatient.getName().trim().equals(summarizeHumanName(cloudPatient))) {
            score += 15;
        }
        if (hasText(localPatient.getAddress()) && localPatient.getAddress().trim().equals(extractAddressText(cloudPatient))) {
            score += 10;
        }
        if (hasText(localPatient.getEmergencyContact())
                && localPatient.getEmergencyContact().trim().equals(extractExtensionString(cloudPatient, EXT_EMERGENCY_CONTACT))) {
            score += 10;
        }
        return score;
    }

    private String extractFirstTelecomValue(ObjectNode cloudPatient) {
        JsonNode telecom = cloudPatient.path("telecom");
        if (!telecom.isArray() || telecom.isEmpty()) {
            return null;
        }
        for (JsonNode item : telecom) {
            String value = item.path("value").asText(null);
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String extractAddressText(ObjectNode cloudPatient) {
        JsonNode addresses = cloudPatient.path("address");
        if (!addresses.isArray() || addresses.isEmpty()) {
            return null;
        }
        for (JsonNode address : addresses) {
            String text = address.path("text").asText(null);
            if (hasText(text)) {
                return text.trim();
            }
        }
        return null;
    }

    private ObjectNode findPatientByIdentifier(String token, String identifierSystem, String identifierValue) {
        if (!hasText(identifierSystem) || !hasText(identifierValue)) {
            return null;
        }
        JsonNode bundle = fetchBundle(
                token,
                "Patient?identifier=" + encode(identifierSystem + "|" + identifierValue.trim()) + "&_count=1",
                "Patient"
        );
        return firstPatientFromBundle(bundle);
    }

    private ObjectNode findPatientByResourceId(String token, String resourceId) {
        if (!hasText(resourceId)) {
            return null;
        }
        String url = buildFhirUrl("Patient/" + resourceId.trim());
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (response.code() == 404) {
                return null;
            }
            if (!response.isSuccessful()) {
                lastError = "Medplum patient read failed: status=" + response.code() + ", bodySummary=" + sanitizer.summarizeText(body);
                throw new BusinessException(502, "Medplum 查询失败: Patient/" + resourceId + " -> HTTP " + response.code());
            }
            JsonNode root = objectMapper.readTree(body);
            return root instanceof ObjectNode objectNode ? objectNode : null;
        } catch (IOException e) {
            lastError = "Medplum patient read error: " + e.getMessage();
            throw new BusinessException(502, "Medplum 查询失败: " + e.getMessage());
        }
    }

    private ObjectNode firstPatientFromBundle(JsonNode bundle) {
        JsonNode entry = bundle.path("entry");
        if (!entry.isArray() || entry.isEmpty()) {
            return null;
        }
        JsonNode resource = entry.get(0).path("resource");
        return resource instanceof ObjectNode objectNode ? objectNode : null;
    }

    private boolean isConsistentCloudPatient(ObjectNode cloudPatient, Patient localPatient) {
        if (cloudPatient == null || localPatient == null) {
            return false;
        }
        String localStableKey = normalizeStablePatientKey(localPatient.getFhirId());
        String cloudStableKey = normalizeStablePatientKey(extractIdentifierValue(cloudPatient, PATIENT_FHIR_IDENTIFIER_SYSTEM));
        if (hasText(localStableKey) && hasText(cloudStableKey)) {
            return localStableKey.equals(cloudStableKey);
        }

        String localUsername = hasText(localPatient.getUsername()) ? localPatient.getUsername().trim() : null;
        String cloudUsername = extractIdentifierValue(cloudPatient, PATIENT_USERNAME_IDENTIFIER_SYSTEM);
        if (hasText(localUsername) && hasText(cloudUsername)) {
            return localUsername.equals(cloudUsername.trim());
        }

        String cloudLocalId = extractIdentifierValue(cloudPatient, PATIENT_IDENTIFIER_SYSTEM);
        return hasText(cloudLocalId) && String.valueOf(localPatient.getId()).equals(cloudLocalId.trim());
    }

    private String normalizeStablePatientKey(String raw) {
        if (!hasText(raw)) {
            return null;
        }
        return raw.trim().toLowerCase()
                .replaceAll("[^a-z0-9\\-.]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^[\\-.]+|[\\-.]+$", "");
    }

    private void ensureIdentifier(ObjectNode resource, String system, String value) {
        ArrayNode identifiers;
        JsonNode existing = resource.get("identifier");
        if (existing instanceof ArrayNode arrayNode) {
            identifiers = arrayNode;
        } else {
            identifiers = objectMapper.createArrayNode();
            resource.set("identifier", identifiers);
        }

        for (JsonNode identifier : identifiers) {
            if (system.equals(identifier.path("system").asText())
                    && value.equals(identifier.path("value").asText())) {
                return;
            }
        }

        ObjectNode identifierNode = objectMapper.createObjectNode();
        identifierNode.put("system", system);
        identifierNode.put("value", value);
        identifiers.add(identifierNode);
    }

    private void rewriteObservationSubject(ObjectNode observationResource, String patientFhirId) {
        JsonNode subject = observationResource.get("subject");
        if (subject instanceof ObjectNode subjectNode) {
            subjectNode.put("reference", "Patient/" + patientFhirId);
        }
    }

    private SyncResult syncPaperCaseResources(String token, String patientFhirId, Long patientId) throws IOException {
        JsonNode paperCaseBundle = objectMapper.readTree(fhirService.toFhirPaperCasesJson(patientId));
        List<ObjectNode> paperCaseResources = new ArrayList<>();

        for (JsonNode entry : paperCaseBundle.path("entry")) {
            JsonNode resource = entry.path("resource");
            String resourceType = resource.path("resourceType").asText();
            if (!"Encounter".equals(resourceType)
                    && !"Condition".equals(resourceType)
                    && !"DocumentReference".equals(resourceType)) {
                continue;
            }
            ObjectNode resourceCopy = (ObjectNode) resource.deepCopy();
            rewriteSubjectReference(resourceCopy, patientFhirId, "subject");
            paperCaseResources.add(resourceCopy);
        }
        return syncResourceGroup(token, patientId, SYNC_SCOPE_PAPER_CASE, paperCaseResources, null);
    }

    private void rewriteSubjectReference(ObjectNode resource, String patientFhirId, String fieldName) {
        JsonNode subject = resource.get(fieldName);
        if (subject instanceof ObjectNode subjectNode) {
            subjectNode.put("reference", "Patient/" + patientFhirId);
        }
    }

    private String extractIdentifierValue(ObjectNode resource, String identifierSystem) {
        JsonNode identifiers = resource.path("identifier");
        if (!identifiers.isArray()) {
            return null;
        }
        for (JsonNode identifier : identifiers) {
            if (identifierSystem.equals(identifier.path("system").asText())) {
                String value = identifier.path("value").asText(null);
                return hasText(value) ? value.trim() : null;
            }
        }
        return null;
    }

    private String extractSuffix(String raw, String prefix) {
        if (!hasText(raw) || !raw.startsWith(prefix)) {
            return null;
        }
        return raw.substring(prefix.length()).trim();
    }

    private String syncPatientResource(String token, Long patientId, SyncPayload payload) {
        if (incrementalSyncEnabled) {
            Optional<MedplumSyncCheckpoint> existing = medplumSyncCheckpointRepository.findByPatientIdAndSyncScope(patientId, SYNC_SCOPE_PATIENT);
            if (existing.isPresent() && payload.hash().equals(existing.get().getPayloadHash())) {
                return firstNonBlank(existing.get().getResourceId(), extractPatientResourceId(payload.resources().get(0)));
            }
        }
        String patientFhirId = upsertPatient(token, patientId, payload.resources().get(0).deepCopy());
        persistCheckpoint(patientId, SYNC_SCOPE_PATIENT, payload, patientFhirId);
        return patientFhirId;
    }

    private SyncResult syncResourceGroup(String token,
                                         Long patientId,
                                         String syncScope,
                                         List<ObjectNode> resources,
                                         String fallbackIdentifierSystem) {
        if (resources == null || resources.isEmpty()) {
            return new SyncResult(0, 0);
        }

        SyncPayload payload = createSyncPayload(resources, fallbackIdentifierSystem);
        if (incrementalSyncEnabled) {
            Optional<MedplumSyncCheckpoint> existing = medplumSyncCheckpointRepository.findByPatientIdAndSyncScope(patientId, syncScope);
            if (existing.isPresent() && payload.hash().equals(existing.get().getPayloadHash())) {
                return new SyncResult(0, payload.resources().size());
            }
        }

        int syncedCount = transactionBundleEnabled
                ? executeTransactionBundle(token, patientId, syncScope, payload.resources(), fallbackIdentifierSystem)
                : upsertResourcesIndividually(token, payload.resources(), fallbackIdentifierSystem);
        persistCheckpoint(patientId, syncScope, payload, null);
        return new SyncResult(syncedCount, 0);
    }

    private SyncPayload createSyncPayload(List<ObjectNode> resources, String fallbackIdentifierSystem) {
        List<ObjectNode> normalizedResources = new ArrayList<>(resources.size());
        ArrayNode summary = objectMapper.createArrayNode();
        for (ObjectNode original : resources) {
            ObjectNode resource = original.deepCopy();
            ensureSyncIdentifier(resource, fallbackIdentifierSystem);
            normalizedResources.add(resource);
            summary.add(buildResourceSummary(resource));
        }
        return new SyncPayload(normalizedResources, sha256(summary.toString()), summary.toString());
    }

    private void ensureSyncIdentifier(ObjectNode resource, String fallbackIdentifierSystem) {
        JsonNode identifier = primaryIdentifier(resource);
        if (identifier != null) {
            return;
        }
        if (!hasText(fallbackIdentifierSystem)) {
            throw new BusinessException(500, "FHIR 资源缺少稳定 identifier，无法安全执行增量或事务同步。");
        }
        ensureIdentifier(resource, fallbackIdentifierSystem, requireResourceId(resource));
    }

    private ObjectNode buildResourceSummary(ObjectNode resource) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("resourceType", resource.path("resourceType").asText(""));
        summary.put("id", resource.path("id").asText(""));

        JsonNode identifier = primaryIdentifier(resource);
        if (identifier != null) {
            summary.put("identifierSystem", identifier.path("system").asText(""));
            summary.put("identifierValue", identifier.path("value").asText(""));
        }

        String effective = firstNonBlank(
                resource.path("effectiveDateTime").asText(null),
                resource.path("date").asText(null),
                resource.path("period").path("start").asText(null)
        );
        if (effective != null) {
            summary.put("effective", effective);
        }

        JsonNode text = resource.path("code").path("text");
        if (text.isTextual()) {
            summary.put("codeText", text.asText());
        }
        return summary;
    }

    private JsonNode primaryIdentifier(ObjectNode resource) {
        JsonNode identifiers = resource.path("identifier");
        if (!identifiers.isArray() || identifiers.isEmpty()) {
            return null;
        }
        JsonNode first = identifiers.get(0);
        return first.isObject() ? first : null;
    }

    private int executeTransactionBundle(String token,
                                        Long patientId,
                                        String syncScope,
                                        List<ObjectNode> resources,
                                        String fallbackIdentifierSystem) {
        int synced = 0;
        for (int i = 0; i < resources.size(); i += MAX_TRANSACTION_BUNDLE_SIZE) {
            List<ObjectNode> chunk = resources.subList(i, Math.min(resources.size(), i + MAX_TRANSACTION_BUNDLE_SIZE));
            try {
                synced += postTransactionBundle(token, chunk);
            } catch (BusinessException ex) {
                if (!transactionBundleFallbackEnabled) {
                    throw ex;
                }
                log.warn("[MedplumService] transaction bundle chunk failed for patient {}, scope {}, chunk {}-{}, fallback to per-resource upsert: {}",
                        patientId,
                        syncScope,
                        i + 1,
                        i + chunk.size(),
                        ex.getMessage());
                synced += upsertResourcesIndividually(token, chunk, fallbackIdentifierSystem);
            }
        }
        return synced;
    }

    private int upsertResourcesIndividually(String token, List<ObjectNode> resources, String fallbackIdentifierSystem) {
        int synced = 0;
        for (ObjectNode resource : resources) {
            JsonNode identifier = primaryIdentifier(resource);
            if (identifier == null) {
                throw new BusinessException(500, "FHIR 资源缺少稳定 identifier。");
            }
            String resourceType = resource.path("resourceType").asText("");
            String identifierSystem = firstNonBlank(identifier.path("system").asText(null), fallbackIdentifierSystem);
            String identifierValue = identifier.path("value").asText(null);
            if (!hasText(resourceType) || !hasText(identifierSystem) || !hasText(identifierValue)) {
                throw new BusinessException(500, "FHIR 资源 identifier 不完整，无法安全同步。");
            }
            upsertResourceByIdentifier(token, resourceType, identifierSystem, identifierValue.trim(), resource.deepCopy());
            synced++;
        }
        return synced;
    }

    private int postTransactionBundle(String token, List<ObjectNode> resources) {
        ObjectNode bundle = objectMapper.createObjectNode();
        bundle.put("resourceType", "Bundle");
        bundle.put("type", "transaction");
        ArrayNode entries = objectMapper.createArrayNode();

        for (ObjectNode resource : resources) {
            ObjectNode resourceCopy = resource.deepCopy();
            JsonNode identifier = primaryIdentifier(resourceCopy);
            if (identifier == null) {
                throw new BusinessException(500, "FHIR transaction 资源缺少稳定 identifier。");
            }
            String resourceType = resourceCopy.path("resourceType").asText("");
            String identifierSystem = identifier.path("system").asText("");
            String identifierValue = identifier.path("value").asText("");
            if (!hasText(resourceType) || !hasText(identifierSystem) || !hasText(identifierValue)) {
                throw new BusinessException(500, "FHIR transaction 资源 identifier 不完整。");
            }

            resourceCopy.remove("id");
            ObjectNode entry = objectMapper.createObjectNode();
            entry.set("resource", resourceCopy);

            ObjectNode requestNode = objectMapper.createObjectNode();
            requestNode.put("method", "PUT");
            requestNode.put("url", resourceType + "?identifier=" + encode(identifierSystem + "|" + identifierValue));
            entry.set("request", requestNode);
            entries.add(entry);
        }
        bundle.set("entry", entries);

        String payload;
        try {
            payload = objectMapper.writeValueAsString(bundle);
        } catch (IOException e) {
            throw new BusinessException(500, "FHIR transaction bundle 序列化失败: " + e.getMessage());
        }

        Request request = new Request.Builder()
                .url(buildFhirUrl(""))
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/fhir+json")
                .post(RequestBody.create(payload, FHIR_JSON_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                lastError = "Medplum transaction bundle failed: status=" + response.code() + ", bodySummary=" + sanitizer.summarizeText(body);
                throw new BusinessException(502, "Medplum transaction bundle 同步失败: HTTP " + response.code());
            }
            JsonNode root = body.isBlank() ? null : objectMapper.readTree(body);
            int count = countTransactionBundleEntries(root);
            return count > 0 ? count : resources.size();
        } catch (IOException e) {
            lastError = "Medplum transaction bundle error: " + e.getMessage();
            throw new BusinessException(502, "Medplum transaction bundle 同步失败: " + e.getMessage());
        }
    }

    private int countTransactionBundleEntries(JsonNode root) {
        if (root == null) {
            return 0;
        }
        JsonNode entries = root.path("entry");
        if (!entries.isArray()) {
            return 0;
        }
        int count = 0;
        for (JsonNode ignored : entries) {
            count++;
        }
        return count;
    }

    private void persistCheckpoint(Long patientId, String syncScope, SyncPayload payload, String resourceId) {
        if (patientId == null || !hasText(syncScope) || payload == null) {
            return;
        }
        MedplumSyncCheckpoint checkpoint = medplumSyncCheckpointRepository
                .findByPatientIdAndSyncScope(patientId, syncScope)
                .orElseGet(() -> MedplumSyncCheckpoint.builder()
                        .patientId(patientId)
                        .syncScope(syncScope)
                        .build());
        checkpoint.setPayloadHash(payload.hash());
        checkpoint.setResourceCount(payload.resources().size());
        checkpoint.setResourceId(resourceId);
        checkpoint.setResourceSummaryJson(payload.summaryJson());
        checkpoint.setSyncedAt(LocalDateTime.now());
        medplumSyncCheckpointRepository.save(checkpoint);
    }

    private String extractPatientResourceId(ObjectNode patientResource) {
        String explicitId = patientResource.path("id").asText("");
        if (hasText(explicitId)) {
            return explicitId.trim();
        }
        String identifierValue = extractIdentifierValue(patientResource, PATIENT_IDENTIFIER_SYSTEM);
        if (identifierValue != null) {
            return identifierValue;
        }
        throw new BusinessException(500, "患者 FHIR 资源缺少稳定标识。");
    }

    private List<Map<String, Object>> summarizeResources(JsonNode bundle, String resourceType) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode entry : bundle.path("entry")) {
            JsonNode resource = entry.path("resource");
            if (!resourceType.equals(resource.path("resourceType").asText())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", resource.path("id").asText(""));
            item.put("resourceType", resourceType);
            item.put("description", summarizeResource(resourceType, resource));
            item.put("resource", resource);
            items.add(item);
        }
        return items;
    }

    private String summarizeResource(String resourceType, JsonNode resource) {
        return switch (resourceType) {
            case "Encounter" -> resource.path("reasonCode").path(0).path("text").asText("纸质病历就诊");
            case "Condition" -> resource.path("code").path("text").asText("未命名诊断");
            case "DocumentReference" -> resource.path("description").asText("纸质病历文书");
            default -> resourceType;
        };
    }

    private String findResourceIdByIdentifier(String token,
                                              String resourceType,
                                              String identifierSystem,
                                              String identifierValue) {
        String url = buildFhirUrl(resourceType + "?identifier=" + encode(identifierSystem + "|" + identifierValue) + "&_count=1");
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                lastError = "Medplum search failed: resourceType=" + resourceType + ", status=" + response.code() + ", bodySummary=" + sanitizer.summarizeText(body);
                throw new BusinessException(502, "Medplum 查询失败: " + resourceType + " -> HTTP " + response.code());
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode entries = root.path("entry");
            if (!entries.isArray() || entries.isEmpty()) {
                return null;
            }
            return entries.get(0).path("resource").path("id").asText(null);
        } catch (IOException e) {
            lastError = "Medplum search error: " + e.getMessage();
            throw new BusinessException(502, "Medplum 查询失败: " + e.getMessage());
        }
    }

    private ObjectNode findFirstResourceByIdentifier(String token,
                                                     String resourceType,
                                                     String identifierSystem,
                                                     String identifierValue) {
        String url = buildFhirUrl(resourceType + "?identifier=" + encode(identifierSystem + "|" + identifierValue) + "&_count=1");
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                lastError = "Medplum search failed: resourceType=" + resourceType + ", status=" + response.code() + ", bodySummary=" + sanitizer.summarizeText(body);
                throw new BusinessException(502, "Medplum 查询失败: " + resourceType + " -> HTTP " + response.code());
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode entries = root.path("entry");
            if (!entries.isArray() || entries.isEmpty()) {
                return null;
            }
            JsonNode resource = entries.get(0).path("resource");
            return resource instanceof ObjectNode objectNode ? objectNode : null;
        } catch (IOException e) {
            lastError = "Medplum search error: " + e.getMessage();
            throw new BusinessException(502, "Medplum 查询失败: " + e.getMessage());
        }
    }

    private JsonNode fetchBundle(String token, String relativeQuery, String resourceTypeLabel) {
        String url = buildFhirUrl(relativeQuery);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                lastError = "Medplum bundle fetch failed: resourceType=" + resourceTypeLabel + ", status=" + response.code() + ", bodySummary=" + sanitizer.summarizeText(body);
                throw new BusinessException(502, "Medplum 查询失败: " + resourceTypeLabel + " -> HTTP " + response.code());
            }
            return objectMapper.readTree(body);
        } catch (IOException e) {
            lastError = "Medplum bundle fetch error: " + e.getMessage();
            throw new BusinessException(502, "Medplum 查询失败: " + e.getMessage());
        }
    }

    private boolean isConfigured() {
        return enabled
                && hasText(normalizeBaseUrl())
                && (hasText(accessToken) || hasClientCredentials());
    }

    private boolean probeAuthentication() {
        String token = resolveAccessToken();
        String url = buildFhirUrl("Patient?_count=1");
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "no body";
                lastError = "Medplum probe failed: status=" + response.code() + ", bodySummary=" + sanitizer.summarizeText(body);
                throw new BusinessException(502, "Medplum 连通性探测失败: HTTP " + response.code());
            }
            lastError = null;
            return true;
        } catch (IOException e) {
            lastError = "Medplum probe error: " + e.getMessage();
            throw new BusinessException(502, "Medplum 连通性探测失败: " + e.getMessage());
        }
    }

    private String putResource(String token, String resourceType, String resourceId, JsonNode resource) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(resource);
        } catch (IOException e) {
            throw new BusinessException(500, "FHIR 资源序列化失败: " + e.getMessage());
        }

        String url = buildFhirUrl(resourceType + "/" + resourceId);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/fhir+json")
                .put(RequestBody.create(payload, FHIR_JSON_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "no body";
                lastError = "Medplum upsert failed: resourceType=" + resourceType + ", status=" + response.code() + ", bodySummary=" + sanitizer.summarizeText(body);
                throw new BusinessException(502, "Medplum 同步失败: " + resourceType + "/" + resourceId + " -> HTTP " + response.code());
            }
            String body = response.body() != null ? response.body().string() : "";
            JsonNode root = body.isBlank() ? null : objectMapper.readTree(body);
            return root != null && root.path("id").isTextual() ? root.path("id").asText() : resourceId;
        } catch (IOException e) {
            lastError = "Medplum upsert error: " + e.getMessage();
            throw new BusinessException(502, "Medplum 同步失败: " + e.getMessage());
        }
    }

    private String postResource(String token, String resourceType, JsonNode resource) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(resource);
        } catch (IOException e) {
            throw new BusinessException(500, "FHIR 资源序列化失败: " + e.getMessage());
        }

        String url = buildFhirUrl(resourceType);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/fhir+json")
                .post(RequestBody.create(payload, FHIR_JSON_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                lastError = "Medplum create failed: resourceType=" + resourceType + ", status=" + response.code() + ", bodySummary=" + sanitizer.summarizeText(body);
                throw new BusinessException(502, "Medplum 创建失败: " + resourceType + " -> HTTP " + response.code());
            }
            JsonNode root = objectMapper.readTree(body);
            String resourceId = root.path("id").asText("");
            if (resourceId.isBlank()) {
                lastError = "Medplum create response missing id for resourceType=" + resourceType;
                throw new BusinessException(502, "Medplum 创建失败: 响应中缺少资源 id。");
            }
            return resourceId;
        } catch (IOException e) {
            lastError = "Medplum create error: " + e.getMessage();
            throw new BusinessException(502, "Medplum 创建失败: " + e.getMessage());
        }
    }

    private String resolveAccessToken() {
        if (hasText(accessToken)) {
            return accessToken.trim();
        }

        if (cachedAccessToken != null && cachedAccessTokenExpiresAt != null
                && Instant.now().isBefore(cachedAccessTokenExpiresAt.minusSeconds(30))) {
            return cachedAccessToken;
        }

        if (!hasClientCredentials()) {
            throw new BusinessException(400, "Medplum OAuth 凭证缺失，无法获取 access token。");
        }

        FormBody requestBody = new FormBody.Builder()
                .add("grant_type", "client_credentials")
                .build();

        Request request = new Request.Builder()
                .url(buildOauthTokenUrl())
                .addHeader("Authorization", Credentials.basic(clientId.trim(), clientSecret.trim()))
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                lastError = "Medplum token request failed: status=" + response.code() + ", bodySummary=" + sanitizer.summarizeText(body);
                throw new BusinessException(502, "Medplum token 获取失败: HTTP " + response.code());
            }

            JsonNode root = objectMapper.readTree(body);
            String resolvedAccessToken = root.path("access_token").asText("");
            if (resolvedAccessToken.isBlank()) {
                lastError = "Medplum token response missing access_token";
                throw new BusinessException(502, "Medplum token 获取失败: 响应中缺少 access_token。");
            }

            long expiresIn = root.path("expires_in").asLong(3600L);
            cachedAccessToken = resolvedAccessToken;
            cachedAccessTokenExpiresAt = Instant.now().plusSeconds(Math.max(expiresIn, 60L));
            lastError = null;
            return resolvedAccessToken;
        } catch (IOException e) {
            lastError = "Medplum token request error: " + e.getMessage();
            throw new BusinessException(502, "Medplum token 获取失败: " + e.getMessage());
        }
    }

    private String requireResourceId(JsonNode resource) {
        String resourceId = resource.path("id").asText("");
        if (resourceId.isBlank()) {
            throw new BusinessException(500, "FHIR 资源缺少稳定 id，无法安全同步到 Medplum。");
        }
        return resourceId;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String buildStatusMessage(boolean configured, boolean authenticated) {
        if (!enabled) {
            return "Medplum 集成未启用。";
        }
        if (!configured) {
            return "Medplum 凭证未配置完整。";
        }
        if (authenticated) {
            return "Medplum Cloud 已连通，可直接同步 FHIR 资源。";
        }
        return "Medplum 已配置，但认证或连通性校验尚未通过。";
    }

    private String buildOauthTokenUrl() {
        return normalizeBaseUrl() + "/oauth2/token";
    }

    private String buildFhirUrl(String path) {
        String normalizedPath = path == null ? "" : path.trim();
        if (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }
        String base = normalizeBaseUrl() + "/fhir/R4";
        return normalizedPath.isBlank() ? base : base + "/" + normalizedPath;
    }

    private String normalizeBaseUrl() {
        String sanitized = baseUrl == null ? "" : baseUrl.trim();
        if (sanitized.isBlank()) {
            sanitized = "https://api.medplum.com";
        }
        return sanitized.replaceAll("/+$", "");
    }

    private boolean probeMcpBridgeSafely(Map<String, Object> status) {
        String normalizedMcpBaseUrl = normalizeMcpBaseUrl();
        if (!hasText(normalizedMcpBaseUrl)) {
            status.put("mcpMessage", "Medplum MCP bridge 未配置。");
            return false;
        }
        try {
            boolean reachable = probeMcpBridge(normalizedMcpBaseUrl);
            status.put("mcpMessage", "Medplum MCP bridge 已连通。");
            return reachable;
        } catch (RuntimeException e) {
            status.put("mcpMessage", e.getMessage());
            return false;
        }
    }

    private boolean probeMcpBridge(String normalizedMcpBaseUrl) {
        Request request = new Request.Builder()
                .url(normalizedMcpBaseUrl + "/health")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new BusinessException(502, "Medplum MCP bridge 探测失败: HTTP " + response.code());
            }
            return true;
        } catch (IOException e) {
            throw new BusinessException(502, "Medplum MCP bridge 探测失败: " + e.getMessage());
        }
    }

    private String normalizeMcpBaseUrl() {
        return hasText(mcpBaseUrl) ? mcpBaseUrl.trim().replaceAll("/+$", "") : "";
    }

    private boolean hasClientCredentials() {
        return hasText(clientId) && hasText(clientSecret);
    }

    private String summarizeHumanName(JsonNode patient) {
        JsonNode names = patient.path("name");
        if (!names.isArray() || names.isEmpty()) {
            return "";
        }
        JsonNode first = names.get(0);
        String text = first.path("text").asText("");
        if (!text.isBlank()) {
            return text.trim();
        }
        String family = first.path("family").asText("");
        List<String> givenNames = new ArrayList<>();
        for (JsonNode given : first.path("given")) {
            if (given.isTextual() && !given.asText().isBlank()) {
                givenNames.add(given.asText());
            }
        }
        String summary = (family + String.join("", givenNames)).trim();
        if (!summary.isBlank()) {
            return summary;
        }
        for (JsonNode node : names) {
            String fallbackText = node.path("text").asText("");
            if (!fallbackText.isBlank()) {
                return fallbackText.trim();
            }
        }
        return "";
    }

    private record SyncPayload(List<ObjectNode> resources,
                               String hash,
                               String summaryJson) {
    }

    private record SyncResult(int syncedCount,
                              int skippedCount) {
    }

    private String extractObservationCode(JsonNode resource) {
        JsonNode coding = resource.path("code").path("coding");
        if (coding.isArray() && !coding.isEmpty()) {
            JsonNode first = coding.get(0);
            String display = first.path("display").asText("");
            if (!display.isBlank()) {
                return display;
            }
            return first.path("code").asText("");
        }
        return "";
    }

    private String extractObservationValue(JsonNode resource) {
        JsonNode valueQuantity = resource.path("valueQuantity");
        if (valueQuantity.isObject()) {
            String value = valueQuantity.path("value").asText("");
            String unit = valueQuantity.path("unit").asText("");
            return (value + " " + unit).trim();
        }
        if (resource.path("valueBoolean").isBoolean()) {
            return resource.path("valueBoolean").asBoolean() ? "是" : "否";
        }
        if (resource.path("valueString").isTextual()) {
            return resource.path("valueString").asText("");
        }
        if (resource.path("valueCodeableConcept").isObject()) {
            JsonNode coding = resource.path("valueCodeableConcept").path("coding");
            if (coding.isArray() && !coding.isEmpty()) {
                return coding.get(0).path("display").asText("");
            }
        }
        return "";
    }

    private String extractExtensionString(JsonNode resource, String url) {
        JsonNode extension = findExtension(resource, url);
        if (extension == null) {
            return "";
        }
        if (extension.path("valueString").isTextual()) {
            return extension.path("valueString").asText("");
        }
        if (extension.path("valueInteger").canConvertToInt()) {
            return extension.path("valueInteger").asText("");
        }
        if (extension.path("valueBoolean").isBoolean()) {
            return extension.path("valueBoolean").asText("");
        }
        return "";
    }

    private Boolean extractExtensionBoolean(JsonNode resource, String url) {
        JsonNode extension = findExtension(resource, url);
        if (extension == null || !extension.path("valueBoolean").isBoolean()) {
            return null;
        }
        return extension.path("valueBoolean").asBoolean();
    }

    private JsonNode findExtension(JsonNode resource, String url) {
        JsonNode extensions = resource.path("extension");
        if (!extensions.isArray()) {
            return null;
        }
        for (JsonNode extension : extensions) {
            if (url.equals(extension.path("url").asText())) {
                return extension;
            }
        }
        return null;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean bridgeScriptExists() {
        if (!hasText(bridgeScriptPath)) {
            return false;
        }
        try {
            return Files.exists(Path.of(bridgeScriptPath.trim()));
        } catch (InvalidPathException e) {
            log.warn("[MedplumService] Invalid bridge script path: {}", bridgeScriptPath);
            return false;
        }
    }
}
