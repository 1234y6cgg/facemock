package com.mockinterview.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_message")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    private MessageRole role;

    @Lob
    private String content;

    private String layer;

    @Column(name = "question_idx")
    private Integer questionIdx;

    private LocalDateTime createdAt;
}
