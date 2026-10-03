package com.mockinterview.domain.dto;

import com.mockinterview.domain.ResumeStatus;
import com.mockinterview.domain.ResumeStructured;

public record ResumeStatusResponse(Long resumeId, ResumeStatus status, ResumeStructured parsed,
                                   String errorMessage, String extractionMethod) {
    public ResumeStatusResponse(Long id, ResumeStatus status, ResumeStructured parsed) {
        this(id, status, parsed, null, null);
    }
}
