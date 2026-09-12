package com.mockinterview.domain.dto;

import com.mockinterview.domain.ResumeStatus;
import com.mockinterview.domain.ResumeStructured;

public record ResumeStatusResponse(Long resumeId, ResumeStatus status, ResumeStructured parsed) {
}
