package com.mockinterview.service.project;
import com.mockinterview.repository.project.ProjectEvaluationRepository;
import com.mockinterview.service.question.QuestionJson;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static com.mockinterview.service.project.ProjectDtos.*;

@Service @Transactional
public class ProjectEvaluationStore {
    private final ProjectEvaluationRepository evaluations;private final QuestionJson json;
    public ProjectEvaluationStore(ProjectEvaluationRepository evaluations,QuestionJson json){this.evaluations=evaluations;this.json=json;}
    @Transactional(readOnly=true) public List<String> pending(){return evaluations.findTop3ByStatusOrderByCreatedAtAsc("PENDING").stream().map(e->e.getId()).toList();}
    public Work claim(String id){var e=evaluations.lock(id).orElse(null);if(e==null||!"PENDING".equals(e.getStatus()))return null;e.setStatus("RUNNING");var a=e.getAttempt();
        return new Work(id,json.read(a.getSession().getSnapshotJson(),Snapshot.class),a.getSession().getPrompt(),a.getAnswer());}
    public void finish(String id,Assessment result,String error,String model,long elapsed){var e=evaluations.lock(id).orElse(null);if(e==null||!"RUNNING".equals(e.getStatus()))return;
        e.setStatus(error==null?"SUCCEEDED":"FAILED");e.setResultJson(result==null?null:json.write(result));e.setErrorCode(error);e.setModelName(model);e.setPromptVersion(ProjectAssessmentParser.VERSION);e.setDurationMs(elapsed);e.setFinishedAt(Instant.now());}
    public void recover(){for(var e:evaluations.findByStatus("RUNNING")){e.setStatus("FAILED");e.setErrorCode("INTERRUPTED");e.setFinishedAt(Instant.now());}}
    public record Work(String id,Snapshot snapshot,String question,String answer) {}
}
