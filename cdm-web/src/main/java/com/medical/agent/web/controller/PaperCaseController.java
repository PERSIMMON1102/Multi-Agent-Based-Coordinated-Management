package com.medical.agent.web.controller;

import com.medical.agent.common.dto.PaperCaseAccessAuditSummaryDTO;
import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.common.dto.PaperCaseIcdConfirmDTO;
import com.medical.agent.common.dto.PaperCasePatientSummaryDTO;
import com.medical.agent.common.dto.PaperCaseRecordDTO;
import com.medical.agent.service.PaperCaseAccessAuditService;
import com.medical.agent.service.PaperCaseService;
import com.medical.agent.service.security.PaperCaseFileAccess;
import com.medical.agent.web.security.AccessGuard;
import com.medical.agent.web.security.AuthenticatedUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/paper-cases")
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','PATIENT')")
public class PaperCaseController {

    private final PaperCaseService paperCaseService;
    private final AccessGuard accessGuard;
    private final PaperCaseAccessAuditService paperCaseAccessAuditService;

    public PaperCaseController(PaperCaseService paperCaseService,
                               AccessGuard accessGuard,
                               PaperCaseAccessAuditService paperCaseAccessAuditService) {
        this.paperCaseService = paperCaseService;
        this.accessGuard = accessGuard;
        this.paperCaseAccessAuditService = paperCaseAccessAuditService;
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<PaperCaseRecordDTO>> getByPatientId(@PathVariable Long patientId) {
        accessGuard.requirePatientAccess(patientId);
        return ResponseEntity.ok(paperCaseService.getByPatientId(patientId));
    }

    @GetMapping("/patient/{patientId}/summary")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<PaperCasePatientSummaryDTO> getPatientSummary(@PathVariable Long patientId) {
        accessGuard.requireDoctorPatientAccess(patientId);
        return ResponseEntity.ok(paperCaseService.getPatientSummary(patientId));
    }

    @GetMapping("/pending-review")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<List<PaperCaseRecordDTO>> getPendingReviewRecords() {
        accessGuard.requireDoctorOrAdmin();
        if (accessGuard.currentUser().isDoctor()) {
            return ResponseEntity.ok(paperCaseService.getPendingReviewRecordsForDoctor(accessGuard.currentUser().id()));
        }
        return ResponseEntity.ok(paperCaseService.getPendingReviewRecords());
    }

    @GetMapping("/sync-failed")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<List<PaperCaseRecordDTO>> getFailedSyncRecords() {
        accessGuard.requireDoctorOrAdmin();
        if (accessGuard.currentUser().isDoctor()) {
            return ResponseEntity.ok(paperCaseService.getFailedSyncRecordsForDoctor(accessGuard.currentUser().id()));
        }
        return ResponseEntity.ok(paperCaseService.getFailedSyncRecords());
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<PaperCaseAccessAuditSummaryDTO> getAuditSummary(
            @RequestParam(value = "patientId", required = false) Long patientId,
            @RequestParam(value = "paperCaseId", required = false) Long paperCaseId,
            @RequestParam(value = "limit", required = false) Integer limit) {
        accessGuard.requireDoctorOrAdmin();
        if (paperCaseId != null) {
            Long resolvedPatientId = paperCaseService.getPatientIdByPaperCaseId(paperCaseId);
            accessGuard.requireDoctorPatientAccess(resolvedPatientId);
            if (patientId != null && !patientId.equals(resolvedPatientId)) {
                throw new BusinessException(400, "paperCaseId 与 patientId 不匹配");
            }
            patientId = resolvedPatientId;
        } else if (patientId != null) {
            accessGuard.requireDoctorPatientAccess(patientId);
        } else {
            accessGuard.requireAdmin();
        }
        return ResponseEntity.ok(paperCaseAccessAuditService.getAuditSummary(patientId, paperCaseId, limit));
    }

    @GetMapping("/{paperCaseId}/file")
    public ResponseEntity<Resource> downloadPaperCaseFile(@PathVariable Long paperCaseId,
                                                          HttpServletRequest request) {
        Long patientId = paperCaseService.getPatientIdByPaperCaseId(paperCaseId);
        AuthenticatedUser actor = accessGuard.currentUser();
        try {
            accessGuard.requirePatientAccess(patientId);
        } catch (BusinessException ex) {
            paperCaseAccessAuditService.record(
                    paperCaseId,
                    patientId,
                    actor.id(),
                    actor.role(),
                    "download",
                    "denied",
                    "paper_case_file",
                    request == null ? "" : request.getRemoteAddr(),
                    request == null ? "" : request.getHeader(HttpHeaders.USER_AGENT),
                    "paper-case#" + paperCaseId,
                    ex.getMessage(),
                    java.util.Map.of("errorCode", ex.getCode())
            );
            throw ex;
        }
        PaperCaseFileAccess access = paperCaseService.resolveFileAccess(paperCaseId);
        paperCaseAccessAuditService.record(
                paperCaseId,
                patientId,
                actor.id(),
                actor.role(),
                "download",
                "success",
                "paper_case_file",
                request == null ? "" : request.getRemoteAddr(),
                request == null ? "" : request.getHeader(HttpHeaders.USER_AGENT),
                access.downloadFileName(),
                "下载病历原件",
                java.util.Map.of(
                        "mediaType", access.mediaType(),
                        "fileName", access.downloadFileName()
                )
        );
        Resource resource = new FileSystemResource(access.filePath());
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(URLEncoder.encode(access.downloadFileName(), StandardCharsets.UTF_8))
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(MediaType.parseMediaType(access.mediaType()))
                .body(resource);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','PATIENT')")
    public ResponseEntity<PaperCaseRecordDTO> uploadAndProcess(
            @RequestParam("patientId") Long patientId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "recordDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate recordDate) {
        if (accessGuard.currentUser().isPatient()) {
            accessGuard.requirePatientAccess(patientId);
        } else {
            accessGuard.requireDoctorPatientAccess(patientId);
        }
        return ResponseEntity.ok(paperCaseService.processUpload(patientId, file, recordDate));
    }

    @PostMapping("/{paperCaseId}/confirm-icd")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<PaperCaseRecordDTO> confirmIcd(@PathVariable Long paperCaseId,
                                                         @RequestBody PaperCaseIcdConfirmDTO request) {
        accessGuard.requireDoctorOrAdmin();
        accessGuard.requireDoctorPatientAccess(paperCaseService.getPatientIdByPaperCaseId(paperCaseId));
        Long reviewerUserId = accessGuard.currentUser().id();
        return ResponseEntity.ok(paperCaseService.confirmIcd(paperCaseId, request, reviewerUserId));
    }
}
