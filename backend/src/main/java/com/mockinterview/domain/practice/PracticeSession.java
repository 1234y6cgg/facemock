package com.mockinterview.domain.practice;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="practice_session", uniqueConstraints=@UniqueConstraint(columnNames="client_request_id"))
@Getter @Setter
public class PracticeSession {
    @Id @Column(length=36) private String id;
    @Column(name="client_request_id", nullable=false, length=64) private String clientRequestId;
    @Column(nullable=false, length=120) private String title;
    @Column(nullable=false, length=120) private String questionId;
    private int questionVersion;
    @Column(nullable=false, columnDefinition="LONGTEXT") private String snapshotJson;
    @Column(nullable=false) private String kind;
    @Column(length=36) private String originAttemptId;
    @Column(nullable=false) private String status;
    private boolean referenceViewed;
    private boolean hintsUsed;
    @Column(nullable=false) private Instant createdAt;
    private Instant endedAt;
    @Column(length=100) private String reviewPointId;
    private java.time.LocalDate reviewDueDate;
}
