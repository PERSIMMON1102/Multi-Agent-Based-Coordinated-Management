package com.medical.agent.web.controller;

import com.medical.agent.service.DoctorDashboardService;
import com.medical.agent.web.security.AccessGuard;
import com.medical.agent.web.websocket.OnlinePresenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/doctor-dashboard")
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
public class DoctorDashboardController {

    private final DoctorDashboardService doctorDashboardService;
    private final AccessGuard accessGuard;
    private final OnlinePresenceService onlinePresenceService;

    public DoctorDashboardController(DoctorDashboardService doctorDashboardService,
                                     AccessGuard accessGuard,
                                     OnlinePresenceService onlinePresenceService) {
        this.doctorDashboardService = doctorDashboardService;
        this.accessGuard = accessGuard;
        this.onlinePresenceService = onlinePresenceService;
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview(@RequestParam(required = false) Long doctorId) {
        Map<String, Object> response = new LinkedHashMap<>(doctorDashboardService.getOverview(accessGuard.resolveDoctorIdOrCurrent(doctorId)));
        response.put("onlinePresence", onlinePresenceService.getSnapshot());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/presence")
    public ResponseEntity<Map<String, Object>> presence() {
        return ResponseEntity.ok(onlinePresenceService.getSnapshot());
    }
}
