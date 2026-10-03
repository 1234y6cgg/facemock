package com.mockinterview.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_session")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resume_id", nullable = false)
    private Long resumeId;

    @Column(name = "target_role")
    private String targetRole;

    @Column(name = "mode")
    private String mode;

    @Column(name = "job_detail", columnDefinition = "LONGTEXT")
    private String jobDetail;

    @Enumerated(EnumType.STRING)
    private InterviewStatus status;

    private String stage;   // Stage 枚举名
    private String layer;   // Layer 枚举名

    @Column(name = "current_project_idx")
    private int currentProjectIdx;

    @Column(name = "total_projects")
    private int totalProjects;

    @Column(name = "question_count")
    private int questionCount;

    @Column(name = "consecutive_stuck")
    private int consecutiveStuck;

    private Integer difficulty;

    @Column(name = "pressure_used")
    private Boolean pressureUsed;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
