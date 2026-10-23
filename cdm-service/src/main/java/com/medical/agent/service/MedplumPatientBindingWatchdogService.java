package com.medical.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class MedplumPatientBindingWatchdogService {

    private static final Logger log = LoggerFactory.getLogger(MedplumPatientBindingWatchdogService.class);

    private final MedplumService medplumService;

    @Value("${medplum.patient-binding-watchdog.enabled:true}")
    private boolean enabled;

    @Value("${medplum.patient-binding-watchdog.max-patients:50}")
    private int maxPatients;

    public MedplumPatientBindingWatchdogService(MedplumService medplumService) {
        this.medplumService = medplumService;
    }

    @Scheduled(
            initialDelayString = "${medplum.patient-binding-watchdog.initial-delay-ms:120000}",
            fixedDelayString = "${medplum.patient-binding-watchdog.fixed-delay-ms:1800000}"
    )
    public void auditAndRepairBindings() {
        if (!enabled || medplumService == null || !medplumService.isAutoSyncReady()) {
            return;
        }
        try {
            Map<String, Object> result = medplumService.auditAndRepairPatientMappings(true, maxPatients);
            log.info("[MedplumPatientBindingWatchdog] inspected={}, repaired={}, failed={}",
                    result.getOrDefault("inspectedPatients", 0),
                    result.getOrDefault("repairedPatients", 0),
                    result.getOrDefault("failedPatients", 0));
        } catch (RuntimeException ex) {
            log.warn("[MedplumPatientBindingWatchdog] audit failed: {}", ex.getMessage());
        }
    }
}
