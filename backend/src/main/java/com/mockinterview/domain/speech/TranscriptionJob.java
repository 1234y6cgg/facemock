package com.mockinterview.domain.speech;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="transcription_job",uniqueConstraints={@UniqueConstraint(columnNames={"recording_id","generation"}),@UniqueConstraint(columnNames={"recording_id","request_key"})})
@Getter @Setter
public class TranscriptionJob {
    @Id @Column(length=36) private String id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="recording_id") private SpeechRecording recording;
    @Column(nullable=false) private int generation;
    @Column(name="request_key",nullable=false,length=64) private String requestKey;
    @Column(nullable=false) private String status;
    @Column(columnDefinition="LONGTEXT") private String originalText;
    private String provider;
    private String errorCode;
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;
}
