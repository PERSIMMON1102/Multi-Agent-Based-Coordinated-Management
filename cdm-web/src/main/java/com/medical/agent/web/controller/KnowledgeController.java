package com.medical.agent.web.controller;

import com.medical.agent.service.KnowledgeService;
import com.medical.agent.service.VectorStoreService;
import com.medical.agent.web.security.AccessGuard;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','PATIENT')")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final VectorStoreService vectorStoreService;
    private final AccessGuard accessGuard;

    public KnowledgeController(KnowledgeService knowledgeService,
                               VectorStoreService vectorStoreService,
                               AccessGuard accessGuard) {
        this.knowledgeService = knowledgeService;
        this.vectorStoreService = vectorStoreService;
        this.accessGuard = accessGuard;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                      @RequestParam(value = "disease", required = false) String disease,
                                                      @RequestParam(value = "title", required = false) String title,
                                                      @RequestParam(value = "source", required = false) String source,
                                                      @RequestParam(value = "publishedAt", required = false) String publishedAt,
                                                      @RequestParam(value = "versionTag", required = false) String versionTag,
                                                      @RequestParam(value = "contentTags", required = false) String contentTags) {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(knowledgeService.upload(file, disease, title, source, publishedAt, versionTag, contentTags));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> search(@RequestParam(value = "disease", required = false) String disease,
                                                            @RequestParam(value = "keyword", required = false) String keyword,
                                                            @RequestParam(value = "limit", required = false) Integer limit) {
        return ResponseEntity.ok(knowledgeService.search(disease, keyword, limit));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        accessGuard.currentUser();
        return ResponseEntity.ok(vectorStoreService.getStatus());
    }

    @PostMapping("/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> reindex() {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(knowledgeService.reindexAll());
    }

    @PostMapping("/evaluate-rag")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> evaluateRag(@RequestBody List<Map<String, Object>> cases) {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(knowledgeService.evaluateRagRegression(cases));
    }
}
