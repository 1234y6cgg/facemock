package com.mockinterview.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "resume")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String filename;

    @Lob
    @Column(name = "raw_text")
    private String rawText;

    @Lob
    @Column(name = "parsed_json")
    private String parsedJson;

    @Enumerated(EnumType.STRING)
    private ResumeStatus status;

    private LocalDateTime createdAt;
}
