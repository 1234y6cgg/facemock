package com.mockinterview.domain.question;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "training_source_revision", uniqueConstraints = {
        @UniqueConstraint(name = "uk_training_source_version", columnNames = {"source_id", "revision_number"}),
        @UniqueConstraint(name = "uk_training_source_document", columnNames = "document_id")})
@Getter @Setter @NoArgsConstructor
public class QuestionSourceRevisionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "source_id", nullable = false, length = 100) private String sourceId;
    @Column(name = "revision_number", nullable = false) private int version;
    @Column(name = "document_id", nullable = false, length = 100) private String documentId;
    @Column(nullable = false, length = 64) private String contentHash;
    @Lob @Column(nullable = false, columnDefinition = "LONGTEXT") private String snapshotJson;
}
