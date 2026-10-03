package com.mockinterview.domain.speech;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="speech_recording",uniqueConstraints={@UniqueConstraint(columnNames={"session_id","client_request_id"}),@UniqueConstraint(columnNames={"session_id","confirmation_key"})})
@Getter @Setter
public class SpeechRecording {
    @Id @Column(length=36) private String id;
    @Column(name="session_id",nullable=false,length=36) private String sessionId;
    @Column(name="client_request_id",nullable=false,length=64) private String clientRequestId;
    @Column(length=36) private String parentAttemptId;
    @Column(nullable=false,length=64) private String audioHash;
    private long durationMs;
    private long byteSize;
    private Instant createdAt;
    private Instant expiresAt;
    @Column(nullable=false) private String state;
    @Column(length=36) private String attemptId;
    @Column(name="confirmation_key",length=64) private String confirmationKey;
}
