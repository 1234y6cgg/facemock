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

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
