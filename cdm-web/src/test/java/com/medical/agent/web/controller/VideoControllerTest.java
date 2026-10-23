package com.medical.agent.web.controller;

import com.medical.agent.service.security.UploadSecurityService;
import com.medical.agent.web.config.OssConfig;
import com.medical.agent.web.security.AccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertThrows;

class VideoControllerTest {

    @Test
    void shouldRejectInvalidVideoSignatureDuringUpload() {
        VideoController controller = new VideoController(
                null,
                null,
                null,
                null,
                new OssConfig(),
                new AccessGuard(null, null, null, null, null, null),
                new UploadSecurityService()
        );

        MockMultipartFile fakeVideo = new MockMultipartFile(
                "file",
                "lesson.mp4",
                "video/mp4",
                "not-a-real-video".getBytes()
        );

        assertThrows(com.medical.agent.common.exception.BusinessException.class,
                () -> controller.upload(fakeVideo, "video"));
    }
}
