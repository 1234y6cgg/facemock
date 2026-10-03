package com.mockinterview.service.practice;

import com.mockinterview.capability.evaluation.*;
import com.mockinterview.controller.NotFoundException;
import com.mockinterview.domain.practice.*;
import com.mockinterview.domain.question.*;
import com.mockinterview.repository.practice.*;
import com.mockinterview.service.question.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;
import static com.mockinterview.service.practice.PracticeDtos.*;

@Service
@Transactional
public class PracticeStore {
    private final PracticeSessionRepository sessions;
    private final PracticeAttemptRepository attempts;
    private final PracticeEvaluationRepository evaluations;
    private final QuestionService questions;
    private final QuestionJson json;
    private final TransactionTemplate transaction;
    public PracticeStore(PracticeSessionRepository sessions,PracticeAttemptRepository attempts,
        PracticeEvaluationRepository evaluations,QuestionService questions,QuestionJson json,PlatformTransactionManager manager) {
        this.sessions=sessions; this.attempts=attempts; this.evaluations=evaluations; this.questions=questions; this.json=json;
        this.transaction=new TransactionTemplate(manager);
    }

    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public synchronized SessionView create(CreateSession request) {
        // Keep the single-instance idempotency lock until the transaction actually commits.
        return Objects.requireNonNull(transaction.execute(status->createInTransaction(request)));
    }

    private SessionView createInTransaction(CreateSession request) {
        var existing=sessions.findByClientRequestId(request.clientRequestId());
        if(existing.isPresent()) {
            var s=existing.get();
            if(!"QUESTION".equals(s.getKind()) || !s.getQuestionId().equals(request.questionId())
                || (request.questionVersion()!=null && s.getQuestionVersion()!=request.questionVersion()))
                throw new PracticeConflictException("请求标识已用于另一道题");
            return view(s);
        }
        var q=questions.detail(request.questionId(),request.questionVersion());
        return view(newSession(questions.snapshot(q.id(),q.version()),request.clientRequestId(),"QUESTION",null));
    }

    private PracticeSession newSession(QuestionSnapshot snapshot,String key,String kind,String origin) {
        var s=new PracticeSession();
        s.setId(UUID.randomUUID().toString()); s.setClientRequestId(key); s.setKind(kind); s.setOriginAttemptId(origin);
        s.setQuestionId(snapshot.question().id()); s.setQuestionVersion(snapshot.question().version());
        s.setTitle(snapshot.question().title()); s.setSnapshotJson(json.write(snapshot));
        s.setStatus("ACTIVE"); s.setCreatedAt(Instant.now());
        return sessions.save(s);
    }

    public SessionView createReview(String pointId,String questionId,int version,java.time.LocalDate due,String key) {
        var old=sessions.findByClientRequestId(key);
        if(old.isPresent()){
            var s=old.get();if(!"REVIEW".equals(s.getKind())||!pointId.equals(s.getReviewPointId())||!questionId.equals(s.getQuestionId()))
                throw new PracticeConflictException("请求标识已用于其他练习");return view(s);
        }
        var snap=questions.snapshot(questionId,version);var q=snap.question();
        var alternate=new QuestionCatalog.Question(q.id(),q.version(),q.rubricVersion(),q.topic(),q.difficulty(),q.title(),
            "延迟复测：请换一个具体场景解释，并说明机制和失败边界。\n"+q.prompt(),q.suggestedSeconds(),q.active(),q.criteria(),q.explanation(),q.followups());
        var s=newSession(new QuestionSnapshot(alternate,snap.knowledgePoints(),snap.sources()),key,"REVIEW",null);
        s.setReviewPointId(pointId);s.setReviewDueDate(due);return view(s);
    }

    public AttemptView submit(String sessionId,SubmitAnswer request) {
        return submitInternal(sessionId,request,"TEXT",null);
    }

    public AttemptView submitRecorded(String sessionId,SubmitAnswer request,long durationMs) {
        return submitInternal(sessionId,request,"SPEECH",durationMs);
    }

