package com.mockinterview.service.practice;
import com.mockinterview.capability.evaluation.TrainingEvaluation;
import com.mockinterview.domain.question.QuestionCatalog;
import com.mockinterview.capability.speech.OralFeedback;
import com.mockinterview.service.question.QuestionService.PublicQuestion;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class PracticeDtos {
    private PracticeDtos() {}
    public record CreateSession(@NotBlank @Size(max=100) String questionId,
        @Min(1) Integer questionVersion,
        @NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{8,64}") String clientRequestId) {}
    public record SubmitAnswer(@NotBlank @Size(max=10000) String answer,
        @Size(max=36) String parentAttemptId,
        @NotBlank @Pattern(regexp="TEXT") String inputMode,
        @NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{8,64}") String clientRequestId) {}
    public record RequestKey(@NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{8,64}") String clientRequestId) {}
    public record EvaluationView(String id,int generation,String status,String errorCode,String errorMessage,
        String modelName,String promptVersion,Long durationMs,Instant createdAt,Instant finishedAt,
        String referenceState,String referenceNotice,TrainingEvaluation result,List<SourceView> sources,Map<String,String> criterionLabels) {}
    public record AttemptView(String id,String sessionId,String clientRequestId,int attemptNumber,String answer,String parentAttemptId,
        String inputMode,boolean referenceViewed,boolean hintsUsed,Instant createdAt,
        EvaluationView evaluation,List<EvaluationView> evaluations,Comparison comparison,OralFeedback oralFeedback) {}
    public record SessionView(String id,String kind,String originAttemptId,String originSessionId,String status,
        PublicQuestion question,boolean referenceViewed,boolean hintsUsed,Instant createdAt,Instant endedAt,
        List<AttemptView> attempts,String reviewPointId,java.time.LocalDate reviewDueDate) {}
    public record HistoryItem(String id,String title,String kind,String status,int attemptCount,
        String evaluationStatus,Instant createdAt) {}
    public record HistoryPage(List<HistoryItem> items,int page,int size,long totalElements,int totalPages) {}
    public record CriterionChange(String criterionId,String label,String before,String after,String change) {}
    public record Comparison(boolean comparable,String notice,List<CriterionChange> criteria) {}
    public record ReferenceView(String explanation,List<QuestionCatalog.Criterion> criteria,
        List<SourceView> sources,boolean assisted) {}
    public record SourceView(String sourceId,String title,String url,String content,String documentRevision) {}
    public record HintView(String hint) {}
}
