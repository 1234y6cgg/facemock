package com.mockinterview.domain.project;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="project_evaluation",uniqueConstraints={@UniqueConstraint(columnNames={"attempt_id","generation"}),@UniqueConstraint(columnNames={"attempt_id","request_key"})})
@Getter @Setter
public class ProjectEvaluation {
    @Id @Column(length=36) private String id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="attempt_id") private ProjectAttempt attempt;
    private int generation;
    @Column(name="request_key",length=64,nullable=false) private String requestKey;
    private String status;
    @Column(columnDefinition="LONGTEXT") private String resultJson;
    private String errorCode;
    private String modelName;
    private String promptVersion;
    private Long durationMs;
    private Instant createdAt;
    private Instant finishedAt;
}
