package com.mockinterview.service.speech;
import com.mockinterview.repository.practice.*;
import com.mockinterview.repository.speech.*;
import com.mockinterview.service.practice.PracticeConflictException;
import com.mockinterview.controller.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @Transactional
public class PracticeDeletion {
    private final PracticeSessionRepository sessions;private final PracticeAttemptRepository attempts;private final PracticeEvaluationRepository evaluations;
    private final SpeechRecordingRepository recordings;private final TranscriptionJobRepository jobs;private final SpeechStore speech;
    public PracticeDeletion(PracticeSessionRepository sessions,PracticeAttemptRepository attempts,PracticeEvaluationRepository evaluations,
        SpeechRecordingRepository recordings,TranscriptionJobRepository jobs,SpeechStore speech) {this.sessions=sessions;this.attempts=attempts;this.evaluations=evaluations;this.recordings=recordings;this.jobs=jobs;this.speech=speech;}
    public Deleted delete(String id) {
        sessions.lock(id).orElseThrow(()->new NotFoundException("练习不存在"));
        var targets=new LinkedHashSet<String>();targets.add(id);
        boolean added;
        do {added=false;var parentIds=new HashSet<String>();for(var target:targets)for(var a:attempts.lockBySession(target))parentIds.add(a.getId());
            // Locking reads also see children committed before their parent's lock was acquired.
            if(!parentIds.isEmpty())for(var s:sessions.lockChildren(parentIds))if(targets.add(s.getId()))added=true;
        }while(added);
        for(var target:targets)for(var a:attempts.lockBySession(target))
            if(evaluations.findByAttempt_IdOrderByGeneration(a.getId()).stream().anyMatch(e->Set.of("PENDING","RUNNING").contains(e.getStatus())))
                throw new PracticeConflictException("请等待知识评估完成后删除练习");
        var removedRecordings=new ArrayList<String>();var reversed=new ArrayList<>(targets);Collections.reverse(reversed);
        for(var target:reversed) {
            for(var r:recordings.findBySessionIdOrderByCreatedAtAsc(target)){removedRecordings.add(r.getId());speech.delete(r.getId());jobs.deleteAll(jobs.findByRecording_IdOrderByGeneration(r.getId()));recordings.delete(r);}
            for(var a:attempts.lockBySession(target)){evaluations.deleteAll(evaluations.findByAttempt_IdOrderByGeneration(a.getId()));attempts.delete(a);}
            sessions.deleteById(target);
        }
        return new Deleted(List.copyOf(targets),List.copyOf(removedRecordings));
    }
    public record Deleted(List<String> sessionIds,List<String> recordingIds) {}
}
