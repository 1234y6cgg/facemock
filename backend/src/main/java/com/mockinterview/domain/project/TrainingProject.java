package com.mockinterview.domain.project;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="training_project",uniqueConstraints=@UniqueConstraint(columnNames={"resume_id","resume_project_index"}))
@Getter @Setter
public class TrainingProject {
    @Id @Column(length=36) private String id;
    @Column(name="resume_id") private Long resumeId;
    @Column(name="resume_project_index") private Integer resumeProjectIndex;
    @Column(length=120,nullable=false) private String name;
    @Column(columnDefinition="LONGTEXT",nullable=false) private String factsJson;
    @Column(columnDefinition="LONGTEXT") private String extractionJson;
    private int revision;
    private int indexedRevision;
    private String indexStatus;
    private boolean deleted;
    private Instant createdAt;
}
