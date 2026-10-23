package com.medical.agent.service.papercase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.medical.agent.common.dto.PaperCaseIcdCandidateDTO;
import com.medical.agent.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import okhttp3.Credentials;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class PaperCaseIcd11Service {

    private static final Logger log = LoggerFactory.getLogger(PaperCaseIcd11Service.class);
    private static final List<String> LOCAL_MAPPING_PROVIDERS = List.of("local-mapping", "manual");

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();
    private final OkHttpClient httpClient;
    private volatile List<LocalIcdEntry> localEntries = List.of();

    @Value("${paper-case.icd11.provider:${PAPER_CASE_ICD11_PROVIDER:local-mapping}}")
    private String provider = "local-mapping";

    @Value("${paper-case.icd11.base-url:https://id.who.int}")
    private String baseUrl = "https://id.who.int";

    @Value("${paper-case.icd11.token-url:https://icdaccessmanagement.who.int/connect/token}")
    private String tokenUrl = "https://icdaccessmanagement.who.int/connect/token";

    @Value("${paper-case.icd11.client-id:${WHO_ICD_CLIENT_ID:}}")
    private String clientId = "";

    @Value("${paper-case.icd11.client-secret:${WHO_ICD_CLIENT_SECRET:}}")
    private String clientSecret = "";

    @Value("${paper-case.icd11.release-id:${WHO_ICD_RELEASE:2026-01}}")
    private String releaseId = "2026-01";

    @Value("${paper-case.icd11.linearization:mms}")
    private String linearization = "mms";

    @Value("${paper-case.icd11.language:${WHO_ICD_LANGUAGE:zh}}")
    private String language = "zh";

    @Value("${paper-case.icd11.local.top-k:3}")
    private int localTopK = 3;

    @Value("${paper-case.icd11.local.mapping-resource:classpath:icd11/icd11-local-mapping-sample.csv}")
    private Resource localMappingResource = new ClassPathResource("icd11/icd11-local-mapping-sample.csv");

    private volatile String cachedAccessToken;
    private volatile Instant cachedTokenExpiresAt;

    public PaperCaseIcd11Service() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(40, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    @PostConstruct
    void init() {
        if (usesLocalMappingProvider()) {
            reloadLocalMappings();
        }
    }

    public boolean isAvailable() {
        if (usesLocalMappingProvider()) {
            ensureLocalMappingsLoaded();
            return !localEntries.isEmpty();
        }
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    public PaperCaseIcdMatchResult matchDiagnosis(String diagnosis) {
        String normalized = diagnosis == null ? "" : diagnosis.trim();
        if (normalized.isBlank()) {
            return emptyResult("not_coded", currentProvider());
        }
        if (usesLocalMappingProvider()) {
            return matchByLocalMapping(normalized);
        }
        if (!isAvailable()) {
            log.warn("[PaperCaseIcd11Service] WHO ICD-11 credentials missing, skip coding");
            return emptyResult("not_coded", "who-remote");
        }

        List<PaperCaseIcdCandidateDTO> candidates = new ArrayList<>();
        PaperCaseIcdCandidateDTO autoCode = tryAutoCode(normalized);
        if (autoCode != null) {
            candidates.add(autoCode);
        }
        candidates.addAll(searchCandidates(normalized));
        List<PaperCaseIcdCandidateDTO> deduped = dedupeAndSort(candidates);
        if (deduped.isEmpty()) {
            return emptyResult("not_coded", "who-remote");
        }
        PaperCaseIcdCandidateDTO top = deduped.get(0);
        return PaperCaseIcdMatchResult.builder()
                .code(top.getCode())
                .title(top.getTitle())
                .confidence(top.getConfidence())
                .codingStatus("auto_coded")
                .codingSource("who-remote")
                .candidates(deduped)
                .build();
    }

    public void reloadLocalMappings() {
        if (localMappingResource == null || !localMappingResource.exists()) {
            log.warn("[PaperCaseIcd11Service] local mapping resource not found: {}", localMappingResource);
            localEntries = List.of();
            return;
        }
        try (InputStream inputStream = localMappingResource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            List<LocalIcdEntry> loaded = new ArrayList<>();
            String line;
            boolean headerSkipped = false;
            while ((line = reader.readLine()) != null) {
                if (!headerSkipped) {
                    headerSkipped = true;
                    continue;
                }
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = parseCsvLine(line);
                if (cols.length < 5) {
                    continue;
                }
                String code = trimToNull(cols[0]);
                String titleEn = trimToNull(cols[1]);
                String titleZh = trimToNull(cols[2]);
                if (code == null || (titleEn == null && titleZh == null)) {
                    continue;
                }
                Set<String> aliases = new LinkedHashSet<>();
                addAlias(aliases, titleEn);
                addAlias(aliases, titleZh);
                for (String keyword : splitAliases(cols[3])) {
                    addAlias(aliases, keyword);
                }
                for (String synonym : splitAliases(cols[4])) {
                    addAlias(aliases, synonym);
                }
                loaded.add(new LocalIcdEntry(code, firstNonBlank(titleZh, titleEn), aliases));
            }
            localEntries = List.copyOf(loaded);
        } catch (IOException ex) {
            throw new BusinessException(500, "加载本地 ICD-11 映射表失败: " + ex.getMessage());
        }
    }

    private PaperCaseIcdMatchResult matchByLocalMapping(String diagnosis) {
        ensureLocalMappingsLoaded();
        List<PaperCaseIcdCandidateDTO> candidates = localEntries.stream()
                .map(entry -> scoreLocalEntry(entry, diagnosis))
                .filter(candidate -> candidate.getConfidence() != null && candidate.getConfidence() > 0)
                .sorted(Comparator.comparingDouble(this::scoreOf).reversed())
                .limit(Math.max(1, localTopK))
                .toList();
        if (candidates.isEmpty()) {
            return emptyResult("not_coded", "local-mapping");
        }
        PaperCaseIcdCandidateDTO top = candidates.get(0);
        return PaperCaseIcdMatchResult.builder()
                .code(top.getCode())
                .title(top.getTitle())
                .confidence(top.getConfidence())
                .codingStatus("pending_review")
                .codingSource("local-mapping")
                .candidates(candidates)
                .build();
    }

    private PaperCaseIcdCandidateDTO scoreLocalEntry(LocalIcdEntry entry, String diagnosis) {
        String normalizedDiagnosis = normalizeForMatch(diagnosis);
        String diagnosisCompact = compactChinesePunctuation(normalizedDiagnosis);
        double bestScore = 0.0;
        for (String alias : entry.aliases()) {
            String normalizedAlias = normalizeForMatch(alias);
            if (normalizedAlias.isBlank()) {
                continue;
            }
            double score = 0.0;
            if (diagnosisCompact.equals(normalizedAlias)) {
                score = 0.99;
            } else if (diagnosisCompact.contains(normalizedAlias) || normalizedAlias.contains(diagnosisCompact)) {
                score = 0.86;
            } else {
                Set<String> diagnosisTokens = tokenize(normalizedDiagnosis);
                Set<String> aliasTokens = tokenize(normalizedAlias);
                if (!diagnosisTokens.isEmpty() && !aliasTokens.isEmpty()) {
                    long overlap = diagnosisTokens.stream().filter(aliasTokens::contains).count();
                    if (overlap > 0) {
                        double precision = overlap / (double) aliasTokens.size();
                        double recall = overlap / (double) diagnosisTokens.size();
                        score = Math.max(score, (0.45 * precision) + (0.35 * recall));
                    }
                }
            }
            if (alias.equals(entry.title())) {
                score += 0.02;
            }
            bestScore = Math.max(bestScore, Math.min(score, 0.99));
        }
        return PaperCaseIcdCandidateDTO.builder()
                .code(entry.code())
                .title(entry.title())
                .confidence(bestScore)
                .source("local-mapping")
                .build();
    }

    private PaperCaseIcdCandidateDTO tryAutoCode(String diagnosis) {
        String query = "searchText=" + urlEncode(diagnosis);
        String path = String.format("/icd/release/11/%s/%s/autocode?%s", releaseId, linearization, query);
        JsonNode root = getJson(path);
        if (root == null || root.path("theCode").asText("").isBlank()) {
            return null;
        }
        return PaperCaseIcdCandidateDTO.builder()
                .code(blankToNull(root.path("theCode").asText(null)))
                .title(blankToNull(root.path("matchingText").asText(null)))
                .confidence(root.path("matchScore").isNumber() ? root.path("matchScore").asDouble() : 0.0)
                .source("autocode")
                .build();
    }

    private List<PaperCaseIcdCandidateDTO> searchCandidates(String diagnosis) {
        String query = "q=" + urlEncode(diagnosis)
                + "&useFlexisearch=true"
                + "&flatResults=true"
                + "&highlightingEnabled=false";
        String path = String.format("/icd/release/11/%s/%s/search?%s", releaseId, linearization, query);
        JsonNode root = getJson(path);
        List<PaperCaseIcdCandidateDTO> results = new ArrayList<>();
        JsonNode entities = root == null ? null : root.path("destinationEntities");
        if (entities == null || !entities.isArray()) {
            return results;
        }
        for (JsonNode entity : entities) {
            String code = blankToNull(entity.path("theCode").asText(null));
            if (code == null) {
                continue;
            }
            results.add(PaperCaseIcdCandidateDTO.builder()
                    .code(code)
                    .title(blankToNull(entity.path("title").asText(null)))
                    .confidence(entity.path("score").isNumber() ? entity.path("score").asDouble() : 0.0)
                    .source("search")
                    .build());
            if (results.size() >= 5) {
                break;
            }
        }
        return results;
    }

    private List<PaperCaseIcdCandidateDTO> dedupeAndSort(List<PaperCaseIcdCandidateDTO> candidates) {
        return candidates.stream()
                .filter(item -> item != null && item.getCode() != null && !item.getCode().isBlank())
                .collect(Collectors.toMap(
                        PaperCaseIcdCandidateDTO::getCode,
                        item -> item,
                        (a, b) -> scoreOf(a) >= scoreOf(b) ? a : b
                ))
                .values()
                .stream()
                .sorted(Comparator.comparingDouble(this::scoreOf).reversed())
                .limit(3)
                .toList();
    }

    private double scoreOf(PaperCaseIcdCandidateDTO item) {
        return item == null || item.getConfidence() == null ? 0.0 : item.getConfidence();
    }

    private JsonNode getJson(String path) {
        try {
            Request request = new Request.Builder()
                    .url(normalizeBaseUrl() + path)
                    .addHeader("Authorization", "Bearer " + resolveAccessToken())
                    .addHeader("Accept", "application/json")
                    .addHeader("API-Version", "v2")
                    .addHeader("Accept-Language", language)
                    .get()
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "no body";
                    log.warn("[PaperCaseIcd11Service] WHO ICD request failed: status={}, path={}, body={}",
                            response.code(), path, errorBody);
                    return null;
                }
                String body = response.body() != null ? response.body().string() : "{}";
                return objectMapper.readTree(body);
            }
        } catch (IOException ex) {
            log.warn("[PaperCaseIcd11Service] WHO ICD request error: {}", ex.getMessage());
            return null;
        }
    }

    private synchronized String resolveAccessToken() {
        if (cachedAccessToken != null && cachedTokenExpiresAt != null && Instant.now().isBefore(cachedTokenExpiresAt)) {
            return cachedAccessToken;
        }

        Request request = new Request.Builder()
                .url(tokenUrl)
                .addHeader("Authorization", Credentials.basic(clientId, clientSecret))
                .post(new FormBody.Builder()
                        .add("grant_type", "client_credentials")
                        .add("scope", "icdapi_access")
                        .build())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new BusinessException(502, "WHO ICD-11 token 获取失败: HTTP " + response.code());
            }
            String body = response.body() != null ? response.body().string() : "{}";
            JsonNode root = objectMapper.readTree(body);
            String accessToken = root.path("access_token").asText("");
            if (accessToken.isBlank()) {
                throw new BusinessException(502, "WHO ICD-11 token 获取失败: 响应缺少 access_token");
            }
            long expiresIn = root.path("expires_in").asLong(3600);
            cachedAccessToken = accessToken;
            cachedTokenExpiresAt = Instant.now().plusSeconds(Math.max(60, expiresIn - 60));
            return cachedAccessToken;
        } catch (IOException ex) {
            throw new BusinessException(502, "WHO ICD-11 token 获取失败: " + ex.getMessage());
        }
    }

    private boolean usesLocalMappingProvider() {
        String current = currentProvider();
        return LOCAL_MAPPING_PROVIDERS.contains(current);
    }

    private void ensureLocalMappingsLoaded() {
        if (!localEntries.isEmpty()) {
            return;
        }
        reloadLocalMappings();
    }

    private String currentProvider() {
        return provider == null ? "local-mapping" : provider.trim().toLowerCase(Locale.ROOT);
    }

    private PaperCaseIcdMatchResult emptyResult(String status, String source) {
        return PaperCaseIcdMatchResult.builder()
                .codingStatus(status)
                .codingSource(source)
                .candidates(List.of())
                .build();
    }

    private String normalizeBaseUrl() {
        return baseUrl != null && baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToNull(String value) {
        return blankToNull(value);
    }

    private String firstNonBlank(String a, String b) {
        String first = trimToNull(a);
        return first != null ? first : trimToNull(b);
    }

    private void addAlias(Set<String> aliases, String value) {
        String trimmed = trimToNull(value);
        if (trimmed != null) {
            aliases.add(trimmed);
        }
    }

    private List<String> splitAliases(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            return List.of();
        }
        return Arrays.stream(trimmed.split("[|;；、,，/]"))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .toList();
    }

    private String[] parseCsvLine(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                columns.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        columns.add(current.toString());
        return columns.toArray(String[]::new);
    }

    private String normalizeForMatch(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[()（）\\[\\]{}]", " ")
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsIdeographic}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }

    private String compactChinesePunctuation(String value) {
        return value == null ? "" : value.replace(" ", "");
    }

    private Set<String> tokenize(String value) {
        String normalized = normalizeForMatch(value);
        if (normalized.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : normalized.split(" ")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        if (tokens.isEmpty() && !normalized.isBlank()) {
            tokens.add(normalized);
        }
        return tokens;
    }

    private record LocalIcdEntry(String code, String title, Set<String> aliases) {
    }
}
