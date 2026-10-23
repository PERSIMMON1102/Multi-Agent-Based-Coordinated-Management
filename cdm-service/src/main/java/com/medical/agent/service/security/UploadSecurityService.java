package com.medical.agent.service.security;

import com.medical.agent.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Service
public class UploadSecurityService {

    private static final long PAPER_CASE_MAX_BYTES = 20L * 1024 * 1024;
    private static final long DEVICE_JSON_MAX_BYTES = 5L * 1024 * 1024;
    private static final long KNOWLEDGE_TEXT_MAX_BYTES = 5L * 1024 * 1024;
    private static final long AVATAR_IMAGE_MAX_BYTES = 5L * 1024 * 1024;
    private static final long VIDEO_COVER_MAX_BYTES = 10L * 1024 * 1024;
    private static final long VIDEO_ASSET_MAX_BYTES = 512L * 1024 * 1024;

    private static final Set<String> PAPER_CASE_EXTENSIONS = Set.of(".pdf", ".png", ".jpg", ".jpeg");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".webp");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(".mp4", ".webm", ".ogg", ".mov");
    private static final Set<String> KNOWLEDGE_TEXT_EXTENSIONS = Set.of(".txt", ".md", ".markdown");

    private final UploadMalwareScanService uploadMalwareScanService;

    @Autowired
    public UploadSecurityService(UploadMalwareScanService uploadMalwareScanService) {
        this.uploadMalwareScanService = uploadMalwareScanService;
    }

    public UploadSecurityService() {
        this(new UploadMalwareScanService());
    }

    public void validatePaperCaseFile(MultipartFile file, String ext) {
        validateRequired(file, "病历文件");
        uploadMalwareScanService.scan("病历文件", file);
        String normalizedExt = requireAllowedExtension(ext, PAPER_CASE_EXTENSIONS, "病历文件");
        validateMaxSize(file, PAPER_CASE_MAX_BYTES, "病历文件");
        validateMagicHeader(file, normalizedExt, "病历文件");
    }

    public void validateDeviceJsonFile(MultipartFile file) {
        validateRequired(file, "设备 JSON 文件");
        uploadMalwareScanService.scan("设备 JSON 文件", file);
        requireAllowedExtension(extractExtension(file.getOriginalFilename()), Set.of(".json"), "设备 JSON 文件");
        validateMaxSize(file, DEVICE_JSON_MAX_BYTES, "设备 JSON 文件");
        validateJsonPayload(readBytes(file, "设备 JSON 文件"), "设备 JSON 文件");
    }

    public void validateKnowledgeTextFile(MultipartFile file) {
        validateRequired(file, "知识库文件");
        uploadMalwareScanService.scan("知识库文件", file);
        requireAllowedExtension(extractExtension(file.getOriginalFilename()), KNOWLEDGE_TEXT_EXTENSIONS, "知识库文件");
        validateMaxSize(file, KNOWLEDGE_TEXT_MAX_BYTES, "知识库文件");
        validateUtf8TextPayload(readBytes(file, "知识库文件"), "知识库文件");
    }

    public void validateAvatarImage(MultipartFile file) {
        validateRequired(file, "头像文件");
        uploadMalwareScanService.scan("头像文件", file);
        String normalizedExt = requireAllowedExtension(extractExtension(file.getOriginalFilename()), IMAGE_EXTENSIONS, "头像文件");
        validateMaxSize(file, AVATAR_IMAGE_MAX_BYTES, "头像文件");
        validateMagicHeader(file, normalizedExt, "头像文件");
    }

    public void validateVideoAsset(MultipartFile file, boolean coverAsset) {
        validateRequired(file, coverAsset ? "视频封面" : "视频文件");
        String label = coverAsset ? "视频封面" : "视频文件";
        uploadMalwareScanService.scan(label, file);
        String ext = extractExtension(file.getOriginalFilename());
        String normalizedExt = requireAllowedExtension(ext, coverAsset ? IMAGE_EXTENSIONS : VIDEO_EXTENSIONS, label);
        validateMaxSize(file, coverAsset ? VIDEO_COVER_MAX_BYTES : VIDEO_ASSET_MAX_BYTES, label);
        validateMagicHeader(file, normalizedExt, label);
    }

    private void validateRequired(MultipartFile file, String label) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, label + "不能为空");
        }
    }

    private void validateMaxSize(MultipartFile file, long maxBytes, String label) {
        if (file.getSize() > maxBytes) {
            throw new BusinessException(400, label + "超过大小限制");
        }
    }

    private String extractExtension(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            throw new BusinessException(400, "文件缺少扩展名");
        }
        return originalName.substring(originalName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }

    private String requireAllowedExtension(String ext, Set<String> allowed, String label) {
        String normalized = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BusinessException(400, label + "不支持的文件类型: " + normalized);
        }
        return normalized;
    }

    private byte[] readBytes(MultipartFile file, String label) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(400, "读取" + label + "失败: " + ex.getMessage());
        }
    }

    private void validateJsonPayload(byte[] payload, String label) {
        validateUtf8TextPayload(payload, label);
        String trimmed = new String(payload, StandardCharsets.UTF_8).trim();
        if (trimmed.isEmpty() || (!trimmed.startsWith("{") && !trimmed.startsWith("["))) {
            throw new BusinessException(400, label + "内容不是有效 JSON 文本");
        }
    }

    private void validateUtf8TextPayload(byte[] payload, String label) {
        if (payload == null || payload.length == 0) {
            throw new BusinessException(400, label + "内容为空");
        }
        for (byte value : payload) {
            if (value == 0) {
                throw new BusinessException(400, label + "包含疑似二进制内容");
            }
        }
        String text = new String(payload, StandardCharsets.UTF_8)
                .replace("\uFEFF", "")
                .trim();
        if (text.isBlank()) {
            throw new BusinessException(400, label + "内容为空或不可读");
        }
    }

    private void validateMagicHeader(MultipartFile file, String ext, String label) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(16);
            if (!matchesHeader(header, ext)) {
                throw new BusinessException(400, label + "内容与扩展名不匹配");
            }
        } catch (IOException ex) {
            throw new BusinessException(400, "读取" + label + "文件头失败: " + ex.getMessage());
        }
    }

    private boolean matchesHeader(byte[] header, String ext) {
        if (header == null || header.length < 4 || ext == null) {
            return false;
        }
        return switch (ext.toLowerCase(Locale.ROOT)) {
            case ".pdf" -> hasPrefix(header, 0x25, 0x50, 0x44, 0x46);
            case ".png" -> hasPrefix(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case ".jpg", ".jpeg" -> hasPrefix(header, 0xFF, 0xD8, 0xFF);
            case ".webp" -> hasAscii(header, 0, "RIFF") && hasAscii(header, 8, "WEBP");
            case ".webm" -> hasPrefix(header, 0x1A, 0x45, 0xDF, 0xA3);
            case ".ogg" -> hasAscii(header, 0, "OggS");
            case ".mp4", ".mov" -> hasAscii(header, 4, "ftyp");
            default -> false;
        };
    }

    private boolean hasPrefix(byte[] header, int... values) {
        if (header.length < values.length) {
            return false;
        }
        for (int i = 0; i < values.length; i++) {
            if ((header[i] & 0xFF) != values[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean hasAscii(byte[] header, int offset, String ascii) {
        byte[] bytes = ascii.getBytes(StandardCharsets.US_ASCII);
        if (header.length < offset + bytes.length) {
            return false;
        }
        for (int i = 0; i < bytes.length; i++) {
            if (header[offset + i] != bytes[i]) {
                return false;
            }
        }
        return true;
    }
}