    private AttemptView submitInternal(String sessionId,SubmitAnswer request,String mode,Long durationMs) {
        var s=lockSession(sessionId);
        var existing=attempts.findBySession_IdAndClientRequestId(sessionId,request.clientRequestId());
        if(existing.isPresent()) {
            var a=existing.get();
            if(!a.getAnswer().equals(request.answer()) || !Objects.equals(a.getParentAttemptId(),request.parentAttemptId())
                || !a.getInputMode().equals(mode) || !Objects.equals(a.getSpeechDurationMs(),durationMs)) throw new PracticeConflictException("相同请求标识的回答内容不一致");
            return attemptView(a);
        }
        requireActive(s);
        if(request.answer()==null || request.answer().isBlank() || request.answer().length()>10000
            || !"TEXT".equals(request.inputMode())) throw new IllegalArgumentException("回答为 1–10000 字的文字");
        var history=attempts.findBySession_IdOrderByAttemptNumber(sessionId);
        if(history.isEmpty()) {
            if(request.parentAttemptId()!=null) throw new PracticeConflictException("首次回答不能引用父回答");
        } else {
            var latest=history.get(history.size()-1);
            if(!latest.getId().equals(request.parentAttemptId())) throw new PracticeConflictException("重答必须引用当前最新回答");
            if(!"SUCCEEDED".equals(latestEvaluation(latest.getId()).getStatus())) throw new PracticeConflictException("当前回答尚未成功评估，请等待或重试");
        }
        var a=new PracticeAttempt();
        a.setId(UUID.randomUUID().toString()); a.setSession(s); a.setAttemptNumber(history.size()+1);
        a.setClientRequestId(request.clientRequestId()); a.setAnswer(request.answer()); a.setParentAttemptId(request.parentAttemptId());
        a.setInputMode(mode); a.setSpeechDurationMs(durationMs); a.setReferenceViewed(s.isReferenceViewed()); a.setHintsUsed(s.isHintsUsed()); a.setCreatedAt(Instant.now());
        attempts.save(a);
        enqueue(a,1,request.clientRequestId());
        return attemptView(a);
    }

    private PracticeEvaluation enqueue(PracticeAttempt a,int generation,String key) {
        var e=new PracticeEvaluation();
        e.setId(UUID.randomUUID().toString()); e.setAttempt(a); e.setGeneration(generation); e.setRequestKey(key);
        e.setStatus("PENDING"); e.setCreatedAt(Instant.now());
        return evaluations.save(e);
    }

    public AttemptView retry(String attemptId,RequestKey request) {
        var a=attempt(attemptId); lockSession(a.getSession().getId());
        var replay=evaluations.findByAttempt_IdAndRequestKey(attemptId,request.clientRequestId());
        if(replay.isPresent()) return attemptView(a);
        requireActive(a.getSession());
        var e=latestEvaluation(attemptId);
        if("PENDING".equals(e.getStatus()) || "RUNNING".equals(e.getStatus())) return attemptView(a);
        if(!"FAILED".equals(e.getStatus())) throw new PracticeConflictException("成功的评估不需要重试");
        if(e.getGeneration()>=3) throw new PracticeConflictException("本次回答已达到三次评估上限，请开始新的练习");
        enqueue(a,e.getGeneration()+1,request.clientRequestId());
        return attemptView(a);
    }

    public ReferenceView reference(String sessionId,boolean assisted) {
        var s=lockSession(sessionId);
        if("REVIEW".equals(s.getKind())&&attempts.findBySession_IdOrderByAttemptNumber(sessionId).isEmpty())
            throw new PracticeConflictException("独立复测首次提交前隐藏提示和解析，可返回题库选择有辅助练习");
        if(attempts.findBySession_IdOrderByAttemptNumber(sessionId).isEmpty() && !assisted)
            throw new PracticeConflictException("请先独立提交回答，或明确选择有辅助练习");
        s.setReferenceViewed(true);
        var snapshot=snapshot(s);
        return new ReferenceView(snapshot.question().explanation(),snapshot.question().criteria(),
            snapshot.sources().stream().map(x->new SourceView(x.sourceId(),x.title(),x.sourceUrl(),x.content(),x.documentRevision())).toList(),true);
    }

    public HintView hint(String sessionId) {
        var s=lockSession(sessionId); requireActive(s);
        if("REVIEW".equals(s.getKind())&&attempts.findBySession_IdOrderByAttemptNumber(sessionId).isEmpty())
            throw new PracticeConflictException("独立复测请先提交回答，再查看提示");
        s.setHintsUsed(true);
        return new HintView("先解释为什么会有这个问题，再说明机制或处理步骤，最后举例说明适用条件和失败边界。");
    }

