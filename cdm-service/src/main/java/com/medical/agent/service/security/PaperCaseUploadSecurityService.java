package com.medical.agent.service.security;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class PaperCaseUploadSecurityService {

    private final UploadSecurityService uploadSecurityService;

    @Autowired
    public PaperCaseUploadSecurityService(UploadSecurityService uploadSecurityService) {
        this.uploadSecurityService = uploadSecurityService;
    }

    public PaperCaseUploadSecurityService() {
        this(new UploadSecurityService());
    }

    public void validatePaperCaseFile(MultipartFile file, String ext) {
        uploadSecurityService.validatePaperCaseFile(file, ext);
    }
}
