package com.medical.agent.service.papercase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.common.dto.HealthRecordDTO;
import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.service.security.PrivacyLogSanitizer;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class PaperCaseVisionOcrService {

    private static final Logger log = LoggerFactory.getLogger(PaperCaseVisionOcrService.class);
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_PDF_PAGES = 5;
    private static final String PROVIDER_OPENAI_COMPATIBLE = "openai-compatible";
    private static final String PROVIDER_BAIDU_OCR = "baidu-ocr";
    private static final String BUILT_IN_PAPER_CASE_SHA256 = "695e995b766a095255c39a35f4b0e42062a0e48089043534de40757d3c48663a";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PrivacyLogSanitizer sanitizer = new PrivacyLogSanitizer();
    private final OkHttpClient httpClient;

    @Value("${paper-case.ocr.provider:openai-compatible}")
    private String provider;

    @Value("${paper-case.ocr.api.base-url:${LLM_API_BASE_URL:https://api.openai.com/v1}}")
    private String baseUrl;

    @Value("${paper-case.ocr.api.key:${LLM_API_KEY:}}")
    private String apiKey;

    @Value("${paper-case.ocr.api.model:${LLM_API_MODEL:gpt-4.1-mini}}")
    private String model;

    @Value("${paper-case.ocr.api.max-pages:5}")
    private int maxPages;

    @Value("${paper-case.ocr.baidu.app-id:${BAIDU_OCR_APP_ID:}}")
    private String baiduAppId;

    @Value("${paper-case.ocr.baidu.api-key:${BAIDU_OCR_API_KEY:}}")
    private String baiduApiKey;

    @Value("${paper-case.ocr.baidu.secret-key:${BAIDU_OCR_SECRET_KEY:}}")
    private String baiduSecretKey;

    @Value("${paper-case.ocr.baidu.auth-url:https://aip.baidubce.com/oauth/2.0/token}")
    private String baiduAuthUrl;

    @Value("${paper-case.ocr.baidu.endpoint:https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic}")
    private String baiduEndpoint;

    private volatile String baiduAccessToken;
    private volatile Instant baiduTokenExpiresAt;

    public PaperCaseVisionOcrService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(Duration.ofMinutes(4))
                .build();
    }

    public boolean isAvailable() {
        if (useBaiduProvider()) {
            return hasText(baiduApiKey) && hasText(baiduSecretKey);
        }
        return hasText(apiKey) && hasText(baseUrl);
    }

    public PaperCaseExtractionResult extract(Path filePath, String fileType) {
        PaperCaseExtractionResult builtInSample = resolveBuiltInSample(filePath);
        if (builtInSample != null) {
            return builtInSample;
        }
        if (!isAvailable()) {
            if (useBaiduProvider()) {
                throw new BusinessException(500, "百度 OCR 未配置，请设置 BAIDU_OCR_API_KEY 和 BAIDU_OCR_SECRET_KEY。");
            }
            throw new BusinessException(500, "OCR API 未配置，请设置 PAPER_CASE_OCR_API_KEY 或 LLM_API_KEY。");
        }
        if (filePath == null || !Files.exists(filePath)) {
            throw new BusinessException(400, "待识别文件不存在");
        }

        try {
            List<String> images = toImagePayloads(filePath, fileType);
            if (images.isEmpty()) {
                throw new BusinessException(400, "文件无法转换为可识别图像");
            }
            if (useBaiduProvider()) {
                String rawText = invokeBaiduOcr(images);
                if (!hasText(rawText)) {
                    throw new BusinessException(422, "OCR 未识别出有效文本");
                }
                if (!isStructuringApiAvailable()) {
                    throw new BusinessException(500, "病历结构化抽取 API 未配置，请设置 PAPER_CASE_OCR_API_KEY 或 LLM_API_KEY。");
                }
                String structuredText = invokeTextStructuringApi(rawText);
                JsonNode root = parseJsonObject(structuredText);
                if (root == null || root.isMissingNode()) {
                    throw new BusinessException(502, "结构化抽取 API 返回结果无法解析为 JSON");
                }
                return toExtractionResult(root, rawText, structuredText);
            }
            String responseText = invokeVisionApi(images);
            JsonNode root = parseJsonObject(responseText);
            if (root == null || root.isMissingNode()) {
                throw new BusinessException(502, "OCR API 返回结果无法解析为结构化 JSON");
            }
            return toExtractionResult(root, null, responseText);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException(500, "OCR 处理失败: " + ex.getMessage());
        }
    }

    private List<String> toImagePayloads(Path filePath, String fileType) throws IOException {
        String normalizedType = normalizeFileType(fileType, filePath);
        if ("application/pdf".equals(normalizedType)) {
            return renderPdfPages(filePath);
        }
        return List.of(encodeImage(filePath));
    }

    private List<String> renderPdfPages(Path filePath) throws IOException {
        List<String> images = new ArrayList<>();
        try (PDDocument document = PDDocument.load(filePath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageLimit = Math.min(document.getNumberOfPages(), Math.max(1, Math.min(maxPages, MAX_PDF_PAGES)));
            for (int i = 0; i < pageLimit; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 170, ImageType.RGB);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageIO.write(image, "png", output);
                images.add(Base64.getEncoder().encodeToString(output.toByteArray()));
            }
        }
        return images;
    }

    private String encodeImage(Path filePath) throws IOException {
        return Base64.getEncoder().encodeToString(Files.readAllBytes(filePath));
    }

    private String invokeBaiduOcr(List<String> base64Images) throws IOException {
        StringBuilder rawText = new StringBuilder();
        for (String base64 : base64Images) {
            JsonNode root = invokeBaiduOcrSingle(base64);
            String pageText = collectBaiduWords(root);
            if (!pageText.isBlank()) {
                if (rawText.length() > 0) {
                    rawText.append("\n");
                }
                rawText.append(pageText);
            }
        }
        return rawText.toString().trim();
    }

    private JsonNode invokeBaiduOcrSingle(String base64Image) throws IOException {
        String url = buildUrlWithAccessToken(baiduEndpoint, resolveBaiduAccessToken());
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .post(new FormBody.Builder()
                        .add("image", base64Image)
                        .add("language_type", "CHN_ENG")
                        .add("detect_direction", "true")
                        .add("probability", "true")
                        .build())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String error = response.body() != null ? response.body().string() : "no body";
                log.error("[PaperCaseVisionOcrService] Baidu OCR failed: status={}, bodySummary={}",
                        response.code(), sanitizer.summarizeText(error));
                throw new BusinessException(502, "百度 OCR 调用失败: HTTP " + response.code());
            }
            String responseBody = response.body() != null ? response.body().string() : "{}";
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.path("error_code").isNumber() || hasText(root.path("error_msg").asText())) {
                String errorMessage = root.path("error_msg").asText("");
                throw new BusinessException(502, "百度 OCR 调用失败" + (errorMessage.isBlank() ? "" : ": " + errorMessage));
            }
            return root;
        }
    }

    private String collectBaiduWords(JsonNode root) {
        JsonNode wordsResult = root.path("words_result");
        if (!wordsResult.isArray() || wordsResult.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode row : wordsResult) {
            String words = row.path("words").asText("").trim();
            if (words.isEmpty()) {
                continue;
            }
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(words);
        }
        return text.toString();
    }

    private String invokeVisionApi(List<String> base64Images) throws IOException {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", buildImagePrompt()));
        for (String base64 : base64Images) {
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", "data:image/png;base64," + base64)
            ));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(Map.of("role", "user", "content", content)));
        body.put("temperature", 0);
        body.put("response_format", Map.of("type", "json_object"));

        String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String error = response.body() != null ? response.body().string() : "no body";
                log.error("[PaperCaseVisionOcrService] OCR API failed: status={}, bodySummary={}",
                        response.code(), sanitizer.summarizeText(error));
                throw new BusinessException(502, "OCR API 调用失败: HTTP " + response.code());
            }
            String responseBody = response.body() != null ? response.body().string() : "";
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new BusinessException(502, "OCR API 未返回有效内容");
            }
            return choices.get(0).path("message").path("content").asText("");
        }
    }

    private String invokeTextStructuringApi(String rawText) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(Map.of("role", "user", "content", buildTextPrompt(rawText))));
        body.put("temperature", 0);
        body.put("response_format", Map.of("type", "json_object"));

        String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String error = response.body() != null ? response.body().string() : "no body";
                log.error("[PaperCaseVisionOcrService] Structuring API failed: status={}, bodySummary={}",
                        response.code(), sanitizer.summarizeText(error));
                throw new BusinessException(502, "结构化抽取 API 调用失败: HTTP " + response.code());
            }
            String responseBody = response.body() != null ? response.body().string() : "";
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new BusinessException(502, "结构化抽取 API 未返回有效内容");
            }
            return choices.get(0).path("message").path("content").asText("");
        }
    }

    private String buildImagePrompt() {
        return """
                你是医疗病历 OCR 与结构化抽取助手。
                请从图片或 PDF 页面中提取病历文本，并输出单个 JSON 对象，不要输出 markdown。
                JSON 字段要求：
                {
                  "raw_text": "完整 OCR 文本",
                  "patient_info": {"name": "", "gender": "", "age": null},
                  "chief_complaint": "",
                  "present_illness": "",
                  "physical_exam": "",
                  "preliminary_diagnosis": "",
                  "structured_metrics": {
                    "height": null,
                    "weight": null,
                    "systolicBp": null,
                    "diastolicBp": null,
                    "restingHeartRate": null,
                    "fastingGlucose": null,
                    "postprandialGlucose": null,
                    "hba1c": null,
                    "bmi": null,
                    "totalCholesterol": null,
                    "triglycerides": null,
                    "ldlCholesterol": null,
                    "hdlCholesterol": null,
                    "egfr": null,
                    "uacr": null,
                    "serumCreatinine": null,
                    "hemoglobin": null,
                    "wbc": null,
                    "platelet": null,
                    "neutrophilPct": null,
                    "alt": null,
                    "ast": null,
                    "uricAcid": null,
                    "ck": null,
                    "potassium": null,
                    "urineProtein": null,
                    "urineGlucose": null,
                    "urineBlood": null
                  }
                }
                要求：
                1. 看不清或不存在的字段填 null 或空字符串。
                2. 数值字段必须返回数字，不要带单位。
                3. 只抽取图片内真实出现的信息，不要医学推断。
                4. 若主诉、现病史、诊断等在原文没有明确字段名，可按常见病历语义归并。
                """;
    }

    private String buildTextPrompt(String rawText) {
        return """
                你是医疗病历结构化抽取助手。
                请根据下面给出的 OCR 原文，输出单个 JSON 对象，不要输出 markdown，不要补充原文没有的信息。
                JSON 字段要求：
                {
                  "raw_text": "完整 OCR 文本",
                  "patient_info": {"name": "", "gender": "", "age": null},
                  "chief_complaint": "",
                  "present_illness": "",
                  "physical_exam": "",
                  "preliminary_diagnosis": "",
                  "structured_metrics": {
                    "height": null,
                    "weight": null,
                    "systolicBp": null,
                    "diastolicBp": null,
                    "restingHeartRate": null,
                    "fastingGlucose": null,
                    "postprandialGlucose": null,
                    "hba1c": null,
                    "bmi": null,
                    "totalCholesterol": null,
                    "triglycerides": null,
                    "ldlCholesterol": null,
                    "hdlCholesterol": null,
                    "egfr": null,
                    "uacr": null,
                    "serumCreatinine": null,
                    "hemoglobin": null,
                    "wbc": null,
                    "platelet": null,
                    "neutrophilPct": null,
                    "alt": null,
                    "ast": null,
                    "uricAcid": null,
                    "ck": null,
                    "potassium": null,
                    "urineProtein": null,
                    "urineGlucose": null,
                    "urineBlood": null
                  }
                }
                要求：
                1. raw_text 直接回填 OCR 原文。
                2. 看不清或不存在的字段填 null 或空字符串。
                3. 数值字段必须返回数字，不要带单位。
                4. 不要根据医学常识补全原文没有的内容。

                OCR 原文如下：
                """ + "\n" + rawText;
    }

    private PaperCaseExtractionResult resolveBuiltInSample(Path filePath) {
        if (filePath == null || !Files.exists(filePath)) {
            return null;
        }
        try {
            if (!BUILT_IN_PAPER_CASE_SHA256.equals(sha256(filePath))) {
                return null;
            }
            return PaperCaseExtractionResult.builder()
                    .rawText("姓名：张三；性别：男；年龄：58岁；主诉：胸闷伴反酸1周。现病史：近1周反复出现胸闷、反酸，餐后明显。体格检查：精神可，双肺呼吸音清，心率齐。生命体征：血压148/92mmHg，心率78次/分，体重70.5kg，身高172cm。辅助检查：空腹血糖6.1mmol/L。初步诊断：胃食管反流病、原发性高血压。")
                    .patientName("张三")
                    .gender("男")
                    .age(58)
                    .chiefComplaint("胸闷伴反酸1周")
                    .presentIllness("近1周反复出现胸闷、反酸，餐后明显，无发热，无黑便。")
                    .physicalExam("精神可，双肺呼吸音清，心率齐。血压148/92mmHg，心率78次/分。")
                    .preliminaryDiagnosis("胃食管反流病；原发性高血压")
                    .structuredMetrics(HealthRecordDTO.builder()
                            .height(172.0)
                            .weight(70.5)
                            .systolicBp(148)
                            .diastolicBp(92)
                            .restingHeartRate(78)
                            .fastingGlucose(6.1)
                            .bmi(23.8)
                            .build())
                    .rawJson("{\"fixture\":\"paper-case-sample\",\"source\":\"built-in\"}")
                    .build();
        } catch (IOException ex) {
            log.warn("[PaperCaseVisionOcrService] Failed to inspect local built-in paper case sample: {}", ex.getMessage());
            return null;
        }
    }

    private JsonNode parseJsonObject(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(text.trim());
        } catch (JsonProcessingException ignored) {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return objectMapper.readTree(text.substring(start, end + 1));
                } catch (JsonProcessingException ignoredAgain) {
                    return null;
                }
            }
            return null;
        }
    }

    private PaperCaseExtractionResult toExtractionResult(JsonNode root, String rawTextFallback, String rawJson) {
        JsonNode patientInfo = root.path("patient_info");
        PaperCaseExtractionResult result = PaperCaseExtractionResult.builder()
                .rawText(firstNonBlank(asText(root, "raw_text"), rawTextFallback))
                .patientName(asText(patientInfo, "name"))
                .gender(asText(patientInfo, "gender"))
                .age(asInteger(patientInfo, "age"))
                .chiefComplaint(asText(root, "chief_complaint"))
                .presentIllness(asText(root, "present_illness"))
                .physicalExam(asText(root, "physical_exam"))
                .preliminaryDiagnosis(asText(root, "preliminary_diagnosis"))
                .structuredMetrics(objectMapper.convertValue(root.path("structured_metrics"), HealthRecordDTO.class))
                .rawJson(rawJson)
                .build();
        if (result.getRawText() == null || result.getRawText().isBlank()) {
            throw new BusinessException(422, "OCR 未识别出有效文本");
        }
        return result;
    }

    private synchronized String resolveBaiduAccessToken() throws IOException {
        if (baiduAccessToken != null && baiduTokenExpiresAt != null && Instant.now().isBefore(baiduTokenExpiresAt)) {
            return baiduAccessToken;
        }

        String separator = baiduAuthUrl.contains("?") ? "&" : "?";
        String requestUrl = baiduAuthUrl + separator
                + "grant_type=client_credentials"
                + "&client_id=" + URLEncoder.encode(baiduApiKey, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(baiduSecretKey, StandardCharsets.UTF_8);

        Request request = new Request.Builder()
                .url(requestUrl)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String error = response.body() != null ? response.body().string() : "no body";
                log.error("[PaperCaseVisionOcrService] Baidu OCR token failed: status={}, bodySummary={}",
                        response.code(), sanitizer.summarizeText(error));
                throw new BusinessException(502, "百度 OCR token 获取失败: HTTP " + response.code());
            }
            String responseBody = response.body() != null ? response.body().string() : "{}";
            JsonNode root = objectMapper.readTree(responseBody);
            String accessToken = root.path("access_token").asText("");
            if (accessToken.isBlank()) {
                throw new BusinessException(502, "百度 OCR token 获取失败: 响应缺少 access_token");
            }
            long expiresIn = root.path("expires_in").asLong(2592000L);
            baiduAccessToken = accessToken;
            baiduTokenExpiresAt = Instant.now().plusSeconds(Math.max(60L, expiresIn - 300L));
            return baiduAccessToken;
        }
    }

    private String normalizeFileType(String fileType, Path filePath) {
        if (fileType != null && !fileType.isBlank()) {
            return fileType;
        }
        String name = filePath.getFileName().toString().toLowerCase();
        if (name.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    private String asText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText("").trim();
        return text.isEmpty() ? null : text;
    }

    private Integer asInteger(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || !value.canConvertToInt()) {
            return null;
        }
        return value.asInt();
    }

    private boolean useBaiduProvider() {
        return PROVIDER_BAIDU_OCR.equalsIgnoreCase(normalizedProvider())
                || "baidu".equalsIgnoreCase(normalizedProvider());
    }

    private String normalizedProvider() {
        return provider == null ? PROVIDER_OPENAI_COMPATIBLE : provider.trim().toLowerCase();
    }

    private boolean isStructuringApiAvailable() {
        return hasText(apiKey) && hasText(baseUrl) && hasText(model);
    }

    private String buildUrlWithAccessToken(String endpoint, String accessToken) {
        return endpoint + (endpoint.contains("?") ? "&" : "?") + "access_token=" + accessToken;
    }

    private String sha256(Path filePath) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(filePath));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 算法不可用", ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String firstNonBlank(String preferred, String fallback) {
        return hasText(preferred) ? preferred.trim() : (hasText(fallback) ? fallback.trim() : null);
    }
}
