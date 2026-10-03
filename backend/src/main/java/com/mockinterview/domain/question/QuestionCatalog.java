package com.mockinterview.domain.question;

import com.mockinterview.capability.knowledge.KnowledgeDocument;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record QuestionCatalog(
        @Min(1) int catalogVersion,
        @NotEmpty @Size(max = 1500) List<@NotNull @Valid KnowledgePoint> knowledgePoints,
        @NotEmpty @Size(max = 500) List<@NotNull @Valid Source> sources,
        @NotEmpty @Size(max = 500) List<@NotNull @Valid Question> questions) {

    public record KnowledgePoint(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9._-]{0,99}") String id,
            @NotBlank @Size(max = 120) String title,
            @NotNull QuestionTopic topic,
            @NotBlank @Size(max = 1000) String description) {}

    public record Source(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9._-]{0,99}") String id,
            @Min(1) int version,
            @NotNull @PastOrPresent LocalDate verifiedOn,
            @NotNull @Valid KnowledgeDocument document) {}

    public record Criterion(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9._-]{0,99}") String id,
            @NotBlank String knowledgePointId,
            @NotBlank @Size(max = 1000) String expected,
            @NotEmpty @Size(max = 6) List<@NotBlank @Size(max = 600) String> acceptedExpressions,
            @NotEmpty @Size(max = 6) List<@NotBlank @Size(max = 600) String> commonMistakes,
            @NotEmpty @Size(max = 4) List<@NotBlank String> sourceIds) {}

    public record Question(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9._-]{0,99}") String id,
            @Min(1) int version,
            @Min(1) int rubricVersion,
            @NotNull QuestionTopic topic,
            @Min(1) @Max(5) int difficulty,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 1500) String prompt,
            @Min(30) @Max(300) int suggestedSeconds,
            @NotNull Boolean active,
            @NotEmpty @Size(max = 12) List<@NotNull @Valid Criterion> criteria,
            @NotBlank @Size(max = 5000) String explanation,
            @NotEmpty @Size(max = 5) List<@NotBlank @Size(max = 600) String> followups) {}
}
