package com.mockinterview.domain.question;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "training_question", indexes = @Index(name = "idx_training_question_filter",
        columnList = "active,topic,difficulty"))
@Getter @Setter @NoArgsConstructor
public class QuestionEntity {
    @Id @Column(length = 100) private String id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private QuestionTopic topic;
    @Column(nullable = false) private int difficulty;
    @Column(nullable = false, length = 120) private String title;
    @Column(nullable = false) private boolean active;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_revision_id")
    private QuestionRevisionEntity currentRevision;
}
