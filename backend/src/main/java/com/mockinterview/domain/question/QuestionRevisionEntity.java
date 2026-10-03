package com.mockinterview.domain.question;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "training_question_revision", uniqueConstraints =
        @UniqueConstraint(name = "uk_training_question_revision", columnNames = {"question_id", "revision_number"}))
@Getter @Setter @NoArgsConstructor
public class QuestionRevisionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private QuestionEntity question;
    @Column(name = "revision_number", nullable = false) private int version;
    @Column(nullable = false) private int rubricVersion;
    @Column(nullable = false, length = 64) private String contentHash;
    @Column(nullable = false, length = 64) private String rubricHash;
    @Lob @Column(nullable = false, columnDefinition = "LONGTEXT") private String snapshotJson;
    @Column(nullable = false) private Instant createdAt;
}
