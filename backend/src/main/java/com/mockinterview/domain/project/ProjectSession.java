package com.mockinterview.domain.project;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="project_session",uniqueConstraints=@UniqueConstraint(columnNames="request_key"))
@Getter @Setter
public class ProjectSession {
    @Id @Column(length=36) private String id;
    @Column(length=36,nullable=false) private String projectId;
    @Column(name="request_key",length=64,nullable=false) private String requestKey;
    @Column(length=36) private String originAttemptId;
    private String template;
    private String status;
    @Column(columnDefinition="LONGTEXT",nullable=false,updatable=false) private String snapshotJson;
    @Column(columnDefinition="LONGTEXT",nullable=false,updatable=false) private String prompt;
    private Instant createdAt;
}
