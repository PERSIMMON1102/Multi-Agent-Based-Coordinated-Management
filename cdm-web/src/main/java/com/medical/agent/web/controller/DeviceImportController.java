package com.medical.agent.web.controller;

import com.medical.agent.common.dto.DeviceImportSummaryDTO;
import com.medical.agent.service.device.HuaweiDeviceJsonImportService;
import com.medical.agent.service.security.UploadSecurityService;
import com.medical.agent.web.security.AccessGuard;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/device-imports")
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','PATIENT')")
public class DeviceImportController {

    private final HuaweiDeviceJsonImportService huaweiDeviceJsonImportService;
    private final AccessGuard accessGuard;
    private final UploadSecurityService uploadSecurityService;

    public DeviceImportController(HuaweiDeviceJsonImportService huaweiDeviceJsonImportService,
                                  AccessGuard accessGuard,
                                  UploadSecurityService uploadSecurityService) {
        this.huaweiDeviceJsonImportService = huaweiDeviceJsonImportService;
        this.accessGuard = accessGuard;
        this.uploadSecurityService = uploadSecurityService;
    }

    @PostMapping(value = "/huawei/patient/{patientId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DeviceImportSummaryDTO> importHuaweiJson(@PathVariable Long patientId,
                                                                   @RequestParam("file") MultipartFile file) throws IOException {
        if (accessGuard.currentUser().isPatient()) {
            accessGuard.requirePatientAccess(patientId);
        } else {
            accessGuard.requireDoctorPatientAccess(patientId);
        }
        uploadSecurityService.validateDeviceJsonFile(file);
        return ResponseEntity.ok(huaweiDeviceJsonImportService.importJson(patientId, file.getBytes()));
    }
}
