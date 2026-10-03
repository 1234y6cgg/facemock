package com.mockinterview.domain.question;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "training_rubric_criterion", uniqueConstraints =
        @UniqueConstraint(name = "uk_training_rubric_criterion", columnNames = {"revision_id", "criterion_id"}))
@Getter @Setter @NoArgsConstructor
public class RubricCriterionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revision_id", nullable = false)
    private QuestionRevisionEntity revision;
    @Column(name = "criterion_id", nullable = false, length = 100) private String criterionId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "knowledge_point_id", nullable = false)
    private KnowledgePointEntity knowledgePoint;
    @Lob @Column(nullable = false, columnDefinition = "LONGTEXT") private String definitionJson;
}
