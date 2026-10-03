package com.mockinterview.domain.question;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "training_knowledge_point")
@Getter @Setter @NoArgsConstructor
public class KnowledgePointEntity {
    @Id @Column(length = 100) private String id;
    @Column(nullable = false, length = 120) private String title;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private QuestionTopic topic;
    @Column(nullable = false, length = 1000) private String description;
}
