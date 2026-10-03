package com.mockinterview.service.review;
import java.time.*;
import java.util.*;
import jakarta.validation.constraints.*;
import com.mockinterview.service.question.QuestionService.PublicQuestion;
public final class ReviewDtos {
    private ReviewDtos(){}
    public record Preferences(@NotBlank String timezone,@Min(1) @Max(20) int dailyLimit,
        @NotNull @Size(min=3,max=3) List<@NotNull @Min(1) @Max(90) Integer> intervals,
        @Min(2) @Max(5) int requiredPasses,boolean paused,LocalDate skippedDate,List<String> skippedQuestions) {}
    public record Configure(@NotBlank String timezone,@Min(1) @Max(20) int dailyLimit,
        @NotNull @Size(min=3,max=3) List<@NotNull @Min(1) @Max(90) Integer> intervals,
        @Min(2) @Max(5) int requiredPasses,boolean paused) {}
    public record Start(@NotBlank @Size(max=100) String pointId,
        @NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{8,64}") String clientRequestId) {}
    public record Skip(@NotBlank @Size(max=100) String questionId) {}
    public record Evidence(String evaluationId,String attemptId,String sessionId,String questionId,String status,
        Instant answeredAt,boolean assisted,boolean delayed,boolean passed,String quote) {}
    public record Point(String id,String title,String topic,String state,LocalDate dueDate,String lastStatus,
        int validAnswers,int independentPasses,String latestSessionId,List<Evidence> evidence,int uncertainAnswers) {}
    public record Recommendation(PublicQuestion question,String pointId,String reason,String kind,LocalDate dueDate) {}
    public record Today(LocalDate date,Preferences preferences,int duePoints,List<Recommendation> recommendations) {}
    public record Overview(Preferences preferences,List<Point> points,int uncertainItems,int failedEvaluations,
        int pendingEvaluations,int validAnswers,int independentPasses) {}
}
