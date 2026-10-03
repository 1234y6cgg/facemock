package com.mockinterview.service.speech;
import com.mockinterview.repository.speech.*;
import com.mockinterview.capability.speech.SpeechFileStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @Transactional
public class TranscriptionStore {
    private final TranscriptionJobRepository jobs;
    private final SpeechRecordingRepository recordings;
    private final SpeechFileStore files;
    public TranscriptionStore(TranscriptionJobRepository jobs,SpeechRecordingRepository recordings,SpeechFileStore files) { this.jobs=jobs;this.recordings=recordings;this.files=files; }
    @Transactional(readOnly=true) public List<String> pending() { return jobs.findTop3ByStatusOrderByCreatedAtAsc("PENDING").stream().map(j->j.getId()).toList(); }
    public Work claim(String id) {
        var base=jobs.findById(id).orElse(null);if(base==null)return null;
        var r=recordings.lock(base.getRecording().getId()).orElse(null);if(r==null)return null;
        var j=jobs.lock(id).orElse(null);
        if(j==null||!"PENDING".equals(j.getStatus()))return null;
        if(!"ACTIVE".equals(r.getState())||!r.getExpiresAt().isAfter(Instant.now())) { j.setStatus("EXPIRED");return null; }
        j.setStatus("RUNNING");j.setStartedAt(Instant.now());return new Work(id,r.getId());
    }
    public void finish(String id,String text,String provider,String error) {
        var base=jobs.findById(id).orElse(null);if(base==null)return;
        var r=recordings.lock(base.getRecording().getId()).orElse(null);if(r==null)return;
        var j=jobs.lock(id).orElse(null);
        if(j==null||!"RUNNING".equals(j.getStatus())||!"ACTIVE".equals(r.getState())||!r.getExpiresAt().isAfter(Instant.now()))return;
        j.setProvider(provider);j.setFinishedAt(Instant.now());j.setErrorCode(error);
        j.setStatus(error==null?"SUCCEEDED":"FAILED");j.setOriginalText(error==null?text:null);
    }
    public void recover() { for(var j:jobs.findByStatus("RUNNING")) { j.setStatus("FAILED");j.setErrorCode("INTERRUPTED");j.setFinishedAt(Instant.now()); } }
    public record Work(String jobId,String recordingId) {}
}
