package com.medical.agent.service.papercase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.common.exception.BusinessException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperCaseVisionOcrServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void shouldCallVisionApiAndParseStructuredResult() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            try {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                requestBody.set(OBJECT_MAPPER.readTree(readBody(exchange)));
                String content = """
                        {
                          "raw_text":"患者李某，男，45岁。主诉：上腹痛伴反酸2周。诊断：胃食管反流病。",
                          "patient_info":{"name":"李某","gender":"男","age":45},
                          "chief_complaint":"上腹痛伴反酸2周",
                          "present_illness":"2周前起反复上腹痛伴反酸",
                          "physical_exam":"上腹部轻压痛",
                          "preliminary_diagnosis":"胃食管反流病",
                          "structured_metrics":{"systolicBp":132,"diastolicBp":86,"fastingGlucose":5.4}
                        }
                        """;
                String response = """
                        {
                          "choices":[
                            {
                              "message":{
                                "content":%s
                              }
                            }
                          ]
                        }
                        """.formatted(OBJECT_MAPPER.writeValueAsString(content));
                writeJson(exchange, 200, response);
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            Path image = tempDir.resolve("case.png");
            BufferedImage bufferedImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            ImageIO.write(bufferedImage, "png", image.toFile());

            PaperCaseVisionOcrService service = new PaperCaseVisionOcrService();
            setField(service, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
            setField(service, "apiKey", "test-key");
            setField(service, "model", "vision-test-model");
            setField(service, "maxPages", 3);

            PaperCaseExtractionResult result = service.extract(image, "image/png");

            assertNotNull(result);
            assertEquals("李某", result.getPatientName());
            assertEquals("男", result.getGender());
            assertEquals(45, result.getAge());
            assertEquals("胃食管反流病", result.getPreliminaryDiagnosis());
            assertEquals(132, result.getStructuredMetrics().getSystolicBp());
            assertEquals(86, result.getStructuredMetrics().getDiastolicBp());
            assertEquals(5.4, result.getStructuredMetrics().getFastingGlucose());

            assertEquals("Bearer test-key", authorization.get());
            assertEquals("vision-test-model", requestBody.get().path("model").asText());
            assertEquals("json_object", requestBody.get().path("response_format").path("type").asText());
            JsonNode content = requestBody.get().path("messages").get(0).path("content");
            assertTrue(content.isArray());
            assertEquals("text", content.get(0).path("type").asText());
            assertEquals("image_url", content.get(1).path("type").asText());
            assertTrue(content.get(1).path("image_url").path("url").asText().startsWith("data:image/png;base64,"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldFailWhenVisionApiReturnsUnparseableContent() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            try {
                writeJson(exchange, 200, """
                        {"choices":[{"message":{"content":"not-json"}}]}
                        """);
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            Path image = tempDir.resolve("case.png");
            BufferedImage bufferedImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            ImageIO.write(bufferedImage, "png", image.toFile());

            PaperCaseVisionOcrService service = new PaperCaseVisionOcrService();
            setField(service, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
            setField(service, "apiKey", "test-key");
            setField(service, "model", "vision-test-model");

            BusinessException ex = null;
            try {
                service.extract(image, "image/png");
            } catch (BusinessException error) {
                ex = error;
            }

            assertNotNull(ex);
            assertEquals(502, ex.getCode());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldUseBaiduOcrThenStructuringApi() throws Exception {
        AtomicReference<String> tokenRequestPath = new AtomicReference<>();
        AtomicReference<String> ocrRequestPath = new AtomicReference<>();
        AtomicReference<String> structuringAuthorization = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/oauth/2.0/token", exchange -> {
            try {
                tokenRequestPath.set(exchange.getRequestURI().toString());
                writeJson(exchange, 200, """
                        {"access_token":"baidu-token","expires_in":2592000}
                        """);
            } finally {
                exchange.close();
            }
        });
        server.createContext("/rest/2.0/ocr/v1/accurate_basic", exchange -> {
            try {
                ocrRequestPath.set(exchange.getRequestURI().toString());
                writeJson(exchange, 200, """
                        {
                          "words_result":[
                            {"words":"患者李某，男，45岁。"},
                            {"words":"主诉：上腹痛伴反酸2周。"},
                            {"words":"诊断：胃食管反流病。"}
                          ]
                        }
                        """);
            } finally {
                exchange.close();
            }
        });
        server.createContext("/chat/completions", exchange -> {
            try {
                structuringAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                String content = """
                        {
                          "raw_text":"患者李某，男，45岁。\\n主诉：上腹痛伴反酸2周。\\n诊断：胃食管反流病。",
                          "patient_info":{"name":"李某","gender":"男","age":45},
                          "chief_complaint":"上腹痛伴反酸2周",
                          "present_illness":"",
                          "physical_exam":"",
                          "preliminary_diagnosis":"胃食管反流病",
                          "structured_metrics":{"systolicBp":132}
                        }
                        """;
                writeJson(exchange, 200, """
                        {"choices":[{"message":{"content":%s}}]}
                        """.formatted(OBJECT_MAPPER.writeValueAsString(content)));
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            Path image = tempDir.resolve("case-baidu.png");
            BufferedImage bufferedImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            ImageIO.write(bufferedImage, "png", image.toFile());

            PaperCaseVisionOcrService service = new PaperCaseVisionOcrService();
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            setField(service, "provider", "baidu-ocr");
            setField(service, "baiduApiKey", "baidu-api-key");
            setField(service, "baiduSecretKey", "baidu-secret-key");
            setField(service, "baiduAuthUrl", base + "/oauth/2.0/token");
            setField(service, "baiduEndpoint", base + "/rest/2.0/ocr/v1/accurate_basic");
            setField(service, "baseUrl", base);
            setField(service, "apiKey", "structuring-key");
            setField(service, "model", "structuring-model");

            PaperCaseExtractionResult result = service.extract(image, "image/png");

            assertNotNull(result);
            assertEquals("李某", result.getPatientName());
            assertEquals("胃食管反流病", result.getPreliminaryDiagnosis());
            assertEquals(132, result.getStructuredMetrics().getSystolicBp());
            assertTrue(tokenRequestPath.get().contains("client_id=baidu-api-key"));
            assertTrue(ocrRequestPath.get().contains("access_token=baidu-token"));
            assertEquals("Bearer structuring-key", structuringAuthorization.get());
        } finally {
            server.stop(0);
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream inputStream = exchange.getRequestBody()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }
}
