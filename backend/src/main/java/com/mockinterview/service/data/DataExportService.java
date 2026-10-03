package com.mockinterview.service.data;
import com.mockinterview.repository.*;
import com.mockinterview.repository.practice.*;
import com.mockinterview.repository.project.*;
import com.mockinterview.repository.speech.*;
import com.mockinterview.service.question.QuestionJson;
import com.mockinterview.service.review.ReviewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
@Service
public class DataExportService {
    private final ResumeRepository resumes;private final InterviewSessionRepository interviews;private final InterviewMessageRepository messages;private final InterviewReportRepository reports;
    private final PracticeSessionRepository sessions;private final PracticeAttemptRepository attempts;private final PracticeEvaluationRepository evaluations;
    private final TrainingProjectRepository projects;private final ProjectRevisionRepository revisions;private final ProjectMaterialRepository materials;
    private final ProjectSessionRepository projectSessions;private final ProjectAttemptRepository projectAttempts;private final ProjectEvaluationRepository projectEvaluations;
    private final SpeechRecordingRepository recordings;private final TranscriptionJobRepository transcriptions;private final ReviewService review;private final QuestionJson json;
    public DataExportService(ResumeRepository resumes,InterviewSessionRepository interviews,InterviewMessageRepository messages,InterviewReportRepository reports,
        PracticeSessionRepository sessions,PracticeAttemptRepository attempts,PracticeEvaluationRepository evaluations,TrainingProjectRepository projects,
        ProjectRevisionRepository revisions,ProjectMaterialRepository materials,ProjectSessionRepository projectSessions,ProjectAttemptRepository projectAttempts,
        ProjectEvaluationRepository projectEvaluations,SpeechRecordingRepository recordings,TranscriptionJobRepository transcriptions,ReviewService review,QuestionJson json){
        this.resumes=resumes;this.interviews=interviews;this.messages=messages;this.reports=reports;this.sessions=sessions;this.attempts=attempts;this.evaluations=evaluations;
        this.projects=projects;this.revisions=revisions;this.materials=materials;this.projectSessions=projectSessions;this.projectAttempts=projectAttempts;this.projectEvaluations=projectEvaluations;
        this.recordings=recordings;this.transcriptions=transcriptions;this.review=review;this.json=json;
    }
    private Map<String,Object> row(Object... pairs){var out=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)out.put((String)pairs[i],pairs[i+1]);return out;}
    @Transactional public String export(){
        var data=row("schemaVersion",1,"exportedAt",Instant.now(),"profile","personal","scope","个人单人部署；含简历文件、文字与快照。录音二进制不包含，需另备份 speech_recordings 数据卷。",
            "review",review.overview());
        data.put("resumes",resumes.findAll().stream().map(r->row("id",r.getId(),"filename",r.getFilename(),"rawText",r.getRawText(),"parsedJson",r.getParsedJson(),"status",r.getStatus(),"createdAt",r.getCreatedAt(),"contentType",r.getContentType(),"fileBase64",r.getFileData())).toList());
        data.put("interviews",interviews.findAll());data.put("interviewMessages",messages.findAll());data.put("interviewReports",reports.findAll());
        data.put("practiceSessions",sessions.findAll().stream().map(s->row("id",s.getId(),"questionId",s.getQuestionId(),"questionVersion",s.getQuestionVersion(),"kind",s.getKind(),"snapshotJson",s.getSnapshotJson(),"originAttemptId",s.getOriginAttemptId(),"status",s.getStatus(),"hintsUsed",s.isHintsUsed(),"referenceViewed",s.isReferenceViewed(),"createdAt",s.getCreatedAt(),"endedAt",s.getEndedAt(),"reviewPointId",s.getReviewPointId(),"reviewDueDate",s.getReviewDueDate())).toList());
        data.put("practiceAttempts",attempts.findAll().stream().map(a->row("id",a.getId(),"sessionId",a.getSession().getId(),"number",a.getAttemptNumber(),"answer",a.getAnswer(),"parentAttemptId",a.getParentAttemptId(),"inputMode",a.getInputMode(),"hintsUsed",a.isHintsUsed(),"referenceViewed",a.isReferenceViewed(),"createdAt",a.getCreatedAt(),"speechDurationMs",a.getSpeechDurationMs())).toList());
        data.put("practiceEvaluations",evaluations.findAll().stream().map(e->row("id",e.getId(),"attemptId",e.getAttempt().getId(),"generation",e.getGeneration(),"status",e.getStatus(),"resultJson",e.getResultJson(),"contextJson",e.getContextJson(),"errorCode",e.getErrorCode(),"modelName",e.getModelName(),"promptVersion",e.getPromptVersion(),"durationMs",e.getDurationMs(),"telemetryJson",e.getTelemetryJson(),"createdAt",e.getCreatedAt(),"finishedAt",e.getFinishedAt())).toList());
        data.put("projects",projects.findAll());data.put("projectRevisions",revisions.findAll());data.put("projectMaterials",materials.findAll());data.put("projectSessions",projectSessions.findAll());
        data.put("projectAttempts",projectAttempts.findAll().stream().map(a->row("id",a.getId(),"sessionId",a.getSession().getId(),"number",a.getNumber(),"answer",a.getAnswer(),"parentAttemptId",a.getParentAttemptId(),"createdAt",a.getCreatedAt())).toList());
        data.put("projectEvaluations",projectEvaluations.findAll().stream().map(e->row("id",e.getId(),"attemptId",e.getAttempt().getId(),"generation",e.getGeneration(),"status",e.getStatus(),"resultJson",e.getResultJson(),"errorCode",e.getErrorCode(),"modelName",e.getModelName(),"promptVersion",e.getPromptVersion(),"durationMs",e.getDurationMs(),"createdAt",e.getCreatedAt(),"finishedAt",e.getFinishedAt())).toList());
        data.put("recordings",recordings.findAll());data.put("transcriptions",transcriptions.findAll().stream().map(t->row("id",t.getId(),"recordingId",t.getRecording().getId(),"generation",t.getGeneration(),"status",t.getStatus(),"originalText",t.getOriginalText(),"errorCode",t.getErrorCode(),"createdAt",t.getCreatedAt(),"finishedAt",t.getFinishedAt())).toList());
        return json.write(data);
    }
}
