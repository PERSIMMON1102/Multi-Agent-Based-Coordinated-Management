package com.medical.agent.web.controller;

import com.medical.agent.service.ReferenceDetailService;
import com.medical.agent.web.security.AccessGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/references")
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','PATIENT')")
public class ReferenceController {

    private final ReferenceDetailService referenceDetailService;
    private final AccessGuard accessGuard;

    public ReferenceController(ReferenceDetailService referenceDetailService,
                               AccessGuard accessGuard) {
        this.referenceDetailService = referenceDetailService;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/detail")
    public ResponseEntity<Map<String, Object>> getDetail(@RequestParam("sourceType") String sourceType,
                                                         @RequestParam("refId") Long refId,
                                                         @RequestParam(value = "videoId", required = false) Long videoId) {
        accessGuard.currentUser();
        return ResponseEntity.ok(referenceDetailService.getDetail(sourceType, refId, videoId));
    }
}
