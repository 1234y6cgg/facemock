package com.mockinterview.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_report")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true)
    private Long sessionId;

    @Lob
    @Column(name = "scores_json", columnDefinition = "LONGTEXT")
    private String scoresJson;

    @Lob
    @Column(name = "weaknesses_json", columnDefinition = "LONGTEXT")
    private String weaknessesJson;

    @Lob
    @Column(name = "suggestions_json", columnDefinition = "LONGTEXT")
    private String suggestionsJson;

    private LocalDateTime createdAt;
}
