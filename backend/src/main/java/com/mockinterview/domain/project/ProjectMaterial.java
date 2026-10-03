package com.mockinterview.domain.project;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="project_material",uniqueConstraints=@UniqueConstraint(columnNames={"project_id","material_key","version"}))
@Getter @Setter
public class ProjectMaterial {
    @Id @Column(length=36) private String id;
    @Column(name="project_id",nullable=false,length=36) private String projectId;
    @Column(name="material_key",nullable=false,length=36) private String materialKey;
    private int version;
    private String title;
    @Column(length=500) private String origin;
    @Column(columnDefinition="LONGTEXT",nullable=false) private String content;
    private boolean deleted;
    private Instant createdAt;
}
