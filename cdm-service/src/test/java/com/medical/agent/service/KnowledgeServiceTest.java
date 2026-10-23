package com.medical.agent.service;

import com.medical.agent.model.entity.KnowledgeChunk;
import com.medical.agent.model.repository.KnowledgeChunkRepository;
import com.medical.agent.service.security.UploadSecurityService;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeServiceTest {

    @Test
    void shouldSkipDuplicateKnowledgeUpload() {
        AtomicInteger saveAllCalls = new AtomicInteger();
        KnowledgeChunkRepository repository = createRepository(saveAllCalls);
        EmbeddingService embeddingService = new EmbeddingService();
        VectorStoreService vectorStoreService = new VectorStoreService(embeddingService, new RerankService());

        KnowledgeService service = new KnowledgeService(repository, vectorStoreService, embeddingService);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "hypertension.txt",
                "text/plain",
                "# 高血压\n控制血压".getBytes()
        );

        Map<String, Object> result = service.upload(
                file,
                "高血压",
                "高血压-高血压-疾病知识",
                "medical.json",
                "",
                "medical-json",
                "监测指标"
        );

        assertEquals("知识文档已存在，跳过重复导入", result.get("message"));
        assertEquals("SKIPPED", result.get("indexStatus"));
        assertEquals("INDEXED", result.get("existingIndexStatus"));
        assertEquals(1, result.get("chunkCount"));
        assertEquals(1, result.get("indexedChunkCount"));
        assertTrue(Boolean.TRUE.equals(result.get("skipped")));
        assertEquals(0, saveAllCalls.get());
    }

    @Test
    void shouldRejectBinaryKnowledgeUploadBeforeParsing() {
        KnowledgeChunkRepository repository = createRepository(new AtomicInteger());
        EmbeddingService embeddingService = new EmbeddingService();
        VectorStoreService vectorStoreService = new VectorStoreService(embeddingService, new RerankService());
        KnowledgeService service = new KnowledgeService(repository, vectorStoreService, embeddingService, new UploadSecurityService());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "hypertension.txt",
                "text/plain",
                new byte[] { 0x41, 0x00, 0x42 }
        );

        assertThrows(com.medical.agent.common.exception.BusinessException.class, () ->
                service.upload(file, "高血压", "标题", "source", "", "v1", ""));
    }

    private KnowledgeChunkRepository createRepository(AtomicInteger saveAllCalls) {
        Map<String, java.util.function.Function<Object[], Object>> handlers = new LinkedHashMap<>();
        handlers.put("findByDiseaseAndTitleAndSourceAndVersionTagAndPublishedAtOrderByIdAsc", args -> List.of(
                KnowledgeChunk.builder()
                        .id(11L)
                        .disease((String) args[0])
                        .title((String) args[1])
                        .source((String) args[2])
                        .versionTag((String) args[3])
                        .publishedAt((LocalDate) args[4])
                        .content("已有知识块")
                        .contentTags("监测指标")
                        .embeddingStatus("INDEXED")
                        .build()
        ));
        handlers.put("saveAll", args -> {
            saveAllCalls.incrementAndGet();
            return args[0];
        });
        return RepositoryProxyFactory.create(KnowledgeChunkRepository.class, handlers);
    }
}
