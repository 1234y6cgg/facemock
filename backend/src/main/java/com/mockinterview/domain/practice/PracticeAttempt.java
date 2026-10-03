package com.mockinterview.domain.practice;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="practice_attempt", uniqueConstraints={
    @UniqueConstraint(columnNames={"session_id","client_request_id"}),
    @UniqueConstraint(columnNames={"session_id","attempt_number"})})
@Getter @Setter
public class PracticeAttempt {
    @Id @Column(length=36) private String id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="session_id") private PracticeSession session;
    @Column(name="client_request_id", nullable=false, length=64) private String clientRequestId;
    @Column(name="attempt_number", nullable=false) private int attemptNumber;
    @Column(nullable=false, columnDefinition="LONGTEXT", updatable=false) private String answer;
    @Column(length=36, updatable=false) private String parentAttemptId;
    @Column(nullable=false, updatable=false) private String inputMode;
    @Column(updatable=false) private Long speechDurationMs;
    @Column(updatable=false) private boolean referenceViewed;
    @Column(updatable=false) private boolean hintsUsed;
    @Column(nullable=false, updatable=false) private Instant createdAt;
}
