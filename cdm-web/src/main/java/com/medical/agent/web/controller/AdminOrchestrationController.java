package com.medical.agent.web.controller;

import com.medical.agent.service.react.orchestration.OrchestrationWatchdogService;
import com.medical.agent.web.security.AccessGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/orchestration")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrchestrationController {

    private final OrchestrationWatchdogService orchestrationWatchdogService;
    private final AccessGuard accessGuard;

    @Autowired
    public AdminOrchestrationController(OrchestrationWatchdogService orchestrationWatchdogService,
                                        AccessGuard accessGuard) {
        this.orchestrationWatchdogService = orchestrationWatchdogService;
        this.accessGuard = accessGuard;
    }

    AdminOrchestrationController(OrchestrationWatchdogService orchestrationWatchdogService) {
        this(orchestrationWatchdogService, new AccessGuard(null, null, null, null, null, null));
    }

    @GetMapping("/active")
    public ResponseEntity<List<Map<String, Object>>> getActiveTasks() {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(orchestrationWatchdogService.listActiveTasks());
    }

    @GetMapping("/cards")
    public ResponseEntity<List<Map<String, Object>>> getTaskCards(@RequestParam(required = false) String controlStatus,
                                                                  @RequestParam(required = false) String reviewTrigger,
                                                                  @RequestParam(required = false) String taskType,
                                                                  @RequestParam(required = false) Boolean circuitBreakerOnly,
                                                                  @RequestParam(required = false) String contractCompatibility,
                                                                  @RequestParam(required = false) Boolean contractValidated,
                                                                  @RequestParam(required = false) Boolean contractErrorsOnly) {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(orchestrationWatchdogService.listTaskCards(
                controlStatus,
                reviewTrigger,
                taskType,
                circuitBreakerOnly,
                contractCompatibility,
                contractValidated,
                contractErrorsOnly
        ));
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview() {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(orchestrationWatchdogService.getOverview());
    }

    @GetMapping("/filters")
    public ResponseEntity<Map<String, Object>> getFilterOptions() {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(orchestrationWatchdogService.getFilterOptions());
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskDetail(@PathVariable String taskId) {
        accessGuard.requireAdmin();
        return orchestrationWatchdogService.getTaskDetail(taskId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> getLatestSessionTask(@PathVariable String sessionId) {
        accessGuard.requireAdmin();
        return orchestrationWatchdogService.getLatestSessionTask(sessionId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/tick")
    public ResponseEntity<Map<String, Object>> triggerTick() {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(orchestrationWatchdogService.triggerOnce());
    }

    @PostMapping("/{taskId}/advance-now")
    public ResponseEntity<Map<String, Object>> advanceTaskNow(@PathVariable String taskId) {
        accessGuard.requireAdmin();
        return orchestrationWatchdogService.advanceTaskNow(taskId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{taskId}/pause")
    public ResponseEntity<Map<String, Object>> pauseTask(@PathVariable String taskId,
                                                         @RequestParam(required = false) String reason) {
        accessGuard.requireAdmin();
        String operator = accessGuard.currentUser().username();
        return orchestrationWatchdogService.pauseTask(taskId, operator, reason)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{taskId}/resume")
    public ResponseEntity<Map<String, Object>> resumeTask(@PathVariable String taskId,
                                                          @RequestParam(required = false) String reason) {
        accessGuard.requireAdmin();
        String operator = accessGuard.currentUser().username();
        return orchestrationWatchdogService.resumeTask(taskId, operator, reason)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{taskId}/ack-review")
    public ResponseEntity<Map<String, Object>> acknowledgeReview(@PathVariable String taskId,
                                                                 @RequestParam(required = false) String reason) {
        accessGuard.requireAdmin();
        String operator = accessGuard.currentUser().username();
        return orchestrationWatchdogService.acknowledgeReview(taskId, operator, reason)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{taskId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelTask(@PathVariable String taskId,
                                                          @RequestParam(required = false) String reason) {
        accessGuard.requireAdmin();
        String operator = accessGuard.currentUser().username();
        return orchestrationWatchdogService.cancelTask(taskId, operator, reason)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{taskId}/retry")
    public ResponseEntity<Map<String, Object>> retryTask(@PathVariable String taskId,
                                                         @RequestParam(required = false) String reason) {
        accessGuard.requireAdmin();
        String operator = accessGuard.currentUser().username();
        return orchestrationWatchdogService.retryTask(taskId, operator, reason)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{taskId}/subtasks/{subTaskId}/skip")
    public ResponseEntity<Map<String, Object>> skipSubTask(@PathVariable String taskId,
                                                           @PathVariable String subTaskId,
                                                           @RequestParam(required = false) String reason) {
        accessGuard.requireAdmin();
        String operator = accessGuard.currentUser().username();
        return orchestrationWatchdogService.skipSubTask(taskId, subTaskId, operator, reason)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{taskId}/subtasks/{subTaskId}/reset")
    public ResponseEntity<Map<String, Object>> resetSubTask(@PathVariable String taskId,
                                                            @PathVariable String subTaskId,
                                                            @RequestParam(required = false) String reason) {
        accessGuard.requireAdmin();
        String operator = accessGuard.currentUser().username();
        return orchestrationWatchdogService.resetSubTask(taskId, subTaskId, operator, reason)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{taskId}/subtasks/{subTaskId}/handoff")
    public ResponseEntity<Map<String, Object>> handoffSubTask(@PathVariable String taskId,
                                                              @PathVariable String subTaskId,
                                                              @RequestParam String targetAgent,
                                                              @RequestParam(required = false) String reason) {
        accessGuard.requireAdmin();
        String operator = accessGuard.currentUser().username();
        return orchestrationWatchdogService.handoffSubTask(taskId, subTaskId, targetAgent, operator, reason)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{taskId}/subtasks/{subTaskId}/handoff-by-capability")
    public ResponseEntity<Map<String, Object>> handoffSubTaskByCapability(@PathVariable String taskId,
                                                                          @PathVariable String subTaskId,
                                                                          @RequestParam String capability,
                                                                          @RequestParam(required = false) String reason) {
        accessGuard.requireAdmin();
        String operator = accessGuard.currentUser().username();
        return orchestrationWatchdogService.handoffSubTaskByCapability(taskId, subTaskId, capability, operator, reason)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