    public SessionView followup(String attemptId,RequestKey request) {
        var a=attempt(attemptId); lockSession(a.getSession().getId());
        var replay=sessions.findByClientRequestId(request.clientRequestId());
        if(replay.isPresent()) {
            if(!attemptId.equals(replay.get().getOriginAttemptId())) throw new PracticeConflictException("请求标识已用于其他练习");
            return view(replay.get());
        }
        var result=successful(a);
        var snapshot=snapshot(a.getSession()); var q=snapshot.question();
        var target=result.criteria().stream().min(Comparator.comparingInt(x->priority(x.status()))).orElseThrow();
        var criterion=q.criteria().stream().filter(c->c.id().equals(target.criterionId())).findFirst().orElseThrow();
        var point=snapshot.knowledgePoints().stream().filter(p->p.id().equals(criterion.knowledgePointId())).findFirst().orElseThrow();
        var fq=new QuestionCatalog.Question(q.id()+".followup."+criterion.id(),q.version(),q.rubricVersion(),q.topic(),q.difficulty(),
            "专项追问："+q.title(),"有人认为“"+criterion.commonMistakes().get(0)+"”你同意吗？请用一个具体场景说明正确做法和故障边界。",
            90,true,List.of(criterion),criterion.expected(),List.of("请换一个场景再解释一次。"));
        var fs=new QuestionSnapshot(fq,List.of(point),snapshot.sources().stream().filter(x->criterion.sourceIds().contains(x.sourceId())).toList());
        var child=newSession(fs,request.clientRequestId(),"FOLLOWUP",attemptId);
        child.setReferenceViewed(a.getSession().isReferenceViewed()); child.setHintsUsed(a.getSession().isHintsUsed());
        return view(child);
    }

    private int priority(CriterionStatus status) {
        return switch(status) { case INCORRECT->0; case MISSING->1; case PARTIAL->2; case UNCERTAIN->3; case COVERED->4; };
    }

    public SessionView complete(String id) {
        var s=lockSession(id);
        var pending=attempts.findBySession_IdOrderByAttemptNumber(id).stream().map(a->latestEvaluation(a.getId()).getStatus())
            .anyMatch(x->"PENDING".equals(x)||"RUNNING".equals(x));
        if(pending) throw new PracticeConflictException("请等待本次评估完成后结束练习");
        s.setStatus("COMPLETED"); if(s.getEndedAt()==null) s.setEndedAt(Instant.now());
        return view(s);
    }

    @Transactional(readOnly=true) public SessionView get(String id) { return view(session(id)); }
    @Transactional(readOnly=true) public AttemptView getAttempt(String id) { return attemptView(attempt(id)); }
    @Transactional(readOnly=true) public HistoryPage history(int page,int size) {
        if(page<0||page>10000||size<1||size>50) throw new IllegalArgumentException("页码为 0–10000，每页为 1–50");
        var p=sessions.findAllByOrderByCreatedAtDesc(PageRequest.of(page,size));
        return new HistoryPage(p.stream().map(s->{
            var list=attempts.findBySession_IdOrderByAttemptNumber(s.getId());
            return new HistoryItem(s.getId(),s.getTitle(),s.getKind(),s.getStatus(),list.size(),
                list.isEmpty()?null:latestEvaluation(list.get(list.size()-1).getId()).getStatus(),s.getCreatedAt());
        }).toList(),page,size,p.getTotalElements(),p.getTotalPages());
    }

    private SessionView view(PracticeSession s) {
        var q=snapshot(s).question();
        String originSession=s.getOriginAttemptId()==null?null:attempt(s.getOriginAttemptId()).getSession().getId();
        return new SessionView(s.getId(),s.getKind(),s.getOriginAttemptId(),originSession,s.getStatus(),
            new QuestionService.PublicQuestion(q.id(),q.version(),q.topic(),q.difficulty(),q.title(),q.prompt(),q.suggestedSeconds()),
            s.isReferenceViewed(),s.isHintsUsed(),s.getCreatedAt(),s.getEndedAt(),
            attempts.findBySession_IdOrderByAttemptNumber(s.getId()).stream().map(this::attemptView).toList(),s.getReviewPointId(),s.getReviewDueDate());
    }

    private AttemptView attemptView(PracticeAttempt a) {
        var all=evaluations.findByAttempt_IdOrderByGeneration(a.getId());
        var latest=all.get(all.size()-1);
        return new AttemptView(a.getId(),a.getSession().getId(),a.getClientRequestId(),a.getAttemptNumber(),a.getAnswer(),a.getParentAttemptId(),
            a.getInputMode(),a.isReferenceViewed(),a.isHintsUsed(),a.getCreatedAt(),evaluationView(latest),
            all.stream().map(this::evaluationView).toList(),comparison(a,latest),a.getSpeechDurationMs()==null?null:
                com.mockinterview.capability.speech.OralFeedback.describe(a.getAnswer(),a.getSpeechDurationMs(),snapshot(a.getSession()).question().suggestedSeconds()));
    }

