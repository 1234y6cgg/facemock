package com.mockinterview.service.review;
import com.mockinterview.domain.review.*;
import com.mockinterview.domain.question.*;
import com.mockinterview.repository.review.*;
import com.mockinterview.repository.practice.*;
import com.mockinterview.repository.question.QuestionRepository;
import com.mockinterview.service.question.*;
import com.mockinterview.service.practice.*;
import com.mockinterview.capability.evaluation.TrainingEvaluation;
import com.mockinterview.controller.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import static com.mockinterview.service.review.ReviewDtos.*;

@Service @Transactional
public class ReviewService {
    private static final String PROFILE="personal";
    private final ReviewProfileRepository profiles;
    private final KnowledgeProgressRepository progress;
    private final ReviewTaskRepository tasks;
    private final PracticeEvaluationRepository evaluations;
    private final PracticeSessionRepository sessions;
    private final QuestionRepository questions;
    private final QuestionJson json;
    private final PracticeStore practice;
    private final Clock clock;
    public ReviewService(ReviewProfileRepository profiles,KnowledgeProgressRepository progress,ReviewTaskRepository tasks,
        PracticeEvaluationRepository evaluations,PracticeSessionRepository sessions,QuestionRepository questions,
        QuestionJson json,PracticeStore practice,Clock clock,PlatformTransactionManager manager){
        this.profiles=profiles;this.progress=progress;this.tasks=tasks;this.evaluations=evaluations;
        this.sessions=sessions;this.questions=questions;this.json=json;this.practice=practice;this.clock=clock;
        new TransactionTemplate(manager).executeWithoutResult(tx->{if(!profiles.existsById(PROFILE)){
            var p=new ReviewProfile();p.setId(PROFILE);p.setPreferencesJson(json.write(new Preferences("Asia/Shanghai",3,List.of(1,3,7),2,false,null,List.of())));profiles.saveAndFlush(p);
        }});
    }
    private ReviewProfile profile(){
        return profiles.lock(PROFILE).orElseGet(()->{var p=new ReviewProfile();p.setId(PROFILE);
            p.setPreferencesJson(json.write(new Preferences("Asia/Shanghai",3,List.of(1,3,7),2,false,null,List.of())));return profiles.saveAndFlush(p);});
    }
    public Preferences configure(Configure input){
        try{ZoneId.of(input.timezone());}catch(DateTimeException e){throw new IllegalArgumentException("日期时区必须为有效的 IANA 时区");}
        if(input.dailyLimit()<1||input.dailyLimit()>20||input.requiredPasses()<2||input.requiredPasses()>5
            ||input.intervals()==null||input.intervals().size()!=3||input.intervals().get(0)<1
            ||input.intervals().get(2)>90||input.intervals().get(0)>=input.intervals().get(1)
            ||input.intervals().get(1)>=input.intervals().get(2))throw new IllegalArgumentException("复测间隔为三个递增的 1–90 天整数");
        var p=profile();var old=json.read(p.getPreferencesJson(),Preferences.class);
        var next=new Preferences(input.timezone(),input.dailyLimit(),input.intervals(),input.requiredPasses(),input.paused(),old.skippedDate(),old.skippedQuestions());
        p.setPreferencesJson(json.write(next));rebuild(next);return next;
    }
    public Overview overview(){return rebuild(json.read(profile().getPreferencesJson(),Preferences.class));}
    private List<QuestionSnapshot> catalog(){return questions.findAll().stream().filter(q->q.isActive())
        .sorted(Comparator.comparing(q->q.getId())).map(q->json.read(q.getCurrentRevision().getSnapshotJson(),QuestionSnapshot.class)).toList();}
    private Overview rebuild(Preferences prefs){
        // Immutable successful evaluations are the event ledger. Replay is deterministic and idempotent,
        // including after interrupted work, deletion, changed rules or a Redis reset.
        var specs=new TreeMap<String,QuestionCatalog.KnowledgePoint>();
        catalog().forEach(s->s.knowledgePoints().forEach(p->specs.put(p.id(),p)));
        var byPoint=new HashMap<String,List<Evidence>>();var seen=new HashSet<String>();int uncertain=0;
        var completed=evaluations.findByStatus("SUCCEEDED").stream()
            .sorted(Comparator.comparing(e->e.getGeneration())).toList();
        for(var e:completed){
            if(e.getResultJson()==null||!seen.add(e.getAttempt().getId()))continue;
            var a=e.getAttempt();var s=a.getSession();var snapshot=json.read(s.getSnapshotJson(),QuestionSnapshot.class);
            snapshot.knowledgePoints().forEach(p->specs.putIfAbsent(p.id(),p));
            var result=json.read(e.getResultJson(),TrainingEvaluation.class);
            for(var c:result.criteria()){
                var criterion=snapshot.question().criteria().stream().filter(x->x.id().equals(c.criterionId())).findFirst().orElseThrow();
                if(c.status().name().equals("UNCERTAIN"))uncertain++;
                boolean delayed="REVIEW".equals(s.getKind())
                    &&a.getAttemptNumber()==1&&a.getParentAttemptId()==null&&s.getReviewDueDate()!=null
                    &&!a.getCreatedAt().atZone(ZoneId.of(prefs.timezone())).toLocalDate().isBefore(s.getReviewDueDate());
                var evidence=new Evidence(e.getId(),a.getId(),s.getId(),snapshot.question().id(),c.status().name(),a.getCreatedAt(),
                    a.isHintsUsed()||a.isReferenceViewed(),delayed,false,c.candidateQuotes().isEmpty()?"":c.candidateQuotes().get(0));
                byPoint.computeIfAbsent(criterion.knowledgePointId(),key->new ArrayList<>()).add(evidence);
            }
        }
        var points=new ArrayList<Point>();
        for(var spec:specs.values()){
            var point=ReviewRules.reduce(spec.id(),spec.title(),spec.topic().name(),byPoint.getOrDefault(spec.id(),List.of()),prefs);points.add(point);
            var row=progress.findById(spec.id()).orElseGet(()->{var x=new KnowledgeProgress();x.setId(spec.id());return x;});
            var value=json.write(point);if(!value.equals(row.getProjectionJson())){row.setProjectionJson(value);progress.save(row);}
            if(point.dueDate()==null)tasks.deleteById(spec.id());else{
                var task=tasks.findById(spec.id()).orElseGet(()->{var x=new ReviewTask();x.setId(spec.id());return x;});
                task.setDueDate(point.dueDate());task.setStatus(prefs.paused()?"PAUSED":"SCHEDULED");
                task.setEvidenceId(point.evidence().get(0).evaluationId());tasks.save(task);
            }
        }
        for(var row:progress.findAll())if(!specs.containsKey(row.getId())){progress.delete(row);tasks.deleteById(row.getId());}
        return new Overview(prefs,List.copyOf(points),uncertain,evaluations.findByStatus("FAILED").size(),
            evaluations.findByStatus("PENDING").size()+evaluations.findByStatus("RUNNING").size(),
            points.stream().mapToInt(Point::validAnswers).sum(),points.stream().mapToInt(Point::independentPasses).sum());
    }
    private LocalDate today(Preferences p){return clock.instant().atZone(ZoneId.of(p.timezone())).toLocalDate();}
    public Today today(){
        var view=overview();var prefs=view.preferences();var day=today(prefs);
        var due=view.points().stream().filter(p->p.dueDate()!=null&&!p.dueDate().isAfter(day))
            .sorted(Comparator.comparing(Point::dueDate).thenComparing(Point::id)).toList();
        if(prefs.paused())return new Today(day,prefs,due.size(),List.of());
        var available=catalog();var recommendations=new LinkedHashMap<String,Recommendation>();
        var skipped=day.equals(prefs.skippedDate())?new HashSet<>(prefs.skippedQuestions()):Set.<String>of();
        for(var point:due)add(recommendations,available,point,"REVIEW","到期复习 · "+point.title()+" · "+point.dueDate(),skipped);
        for(var point:view.points().stream().filter(p->Set.of("INCORRECT","MISSING","PARTIAL").contains(p.lastStatus()==null?"":p.lastStatus()))
            .sorted(Comparator.comparing((Point p)->!"INCORRECT".equals(p.lastStatus())).thenComparing(Point::id)).toList())
            add(recommendations,available,point,"PRACTICE","上次还需练习 · "+point.title(),skipped);
        var practiced=new HashSet<String>();sessions.findAll().forEach(s->{if(!"FOLLOWUP".equals(s.getKind()))practiced.add(s.getQuestionId());});
        for(var snap:available){var q=snap.question();if(!practiced.contains(q.id())&&!skipped.contains(q.id()))
            recommendations.putIfAbsent(q.id(),new Recommendation(publicQuestion(q),null,"未练新题 · "+q.topic().name(),"NEW",null));}
        return new Today(day,prefs,due.size(),recommendations.values().stream().limit(prefs.dailyLimit()).toList());
    }
    private void add(Map<String,Recommendation> out,List<QuestionSnapshot> available,Point p,String kind,String reason,Set<String> skipped){
        available.stream().filter(s->s.question().criteria().stream().anyMatch(c->c.knowledgePointId().equals(p.id())))
            .filter(s->!skipped.contains(s.question().id())).findFirst().ifPresent(s->out.putIfAbsent(s.question().id(),
                new Recommendation(publicQuestion(s.question()),p.id(),reason,kind,p.dueDate())));
    }
    private QuestionService.PublicQuestion publicQuestion(QuestionCatalog.Question q){return new QuestionService.PublicQuestion(q.id(),q.version(),q.topic(),q.difficulty(),q.title(),q.prompt(),q.suggestedSeconds());}
    public Today skip(String questionId){
        var p=profile();var old=json.read(p.getPreferencesJson(),Preferences.class);var day=today(old);
        if(catalog().stream().noneMatch(s->s.question().id().equals(questionId)))throw new NotFoundException("题目不存在");
        var skipped=new LinkedHashSet<String>(day.equals(old.skippedDate())?old.skippedQuestions():List.of());skipped.add(questionId);
        p.setPreferencesJson(json.write(new Preferences(old.timezone(),old.dailyLimit(),old.intervals(),old.requiredPasses(),old.paused(),day,List.copyOf(skipped))));return today();
    }
    public PracticeDtos.SessionView start(Start body){
        var replay=sessions.findByClientRequestId(body.clientRequestId());
        if(replay.isPresent()){
            if(!"REVIEW".equals(replay.get().getKind())||!body.pointId().equals(replay.get().getReviewPointId()))throw new PracticeConflictException("请求标识已用于其他练习");
            return practice.get(replay.get().getId());
        }
        var view=overview();var point=view.points().stream().filter(p->p.id().equals(body.pointId())).findFirst().orElseThrow(()->new NotFoundException("知识点不存在"));
        if(view.preferences().paused())throw new PracticeConflictException("复习已暂停，请先恢复");
        if(point.dueDate()==null||point.dueDate().isAfter(today(view.preferences())))throw new PracticeConflictException("尚未到复测日期，可去题库自主练习");
        var snap=catalog().stream().filter(s->s.question().criteria().stream().anyMatch(c->c.knowledgePointId().equals(point.id()))).findFirst().orElseThrow();
        return practice.createReview(point.id(),snap.question().id(),snap.question().version(),point.dueDate(),body.clientRequestId());
    }
}
