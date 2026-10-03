package com.mockinterview.domain.dto;

public record CreateInterviewRequest(Long resumeId,
                                      String targetRole,
                                      String jobResponsibilities,
                                      String jobRequirements,
                                      String mode) {
}
