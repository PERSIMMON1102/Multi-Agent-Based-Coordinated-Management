package com.medical.agent.web.controller;

import com.medical.agent.service.SmartFollowUpService;
import com.medical.agent.web.security.AccessGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/smart-follow-up")
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','PATIENT')")
public class SmartFollowUpController {

    private final SmartFollowUpService smartFollowUpService;
    private final AccessGuard accessGuard;

    @Autowired
    public SmartFollowUpController(SmartFollowUpService smartFollowUpService,
                                   AccessGuard accessGuard) {
        this.smartFollowUpService = smartFollowUpService;
        this.accessGuard = accessGuard;
    }

    SmartFollowUpController(SmartFollowUpService smartFollowUpService) {
        this(smartFollowUpService, new AccessGuard(null, null, null, null, null, null));
    }

    @PostMapping("/generate/{patientId}")
    public ResponseEntity<Map<String, Object>> generateMessage(@PathVariable Long patientId) {
        accessGuard.requirePatientAccess(patientId);
        return ResponseEntity.ok(smartFollowUpService.generateFollowUpMessage(patientId));
    }

    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendMessage(@RequestBody Map<String, Object> request) {
        Long patientId = Long.valueOf(request.get("patientId").toString());
        accessGuard.requirePatientAccess(patientId);
        String message = (String) request.get("message");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> agentSteps = request.get("agentSteps") instanceof List<?>
                ? (List<Map<String, Object>>) request.get("agentSteps")
                : List.of();
        List<String> historyAlerts = request.get("historyAlerts") instanceof List<?> rawAlerts
                ? rawAlerts.stream().map(String::valueOf).toList()
                : List.of();
        return ResponseEntity.ok(smartFollowUpService.sendFollowUpMessage(patientId, message, agentSteps, historyAlerts));
    }

    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> getHistory(@RequestParam(required = false) Long patientId,
                                                                @RequestParam(required = false) Integer limit) {
        if (patientId != null) {
            accessGuard.requirePatientAccess(patientId);
        } else {
            accessGuard.requireDoctorOrAdmin();
        }
        return ResponseEntity.ok(smartFollowUpService.getMessageHistory(patientId, limit));
    }

    @PostMapping("/feedback")
    public ResponseEntity<Map<String, Object>> recordFeedback(@RequestBody Map<String, Object> request) {
        accessGuard.currentUser();
        Long messageId = Long.valueOf(request.get("messageId").toString());
        String feedback = (String) request.get("feedback");
        return ResponseEntity.ok(smartFollowUpService.recordFeedback(messageId, feedback));
    }
}
