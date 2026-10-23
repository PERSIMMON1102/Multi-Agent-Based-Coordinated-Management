package com.medical.agent.web.controller;

import com.medical.agent.service.HarnessObservabilityService;
import com.medical.agent.web.security.AccessGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/harness")
@PreAuthorize("hasRole('ADMIN')")
public class AdminHarnessController {

    private final HarnessObservabilityService harnessObservabilityService;
    private final AccessGuard accessGuard;

    @Autowired
    public AdminHarnessController(HarnessObservabilityService harnessObservabilityService,
                                  AccessGuard accessGuard) {
        this.harnessObservabilityService = harnessObservabilityService;
        this.accessGuard = accessGuard;
    }

    AdminHarnessController(HarnessObservabilityService harnessObservabilityService) {
        this(harnessObservabilityService, new AccessGuard(null, null, null, null, null, null));
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview(@RequestParam(required = false) Integer days,
                                                           @RequestParam(required = false) Integer traceLimit,
                                                           @RequestParam(required = false) Integer ruleLimit,
                                                           @RequestParam(required = false) Integer messageLimit) {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(harnessObservabilityService.getOverview(days, traceLimit, ruleLimit, messageLimit));
    }
}
