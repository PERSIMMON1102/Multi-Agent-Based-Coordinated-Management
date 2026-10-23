package com.medical.agent.service.security;

import lombok.Builder;

import java.nio.file.Path;

@Builder
public record PaperCaseFileAccess(Long paperCaseId,
                                  Long patientId,
                                  Path filePath,
                                  String mediaType,
                                  String downloadFileName) {
}
