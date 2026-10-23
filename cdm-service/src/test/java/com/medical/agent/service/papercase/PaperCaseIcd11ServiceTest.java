package com.medical.agent.service.papercase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperCaseIcd11ServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldResolveTokenAndReturnTopCandidatesFromWhoApi() throws Exception {
        AtomicInteger tokenCalls = new AtomicInteger();
        AtomicReference<String> tokenAuthorization = new AtomicReference<>();
        AtomicReference<String> tokenBody = new AtomicReference<>();
        AtomicReference<String> searchAuthorization = new AtomicReference<>();
        AtomicReference<String> apiVersionHeader = new AtomicReference<>();
        AtomicReference<String> acceptLanguageHeader = new AtomicReference<>();
        AtomicReference<String> autoCodePath = new AtomicReference<>();
        AtomicReference<String> searchPath = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/connect/token", exchange -> {
            try {
                tokenCalls.incrementAndGet();
                tokenAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                tokenBody.set(readBody(exchange));
                writeJson(exchange, 200, """
                        {"access_token":"token-123","expires_in":3600}
                        """);
            } finally {
                exchange.close();
            }
        });
        server.createContext("/icd/release/11/2026-01/mms/autocode", exchange -> {
            try {
                autoCodePath.set(exchange.getRequestURI().toString());
                searchAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                apiVersionHeader.set(exchange.getRequestHeaders().getFirst("API-Version"));
                acceptLanguageHeader.set(exchange.getRequestHeaders().getFirst("Accept-Language"));
                writeJson(exchange, 200, """
                        {"theCode":"DA22","matchingText":"胃食管反流病","matchScore":0.95}
                        """);
            } finally {
                exchange.close();
            }
        });
        server.createContext("/icd/release/11/2026-01/mms/search", exchange -> {
            try {
                searchPath.set(exchange.getRequestURI().toString());
                writeJson(exchange, 200, """
                        {
                          "destinationEntities":[
                            {"theCode":"DA22","title":"胃食管反流病","score":0.91},
                            {"theCode":"DD90","title":"胃炎","score":0.52}
                          ]
                        }
                        """);
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            PaperCaseIcd11Service service = new PaperCaseIcd11Service();
            setField(service, "provider", "who-remote");
            setField(service, "baseUrl", base);
            setField(service, "tokenUrl", base + "/connect/token");
            setField(service, "clientId", "client-id");
            setField(service, "clientSecret", "client-secret");
            setField(service, "releaseId", "2026-01");
            setField(service, "linearization", "mms");
            setField(service, "language", "zh");

            PaperCaseIcdMatchResult result = service.matchDiagnosis("胃食管反流病");

            assertNotNull(result);
            assertEquals("DA22", result.getCode());
            assertEquals("胃食管反流病", result.getTitle());
            assertEquals(0.95, result.getConfidence());
            assertEquals(2, result.getCandidates().size());
            assertEquals("DA22", result.getCandidates().get(0).getCode());
            assertEquals("autocode", result.getCandidates().get(0).getSource());
            assertEquals(1, tokenCalls.get());
            assertTrue(tokenAuthorization.get().startsWith("Basic "));
            assertTrue(tokenBody.get().contains("grant_type=client_credentials"));
            assertEquals("Bearer token-123", searchAuthorization.get());
            assertEquals("v2", apiVersionHeader.get());
            assertEquals("zh", acceptLanguageHeader.get());
            assertTrue(autoCodePath.get().contains("/autocode?searchText="));
            assertTrue(searchPath.get().contains("/search?q="));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldSkipCodingWhenCredentialsMissing() {
        PaperCaseIcd11Service service = new PaperCaseIcd11Service();
        PaperCaseIcdMatchResult result = service.matchDiagnosis("胃食管反流病");

        assertNotNull(result);
        assertEquals("DA22", result.getCode());
        assertEquals("pending_review", result.getCodingStatus());
        assertEquals("local-mapping", result.getCodingSource());
        assertTrue(result.getCandidates().size() >= 1);
    }

    @Test
    void shouldMatchByLocalMappingWithPendingReviewStatus() throws Exception {
        PaperCaseIcd11Service service = new PaperCaseIcd11Service();
        setField(service, "provider", "local-mapping");
        setField(service, "localTopK", 3);
        setField(service, "localMappingResource",
                new org.springframework.core.io.ClassPathResource("icd11/icd11-local-mapping-sample.csv"));
        service.reloadLocalMappings();

        PaperCaseIcdMatchResult result = service.matchDiagnosis("患者诊断为高血压合并2型糖尿病");

        assertNotNull(result);
        assertEquals("pending_review", result.getCodingStatus());
        assertEquals("local-mapping", result.getCodingSource());
        assertTrue(result.getCandidates().size() >= 2);
        assertTrue(result.getCandidates().stream().anyMatch(item -> "BA00".equals(item.getCode())));
        assertTrue(result.getCandidates().stream().anyMatch(item -> "5A11".equals(item.getCode())));
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
