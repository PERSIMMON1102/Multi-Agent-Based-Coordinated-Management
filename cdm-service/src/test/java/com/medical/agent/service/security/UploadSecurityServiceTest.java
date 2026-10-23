package com.medical.agent.service.security;

import com.medical.agent.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UploadSecurityServiceTest {

    private final UploadSecurityService uploadSecurityService = new UploadSecurityService();

    @Test
    void shouldRejectAvatarFileWhenMagicHeaderDoesNotMatchImageExtension() {
        MockMultipartFile fakeImage = new MockMultipartFile(
                "file",
                "avatar.jpg",
                "image/jpeg",
                "not-an-image".getBytes()
        );

        assertThrows(BusinessException.class, () -> uploadSecurityService.validateAvatarImage(fakeImage));
    }

    @Test
    void shouldRejectKnowledgeUploadWhenPayloadLooksBinary() {
        MockMultipartFile binaryFile = new MockMultipartFile(
                "file",
                "knowledge.txt",
                "text/plain",
                new byte[] { 0x41, 0x00, 0x42 }
        );

        assertThrows(BusinessException.class, () -> uploadSecurityService.validateKnowledgeTextFile(binaryFile));
    }

    @Test
    void shouldAcceptWellFormedJsonDeviceImportPayload() {
        MockMultipartFile jsonFile = new MockMultipartFile(
                "file",
                "device.json",
                "application/json",
                "{\"device\":\"bp\",\"value\":123}".getBytes()
        );

        assertDoesNotThrow(() -> uploadSecurityService.validateDeviceJsonFile(jsonFile));
    }
}
