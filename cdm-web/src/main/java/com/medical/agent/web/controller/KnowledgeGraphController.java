package com.medical.agent.web.controller;

import com.medical.agent.service.KnowledgeGraphService;
import com.medical.agent.web.security.AccessGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/knowledge-graph")
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
public class KnowledgeGraphController {

    private final KnowledgeGraphService knowledgeGraphService;
    private final AccessGuard accessGuard;

    public KnowledgeGraphController(KnowledgeGraphService knowledgeGraphService,
                                    AccessGuard accessGuard) {
        this.knowledgeGraphService = knowledgeGraphService;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        accessGuard.requireDoctorOrAdmin();
        return ResponseEntity.ok(knowledgeGraphService.getStatus());
    }

    @GetMapping("/visualization")
    public ResponseEntity<Map<String, Object>> visualization(@org.springframework.web.bind.annotation.RequestParam(value = "nodeType", required = false) String nodeType,
                                                             @org.springframework.web.bind.annotation.RequestParam(value = "keyword", required = false) String keyword,
                                                             @org.springframework.web.bind.annotation.RequestParam(value = "limit", required = false) Integer limit) {
        accessGuard.requireDoctorOrAdmin();
        return ResponseEntity.ok(knowledgeGraphService.getVisualization(nodeType, keyword, limit));
    }

    @PostMapping("/rebuild")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> rebuild() {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(knowledgeGraphService.rebuildGraph());
    }
}
