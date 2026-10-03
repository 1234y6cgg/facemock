package com.mockinterview.capability.evaluation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record TrainingEvaluation(
        @NotBlank String questionId,
        @Min(1) int questionVersion,
        @Min(1) int rubricVersion,
        @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String answerHash,
        @NotEmpty @Size(max = 12) List<@NotNull @Valid CriterionAssessment> criteria,
        @NotNull @Size(max = 6) List<@NotNull @Valid ExpressionFeedback> expressionFeedback) {

    public record CriterionAssessment(
            @NotBlank String criterionId,
            @NotNull CriterionStatus status,
            @NotNull @Size(max = 5) List<@NotBlank @Size(max = 3000) String> candidateQuotes,
            @NotBlank @Size(max = 1500) String reason,
            @NotNull @Size(max = 4) List<@NotNull @Valid SourceCitation> references) {}

    public record SourceCitation(
            @NotBlank String sourceId,
            @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String documentRevision,
            @NotBlank @Size(max = 1500) String quote) {}

    public record ExpressionFeedback(
            @NotBlank @Size(max = 3000) String quote,
            @NotBlank @Size(max = 1000) String issue,
            @NotBlank @Size(max = 1000) String suggestion) {}
}