    private EvaluationView evaluationView(PracticeEvaluation e) {
        var context=e.getContextJson()==null?null:json.read(e.getContextJson(),PracticeReferences.Context.class);
        var snapshot=snapshot(e.getAttempt().getSession());
        var result=e.getResultJson()==null?null:json.read(e.getResultJson(),TrainingEvaluation.class);
        var labels=new LinkedHashMap<String,String>();
        if(result!=null) for(var c:snapshot.question().criteria()) labels.put(c.id(),snapshot.knowledgePoints().stream()
            .filter(p->p.id().equals(c.knowledgePointId())).map(QuestionCatalog.KnowledgePoint::title).findFirst().orElse(c.id()));
        return new EvaluationView(e.getId(),e.getGeneration(),e.getStatus(),e.getErrorCode(),e.getErrorMessage(),
            e.getModelName(),e.getPromptVersion(),e.getDurationMs(),e.getCreatedAt(),e.getFinishedAt(),
            context==null?null:context.state(),context==null?null:context.notice(),result,
            result==null?List.of():snapshot.sources().stream().map(s->new SourceView(s.sourceId(),s.title(),s.sourceUrl(),"",s.documentRevision())).toList(),labels);
    }

    private Comparison comparison(PracticeAttempt a,PracticeEvaluation latest) {
        if(a.getParentAttemptId()==null) return null;
        var parent=attempt(a.getParentAttemptId()); var before=latestEvaluation(parent.getId());
        if(!"SUCCEEDED".equals(before.getStatus())||!"SUCCEEDED".equals(latest.getStatus()))
            return new Comparison(false,"两次回答均成功评估后，才能比较。",List.of());
        var b=json.read(before.getResultJson(),TrainingEvaluation.class); var n=json.read(latest.getResultJson(),TrainingEvaluation.class);
        if(!b.questionId().equals(n.questionId())||b.questionVersion()!=n.questionVersion()||b.rubricVersion()!=n.rubricVersion())
            return new Comparison(false,"题目或评分版本不同，无法直接比较。",List.of());
        var labels=new HashMap<String,String>();
        var snap=snapshot(a.getSession());
        for(var criterion:snap.question().criteria()) labels.put(criterion.id(),snap.knowledgePoints().stream()
            .filter(p->p.id().equals(criterion.knowledgePointId())).map(QuestionCatalog.KnowledgePoint::title).findFirst().orElse(criterion.id()));
        var old=new HashMap<String,CriterionStatus>(); b.criteria().forEach(c->old.put(c.criterionId(),c.status()));
        var changes=n.criteria().stream().map(c->{
            var prev=old.get(c.criterionId()); var now=c.status();
            String change=now==CriterionStatus.UNCERTAIN||prev==CriterionStatus.UNCERTAIN?"UNCERTAIN":
                now==CriterionStatus.COVERED&&prev==CriterionStatus.INCORRECT?"CORRECTED":
                now==CriterionStatus.COVERED&&prev!=CriterionStatus.COVERED?"NEWLY_COVERED":
                now!=CriterionStatus.COVERED&&prev==CriterionStatus.COVERED?"REGRESSED":
                now==CriterionStatus.COVERED?"MAINTAINED":"UNRESOLVED";
            return new CriterionChange(c.criterionId(),labels.get(c.criterionId()),prev.name(),now.name(),change);
        }).toList();
        return new Comparison(true,a.isHintsUsed()||a.isReferenceViewed()?"本次重答使用过辅助，用于检验刚才的改进，不代表长期掌握。":"同题、同评分版本的两次独立回答对比。",changes);
    }

    private TrainingEvaluation successful(PracticeAttempt a) {
        var e=latestEvaluation(a.getId());
        if(!"SUCCEEDED".equals(e.getStatus())) throw new PracticeConflictException("请先完成成功评估");
        return json.read(e.getResultJson(),TrainingEvaluation.class);
    }
    private PracticeEvaluation latestEvaluation(String id) { return evaluations.findFirstByAttempt_IdOrderByGenerationDesc(id).orElseThrow(); }
    private PracticeSession session(String id) { return sessions.findById(id).orElseThrow(()->new NotFoundException("练习不存在")); }
    private PracticeSession lockSession(String id) { return sessions.lock(id).orElseThrow(()->new NotFoundException("练习不存在")); }
    private PracticeAttempt attempt(String id) { return attempts.findById(id).orElseThrow(()->new NotFoundException("回答不存在")); }
    private QuestionSnapshot snapshot(PracticeSession s) { return json.read(s.getSnapshotJson(),QuestionSnapshot.class); }
    private void requireActive(PracticeSession s) { if(!"ACTIVE".equals(s.getStatus())) throw new PracticeConflictException("练习已结束，请重新选题"); }
}
