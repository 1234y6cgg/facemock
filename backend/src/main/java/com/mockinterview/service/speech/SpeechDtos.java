package com.mockinterview.service.speech;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class SpeechDtos {
    private SpeechDtos() {}
    public record Confirm(@NotBlank @Size(max=10000) String text,@NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{8,64}") String clientRequestId) {}
    public record Status(boolean configured,String provider,int maxSeconds,int retentionDays,String notice) {}
    public record JobView(String id,int generation,String status,String originalText,String errorCode,Instant createdAt,Instant finishedAt) {}
    public record RecordingView(String id,String sessionId,String clientRequestId,String parentAttemptId,String state,long durationMs,long byteSize,
        Instant expiresAt,String attemptId,List<JobView> jobs) {}
}
