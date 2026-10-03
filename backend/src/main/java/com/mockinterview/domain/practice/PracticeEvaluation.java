package com.mockinterview.domain.practice;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="practice_evaluation", uniqueConstraints={
    @UniqueConstraint(columnNames={"attempt_id","generation"}),
    @UniqueConstraint(columnNames={"attempt_id","request_key"})},
    indexes=@Index(name="idx_practice_evaluation_status",columnList="status,created_at"))
@Getter @Setter
public class PracticeEvaluation {
    @Id @Column(length=36) private String id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="attempt_id") private PracticeAttempt attempt;
    @Column(nullable=false) private int generation;
    @Column(name="request_key", nullable=false, length=64) private String requestKey;
    @Column(nullable=false) private String status;
    @Column(columnDefinition="LONGTEXT") private String contextJson;
    @Column(columnDefinition="LONGTEXT") private String resultJson;
    private String errorCode;
    private String errorMessage;
    private String modelName;
    private String promptVersion;
    private Long durationMs;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;
    @Column(columnDefinition="LONGTEXT") private String telemetryJson;
}
