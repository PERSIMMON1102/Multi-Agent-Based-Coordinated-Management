package com.medical.agent.web.controller;

import com.medical.agent.service.VideoTranscriptService;
import com.medical.agent.web.security.AccessGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/video-transcripts")
@PreAuthorize("hasRole('ADMIN')")
public class VideoTranscriptAdminController {

    private final VideoTranscriptService videoTranscriptService;
    private final AccessGuard accessGuard;

    public VideoTranscriptAdminController(VideoTranscriptService videoTranscriptService,
                                          AccessGuard accessGuard) {
        this.videoTranscriptService = videoTranscriptService;
        this.accessGuard = accessGuard;
    }

    @PostMapping("/reindex")
    public ResponseEntity<Map<String, Object>> reindexAll(@RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(videoTranscriptService.reindexAll(dryRun));
    }
}
