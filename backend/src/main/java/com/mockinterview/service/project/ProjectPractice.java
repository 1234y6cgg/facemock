package com.mockinterview.service.project;
import com.mockinterview.domain.project.*;
import com.mockinterview.repository.project.*;
import com.mockinterview.controller.NotFoundException;
import com.mockinterview.service.practice.PracticeConflictException;
import com.mockinterview.service.question.QuestionJson;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;
import static com.mockinterview.service.project.ProjectDtos.*;

@Service @Transactional
public class ProjectPractice {
    private final ProjectSessionRepository sessions;private final ProjectAttemptRepository attempts;private final ProjectEvaluationRepository evaluations;
    private final TrainingProjectRepository projects;private final ProjectIndex index;private final QuestionJson json;private final TransactionTemplate transaction;
    public ProjectPractice(ProjectSessionRepository sessions,ProjectAttemptRepository attempts,ProjectEvaluationRepository evaluations,TrainingProjectRepository projects,
        ProjectIndex index,QuestionJson json,PlatformTransactionManager manager){this.sessions=sessions;this.attempts=attempts;this.evaluations=evaluations;this.projects=projects;this.index=index;this.json=json;transaction=new TransactionTemplate(manager);}
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public synchronized SessionView start(String project,Start request){var template=TEMPLATES.stream().filter(t->t.id().equals(request.template())).findFirst().orElseThrow(()->new IllegalArgumentException("训练模板不存在"));
        var old=transaction.execute(status->sessions.findByRequestKey(request.clientRequestId()).map(s->{if(!s.getProjectId().equals(project)||!s.getTemplate().equals(template.id())||snapshot(s).revision()!=request.expectedRevision())throw new PracticeConflictException("请求标识已用于其他训练");return view(s);}).orElse(null));if(old!=null)return old;
        var context=index.context(project,request.expectedRevision(),template.question());return transaction.execute(status->{var p=projects.lock(project).orElseThrow(()->new NotFoundException("项目不存在"));
            if(p.isDeleted()||p.getRevision()!=context.revision())throw new PracticeConflictException("项目材料已变化，请刷新后练习");
            String detail=context.facts().fields().get(template.field());String question=template.question();if(!detail.isBlank())question+="\n围绕事实卡的这段内容展开（仍需按原材料核对）：“"+detail.substring(0,Math.min(detail.length(),180))+"”";
            return view(create(context,template.id(),question,request.clientRequestId(),null));});}
    private ProjectSession create(Snapshot snapshot,String template,String prompt,String key,String origin){var s=new ProjectSession();s.setId(UUID.randomUUID().toString());s.setProjectId(snapshot.projectId());s.setTemplate(template);s.setPrompt(prompt);
        s.setSnapshotJson(json.write(snapshot));s.setRequestKey(key);s.setOriginAttemptId(origin);s.setStatus("ACTIVE");s.setCreatedAt(Instant.now());return sessions.save(s);}
    public AttemptView answer(String id,Answer body){var s=lock(id);var old=attempts.findBySession_IdAndRequestKey(id,body.clientRequestId());if(old.isPresent()){
        if(!old.get().getAnswer().equals(body.answer())||!Objects.equals(old.get().getParentAttemptId(),body.parentAttemptId()))throw new PracticeConflictException("相同请求标识的回答不同");return attemptView(old.get());}
        active(s);var history=attempts.findBySession_IdOrderByNumber(id);if(history.isEmpty()?body.parentAttemptId()!=null:!history.get(history.size()-1).getId().equals(body.parentAttemptId())||!"SUCCEEDED".equals(latest(history.get(history.size()-1)).getStatus()))throw new PracticeConflictException("重答须引用当前成功评估的回答");
        if(body.answer()==null||body.answer().isBlank()||body.answer().length()>10000)throw new IllegalArgumentException("回答为 1–10000 字");
        var a=new ProjectAttempt();a.setId(UUID.randomUUID().toString());a.setSession(s);a.setAnswer(body.answer());a.setRequestKey(body.clientRequestId());a.setParentAttemptId(body.parentAttemptId());a.setNumber(history.size()+1);a.setCreatedAt(Instant.now());attempts.save(a);enqueue(a,1,body.clientRequestId());return attemptView(a);}
    private void enqueue(ProjectAttempt a,int generation,String key){var e=new ProjectEvaluation();e.setId(UUID.randomUUID().toString());e.setAttempt(a);e.setGeneration(generation);e.setRequestKey(key);e.setStatus("PENDING");e.setCreatedAt(Instant.now());evaluations.save(e);}
    public AttemptView retry(String id,String key){var a=attempt(id);lock(a.getSession().getId());if(evaluations.findByAttempt_IdAndRequestKey(id,key).isPresent())return attemptView(a);
        active(a.getSession());var e=latest(a);if(Set.of("PENDING","RUNNING").contains(e.getStatus()))return attemptView(a);
        if(!"FAILED".equals(e.getStatus())||e.getGeneration()>=3)throw new PracticeConflictException("无需重试或已达到三次评估上限");enqueue(a,e.getGeneration()+1,key);return attemptView(a);}
    public SessionView followup(String id,String key){var a=attempt(id);lock(a.getSession().getId());active(a.getSession());var old=sessions.findByRequestKey(key);if(old.isPresent()){
        if(!id.equals(old.get().getOriginAttemptId()))throw new PracticeConflictException("请求标识已用于其他追问");return view(old.get());}
        var e=latest(a);if(!"SUCCEEDED".equals(e.getStatus()))throw new PracticeConflictException("请先完成评估");var result=json.read(e.getResultJson(),Assessment.class);
        var target=result.criteria().stream().min(Comparator.comparingInt(c->switch(c.status()){case "CONFLICT"->0;case "MISSING"->1;case "PARTIAL"->2;case "UNCERTAIN"->3;default->4;})).orElseThrow();
        String question=switch(target.id()){case "contribution"->"请具体说明一项由你亲自完成的工作：你负责哪一步，团队成员负责哪一步，有什么过程证据？";
            case "evidence"->"请说明刚才回答的一项结果如何验证：测试方法、测量口径、原始证据分别是什么？缺少资料的部分请明确待补。";
            case "consistency"->"请核对刚才回答与材料的对应原文，解释差异；材料不准确时先修订事实卡，再开始新版练习。";
            default->"请围绕原问题给出一条主线：项目背景、你的实际行动和有证据的结果，各用一两句话说明。";};
        var snap=snapshot(a.getSession());if(!target.candidateQuote().isBlank())question+="\n针对你刚才的原话：“"+target.candidateQuote().substring(0,Math.min(target.candidateQuote().length(),180))+"”";
        return view(create(snap,"FOLLOWUP",question,key,id));}
    public SessionView complete(String id){var s=lock(id);if(attempts.findBySession_IdOrderByNumber(id).stream().anyMatch(a->Set.of("PENDING","RUNNING").contains(latest(a).getStatus())))throw new PracticeConflictException("请等待评估完成");s.setStatus("COMPLETED");return view(s);}
    @Transactional(readOnly=true) public SessionView get(String id){return view(sessions.findById(id).orElseThrow(()->new NotFoundException("项目训练不存在")));}
    @Transactional(readOnly=true) public List<SessionView> history(String project){return sessions.findByProjectIdOrderByCreatedAtDesc(project).stream().map(this::view).toList();}
    private SessionView view(ProjectSession s){return new SessionView(s.getId(),s.getProjectId(),s.getTemplate(),s.getPrompt(),s.getStatus(),s.getOriginAttemptId(),snapshot(s),s.getCreatedAt(),attempts.findBySession_IdOrderByNumber(s.getId()).stream().map(this::attemptView).toList());}
    private AttemptView attemptView(ProjectAttempt a){var all=evaluations.findByAttempt_IdOrderByGeneration(a.getId());var e=all.get(all.size()-1);return new AttemptView(a.getId(),a.getRequestKey(),a.getNumber(),a.getAnswer(),a.getParentAttemptId(),a.getCreatedAt(),evaluationView(e),all.stream().map(this::evaluationView).toList(),comparison(a,e));}
    private EvaluationView evaluationView(ProjectEvaluation e){return new EvaluationView(e.getId(),e.getGeneration(),e.getStatus(),e.getErrorCode(),e.getModelName(),e.getPromptVersion(),e.getDurationMs(),e.getResultJson()==null?null:json.read(e.getResultJson(),Assessment.class));}
    private Comparison comparison(ProjectAttempt a,ProjectEvaluation e){if(a.getParentAttemptId()==null)return null;var before=latest(attempt(a.getParentAttemptId()));
        if(!"SUCCEEDED".equals(before.getStatus())||!"SUCCEEDED".equals(e.getStatus()))return new Comparison(false,"两次评估成功后再比较",List.of());
        if(!Objects.equals(before.getPromptVersion(),e.getPromptVersion())||!Objects.equals(before.getModelName(),e.getModelName()))
            return new Comparison(false,"两次评估的模型或规则版本不同，暂不判断表达是否改进；请在当前版本下继续练习。",List.of());
        var b=json.read(before.getResultJson(),Assessment.class);var n=json.read(e.getResultJson(),Assessment.class);var changes=n.criteria().stream().map(c->{String prev=b.criteria().stream().filter(x->x.id().equals(c.id())).findFirst().orElseThrow().status();
            String change=c.status().equals("UNCERTAIN")||prev.equals("UNCERTAIN")?"UNCERTAIN":c.status().equals("COVERED")&&!prev.equals("COVERED")?"IMPROVED":!c.status().equals("COVERED")&&prev.equals("COVERED")?"REGRESSED":c.status().equals(prev)?"UNCHANGED":"CHANGED";
            return new Change(c.id(),prev,c.status(),change);}).toList();return new Comparison(true,"同题、同项目材料版本的表达对比，不代表长期掌握或项目真实性验证。",changes);}
    private Snapshot snapshot(ProjectSession s){return json.read(s.getSnapshotJson(),Snapshot.class);}
    private ProjectEvaluation latest(ProjectAttempt a){var list=evaluations.findByAttempt_IdOrderByGeneration(a.getId());return list.get(list.size()-1);}
    private ProjectAttempt attempt(String id){return attempts.findById(id).orElseThrow(()->new NotFoundException("项目回答不存在"));}
    private ProjectSession lock(String id){return sessions.lock(id).orElseThrow(()->new NotFoundException("项目训练不存在"));}
    private void active(ProjectSession s){if(!"ACTIVE".equals(s.getStatus())||projects.findById(s.getProjectId()).map(TrainingProject::isDeleted).orElse(true))throw new PracticeConflictException("训练已结束或项目已归档");}
}
