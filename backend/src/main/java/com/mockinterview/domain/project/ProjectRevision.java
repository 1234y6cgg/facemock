package com.mockinterview.domain.project;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="project_revision",uniqueConstraints=@UniqueConstraint(columnNames={"project_id","revision"}))
@Getter @Setter
public class ProjectRevision {
    @Id @Column(length=36) private String id;
    @Column(name="project_id",nullable=false,length=36) private String projectId;
    private int revision;
    @Column(columnDefinition="LONGTEXT",nullable=false,updatable=false) private String snapshotJson;
    private Instant createdAt;
}
