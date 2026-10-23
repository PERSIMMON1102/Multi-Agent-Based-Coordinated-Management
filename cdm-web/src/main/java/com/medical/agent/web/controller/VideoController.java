package com.medical.agent.web.controller;

import com.aliyun.oss.OSS;
import com.medical.agent.common.dto.VideoResourceDTO;
import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.service.VideoResourceService;
import com.medical.agent.service.VideoTranscriptService;
import com.medical.agent.service.VideoWatchService;
import com.medical.agent.service.security.UploadSecurityService;
import com.medical.agent.web.config.OssConfig;
import com.medical.agent.web.security.AccessGuard;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/videos")
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','PATIENT')")
public class VideoController {

    private static final Set<String> VIDEO_EXTENSIONS = Set.of(".mp4", ".webm", ".ogg", ".mov");
    private static final Set<String> COVER_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp");

    private final VideoResourceService videoResourceService;
    private final VideoTranscriptService videoTranscriptService;
    private final VideoWatchService videoWatchService;
    private final OSS ossClient;
    private final OssConfig ossConfig;
    private final AccessGuard accessGuard;
    private final UploadSecurityService uploadSecurityService;

    public VideoController(VideoResourceService videoResourceService,
                           VideoTranscriptService videoTranscriptService,
                           VideoWatchService videoWatchService,
                           @org.springframework.lang.Nullable OSS ossClient,
                           OssConfig ossConfig,
                           AccessGuard accessGuard,
                           UploadSecurityService uploadSecurityService) {
        this.videoResourceService = videoResourceService;
        this.videoTranscriptService = videoTranscriptService;
        this.videoWatchService = videoWatchService;
        this.ossClient = ossClient;
        this.ossConfig = ossConfig;
        this.accessGuard = accessGuard;
        this.uploadSecurityService = uploadSecurityService;
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<VideoResourceDTO>> listAdmin(@RequestParam(value = "keyword", required = false) String keyword,
                                                            @RequestParam(value = "active", required = false) Boolean active,
                                                            @RequestParam(value = "diseaseType", required = false) String diseaseType,
                                                            @RequestParam(value = "category", required = false) String category) {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(videoResourceService.listAdmin(keyword, active, diseaseType, category));
    }

    @GetMapping("/admin/recommendation-feedback")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getRecommendationFeedback() {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(videoResourceService.getRecommendationFeedbackSummary());
    }

    @GetMapping("/published")
    public ResponseEntity<List<VideoResourceDTO>> listPublished(@RequestParam(value = "diseaseType", required = false) String diseaseType,
                                                                @RequestParam(value = "category", required = false) String category,
                                                                @RequestParam(value = "limit", required = false) Integer limit) {
        accessGuard.currentUser();
        return ResponseEntity.ok(videoResourceService.listPublished(diseaseType, category, limit));
    }

    @GetMapping("/recommendations/{patientId}")
    public ResponseEntity<List<VideoResourceDTO>> getRecommendations(@PathVariable Long patientId,
                                                                     @RequestParam(value = "limit", required = false) Integer limit) {
        accessGuard.requirePatientAccess(patientId);
        return ResponseEntity.ok(videoResourceService.getRecommendedVideos(patientId, limit));
    }

    @PostMapping("/watch/start")
    public ResponseEntity<Map<String, Object>> startWatch(@RequestBody Map<String, Object> body) {
        accessGuard.requirePatientAccess(toLong(body.get("patientId")));
        return ResponseEntity.ok(videoWatchService.startSession(
                toLong(body.get("patientId")),
                toLong(body.get("videoId")),
                body.get("sourceType") != null ? body.get("sourceType").toString() : null,
                toNullableLong(body.get("sourceRefId")),
                toNullableLong(body.get("durationSeconds"))
        ));
    }

    @PutMapping("/watch/{watchSessionId}/progress")
    public ResponseEntity<Map<String, Object>> updateWatchProgress(@PathVariable Long watchSessionId,
                                                                   @RequestBody Map<String, Object> body) {
        accessGuard.requireVideoWatchAccess(watchSessionId);
        return ResponseEntity.ok(videoWatchService.updateProgress(
                watchSessionId,
                toNullableLong(body.get("watchedSeconds")),
                toNullableDouble(body.get("progressPercent"))
        ));
    }

    @PutMapping("/watch/{watchSessionId}/complete")
    public ResponseEntity<Map<String, Object>> completeWatch(@PathVariable Long watchSessionId,
                                                             @RequestBody(required = false) Map<String, Object> body) {
        accessGuard.requireVideoWatchAccess(watchSessionId);
        Map<String, Object> payload = body == null ? Map.of() : body;
        return ResponseEntity.ok(videoWatchService.completeSession(
                watchSessionId,
                toNullableLong(payload.get("watchedSeconds")),
                toNullableDouble(payload.get("progressPercent"))
        ));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VideoResourceDTO> create(@RequestBody Map<String, Object> body) {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(videoResourceService.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VideoResourceDTO> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        accessGuard.requireAdmin();
        VideoResourceDTO before = videoResourceService.getById(id);
        VideoResourceDTO after = videoResourceService.update(id, body);
        recycleReplacedAssets(before, after);
        return ResponseEntity.ok(after);
    }

    @GetMapping("/{id}/transcript")
    public ResponseEntity<Map<String, Object>> getTranscript(@PathVariable Long id) {
        accessGuard.currentUser();
        return ResponseEntity.ok(videoTranscriptService.getTranscript(id));
    }

    @PostMapping("/{id}/transcript")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> saveTranscript(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(videoTranscriptService.saveTranscript(
                id,
                body.get("content") != null ? body.get("content").toString() : null,
                body.get("sourceType") != null ? body.get("sourceType").toString() : null
        ));
    }

    @PostMapping("/transcripts/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> reindexAllTranscripts(@RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        accessGuard.requireAdmin();
        return ResponseEntity.ok(videoTranscriptService.reindexAll(dryRun));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        accessGuard.requireAdmin();
        VideoResourceDTO before = videoResourceService.getById(id);
        videoTranscriptService.deleteTranscript(id);
        Map<String, Object> result = new LinkedHashMap<>(videoResourceService.delete(id));
        List<String> recycled = recycleDeletedAssets(before);
        if (recycled.isEmpty()) {
            result.put("message", "视频已删除，相关文件因历史报告引用或外部地址配置而保留");
        } else {
            result.put("message", "视频已删除，并回收文件：" + String.join("、", recycled));
        }
        result.put("recycledAssets", recycled);
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                      @RequestParam(value = "kind", defaultValue = "video") String kind) {
        accessGuard.requireAdmin();
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "上传文件不能为空");
        }

        String normalizedKind = normalizeKind(kind);
        String ext = extractExtension(file.getOriginalFilename());
        validateExtension(normalizedKind, ext);
        uploadSecurityService.validateVideoAsset(file, "cover".equals(normalizedKind));

        String directory = "cover".equals(normalizedKind) ? "video-covers" : "videos";
        String fileName = UUID.randomUUID() + ext;
        String objectKey = directory + "/" + fileName;
        String url = ossClient != null
                ? uploadToOss(file, objectKey)
                : saveToLocal(file, directory, fileName);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", url);
        result.put("objectKey", objectKey);
        result.put("sourceType", ossClient != null ? "UPLOAD" : "LOCAL");
        result.put("kind", normalizedKind);
        result.put("originalName", file.getOriginalFilename());
        return ResponseEntity.ok(result);
    }

    private String normalizeKind(String kind) {
        return "cover".equalsIgnoreCase(kind) ? "cover" : "video";
    }

    private void validateExtension(String kind, String ext) {
        Set<String> allowed = "cover".equals(kind) ? COVER_EXTENSIONS : VIDEO_EXTENSIONS;
        if (!allowed.contains(ext.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(400, "不支持的文件类型: " + ext);
        }
    }

    private String extractExtension(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            throw new BusinessException(400, "文件缺少扩展名");
        }
        return originalName.substring(originalName.lastIndexOf('.'));
    }

    private String uploadToOss(MultipartFile file, String objectKey) {
        try (InputStream inputStream = file.getInputStream()) {
            ossClient.putObject(ossConfig.getBucketName(), objectKey, inputStream);
            if (ossConfig.getUrlPrefix() != null && !ossConfig.getUrlPrefix().isBlank()) {
                return ossConfig.getUrlPrefix() + "/" + objectKey;
            }
            String endpoint = ossConfig.getEndpoint().replace("https://", "").replace("http://", "");
            return "https://" + ossConfig.getBucketName() + "." + endpoint + "/" + objectKey;
        } catch (IOException e) {
            throw new BusinessException(500, "上传文件失败: " + e.getMessage());
        }
    }

    private String saveToLocal(MultipartFile file, String directory, String fileName) {
        try {
            Path uploadRoot = Path.of(System.getProperty("user.dir"), "uploads", directory);
            Files.createDirectories(uploadRoot);
            Path target = uploadRoot.resolve(fileName);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return "/uploads/" + directory + "/" + fileName;
        } catch (IOException e) {
            throw new BusinessException(500, "保存本地文件失败: " + e.getMessage());
        }
    }

    private void recycleReplacedAssets(VideoResourceDTO before, VideoResourceDTO after) {
        if (before == null || after == null) {
            return;
        }
        if (!sameText(before.getObjectKey(), after.getObjectKey())) {
            recycleAsset(before.getObjectKey(), before.getVideoUrl(), true);
        }
        if (!sameText(before.getCoverObjectKey(), after.getCoverObjectKey())) {
            recycleAsset(before.getCoverObjectKey(), before.getCoverUrl(), false);
        }
    }

    private List<String> recycleDeletedAssets(VideoResourceDTO deleted) {
        List<String> recycled = new java.util.ArrayList<>();
        if (recycleAsset(deleted.getObjectKey(), deleted.getVideoUrl(), true)) {
            recycled.add("视频文件");
        }
        if (recycleAsset(deleted.getCoverObjectKey(), deleted.getCoverUrl(), false)) {
            recycled.add("封面文件");
        }
        return recycled;
    }

    private boolean recycleAsset(String objectKey, String url, boolean videoAsset) {
        if (objectKey == null || objectKey.isBlank()) {
            return false;
        }
        boolean canRecycle = videoAsset
                ? videoResourceService.canRecycleVideoAsset(objectKey)
                : videoResourceService.canRecycleCoverAsset(objectKey);
        if (!canRecycle) {
            return false;
        }

        if (url != null && url.startsWith("/uploads/")) {
            Path uploadRoot = Path.of(System.getProperty("user.dir"), "uploads").toAbsolutePath().normalize();
            Path target = uploadRoot.resolve(objectKey).normalize();
            if (!target.startsWith(uploadRoot)) {
                return false;
            }
            try {
                return Files.deleteIfExists(target);
            } catch (IOException ignored) {
                return false;
            }
        }

        if (ossClient != null) {
            try {
                ossClient.deleteObject(ossConfig.getBucketName(), objectKey);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }

        return false;
    }

    private boolean sameText(String left, String right) {
        if (left == null || left.isBlank()) {
            return right == null || right.isBlank();
        }
        return left.equals(right);
    }

    private Long toLong(Object value) {
        Long parsed = toNullableLong(value);
        if (parsed == null) {
            throw new BusinessException(400, "缺少必要参数");
        }
        return parsed;
    }

    private Long toNullableLong(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "参数格式错误");
        }
    }

    private Double toNullableDouble(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "参数格式错误");
        }
    }
}
