package com.mockinterview.domain.project;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="project_attempt",uniqueConstraints={@UniqueConstraint(columnNames={"session_id","request_key"}),@UniqueConstraint(columnNames={"session_id","number"})})
@Getter @Setter
public class ProjectAttempt {
    @Id @Column(length=36) private String id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="session_id") private ProjectSession session;
    @Column(name="request_key",length=64,nullable=false) private String requestKey;
    private int number;
    @Column(length=36,updatable=false) private String parentAttemptId;
    @Column(columnDefinition="LONGTEXT",nullable=false,updatable=false) private String answer;
    private Instant createdAt;
}
