package com.mockinterview.service.practice;
import com.mockinterview.capability.evaluation.TrainingEvaluation;
import com.mockinterview.domain.question.QuestionSnapshot;
import com.mockinterview.repository.practice.PracticeEvaluationRepository;
import com.mockinterview.service.question.QuestionJson;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class PracticeEvaluationStore {
    private final PracticeEvaluationRepository evaluations;
    private final QuestionJson json;
    public PracticeEvaluationStore(PracticeEvaluationRepository evaluations,QuestionJson json) { this.evaluations=evaluations; this.json=json; }
    @Transactional(readOnly=true) public List<String> pending() { return evaluations.findTop6ByStatusOrderByCreatedAtAsc("PENDING").stream().map(e->e.getId()).toList(); }
    public Work claim(String id) {
        var e=evaluations.lock(id).orElseThrow();
        if(!"PENDING".equals(e.getStatus())) return null;
        e.setStatus("RUNNING"); e.setStartedAt(Instant.now());
        var a=e.getAttempt();
        return new Work(id,json.read(a.getSession().getSnapshotJson(),QuestionSnapshot.class),a.getAnswer());
    }
    public void context(String id,PracticeReferences.Context context,String model,String prompt) {
        var e=evaluations.lock(id).orElseThrow();
        if(!"RUNNING".equals(e.getStatus())) return;
        e.setContextJson(json.write(context)); e.setModelName(model); e.setPromptVersion(prompt);
    }
    public void success(String id,TrainingEvaluation result,long duration) {
        var e=evaluations.lock(id).orElseThrow();
        if(!"RUNNING".equals(e.getStatus())) return;
        e.setResultJson(json.write(result)); e.setStatus("SUCCEEDED"); e.setDurationMs(duration); e.setFinishedAt(Instant.now());
    }
    public void telemetry(String id,Map<String,Object> metrics){if(metrics==null||metrics.isEmpty())return;evaluations.lock(id).ifPresent(e->e.setTelemetryJson(json.write(metrics)));}
    public void failure(String id,String code,String message,long duration) {
        var e=evaluations.lock(id).orElseThrow();
        if(!"RUNNING".equals(e.getStatus())) return;
        e.setResultJson(null); e.setStatus("FAILED"); e.setErrorCode(code); e.setErrorMessage(message);
        e.setDurationMs(duration); e.setFinishedAt(Instant.now());
    }
    public int recoverInterrupted() {
        var all=evaluations.findByStatus("RUNNING");
        for(var e:all) {
            e.setStatus("FAILED"); e.setResultJson(null); e.setErrorCode("INTERRUPTED");
            e.setErrorMessage("服务重启中断了评估，回答已保留，可以重试。"); e.setFinishedAt(Instant.now());
        }
        return all.size();
    }
    public record Work(String id,QuestionSnapshot snapshot,String answer) {}
}
