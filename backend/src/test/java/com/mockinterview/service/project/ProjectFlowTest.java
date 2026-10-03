package com.mockinterview.service.project;
import com.mockinterview.domain.*;
import com.mockinterview.repository.ResumeRepository;
import com.mockinterview.repository.project.*;
import com.mockinterview.infrastructure.chroma.ChromaProjectStore;
import com.mockinterview.capability.knowledge.*;
import com.mockinterview.service.question.QuestionJson;
import com.mockinterview.service.practice.PracticeConflictException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static com.mockinterview.service.project.ProjectDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@DataJpaTest(showSql=false,properties="spring.jpa.hibernate.ddl-auto=create-drop")
@ContextConfiguration(classes=ProjectJpaConfiguration.class) @Transactional(propagation=Propagation.NOT_SUPPORTED)
class ProjectFlowTest {
    @Autowired ProjectStore store;@Autowired ProjectIndex index;@Autowired ProjectPractice practice;@Autowired ProjectEvaluationWorker worker;@Autowired ProjectEvaluationStore jobs;
    @Autowired TrainingProjectRepository projects;@Autowired ProjectMaterialRepository materials;@Autowired ProjectRevisionRepository revisions;
    @Autowired ProjectSessionRepository sessions;@Autowired ProjectAttemptRepository attempts;@Autowired ProjectEvaluationRepository evaluations;@Autowired ResumeRepository resumes;@Autowired QuestionJson json;
    @MockBean ChromaProjectStore chroma;@MockBean KnowledgeEmbeddingService embeddings;@MockBean ProjectAssessmentModel model;
    private Long resumeId;
    private String key(){return UUID.randomUUID().toString();}
    @BeforeEach void init(){evaluations.deleteAll();attempts.deleteAll();sessions.deleteAll();materials.deleteAll();revisions.deleteAll();projects.deleteAll();resumes.deleteAll();
        reset(chroma,embeddings,model);when(embeddings.embedQuery(anyString())).thenReturn(List.of(1f));when(embeddings.embedChunks(anyList())).thenAnswer(i->((List<?>)i.getArgument(0)).stream().map(x->List.of(1f)).toList());
        when(chroma.search(any(),anyList())).thenAnswer(i->((Snapshot)i.getArgument(0)).sources().stream().filter(s->!s.id().startsWith("fact:")).toList());
        when(model.modelName()).thenReturn("test-only-project-model");when(model.assess(any(),anyString(),anyString())).thenAnswer(i->{var snapshot=(Snapshot)i.getArgument(0);String answer=i.getArgument(2);
            var source=snapshot.sources().stream().filter(s->!s.planned()).findFirst().orElseThrow();return json.write(new Assessment(QuestionJson.sha256(answer),List.of("relevance","contribution","evidence","consistency").stream()
                .map(id->new Criterion(id,"COVERED","ANSWERED",answer,List.of(new Citation(source.id(),source.version(),source.content())))).toList(),List.of(),List.of("MEASURE")));});
        var parsed=new ResumeStructured("测试候选人",List.of("Java"),List.of(new ResumeStructured.Project("订单项目 A","A 只实现订单库存","开发者",List.of("Redis"),List.of("实现库存校验"),List.of("并发扣减")),
            new ResumeStructured.Project("物流项目 B","B 独有数据：日单量一百万","负责人",List.of("Kafka"),List.of("独立完成物流系统"),List.of("降低延迟 80%"))));
        var resume=Resume.builder().filename("p4-fixture.docx").status(ResumeStatus.PARSED).parsedJson(json.write(parsed)).rawText("测试材料，无真实用户信息").createdAt(LocalDateTime.now()).build();resumeId=resumes.save(resume).getId();}
    private ProjectView ready(){var p=store.importResume(new ImportResume(resumeId,0));p=store.facts(p.id(),new SaveFacts(p.name(),p.revision(),new Facts("订单库存","我负责库存校验","Redis Lua 检查库存并扣减","","","","计划加入补偿机制，尚未实施",true)));index.sync(p.id());return store.get(p.id());}
    private SessionView start(ProjectView p){return practice.start(p.id(),new Start("INTRO",key(),p.revision()));}
    private AttemptView answer(SessionView s,String text,String parent,String key){return practice.answer(s.id(),new Answer(text,parent,key));}
    private void run(AttemptView a){worker.run(a.evaluation().id());}

    @Test void resumeImportIsIdempotentAndDoesNotCopyOtherProjectsOrInventResults(){var a=store.importResume(new ImportResume(resumeId,0));var again=store.importResume(new ImportResume(resumeId,0));var b=store.importResume(new ImportResume(resumeId,1));
        assertEquals(a.id(),again.id());assertNotEquals(a.id(),b.id());assertEquals(2,store.list().size());assertEquals("",a.facts().result());assertEquals("",a.facts().evidence());assertFalse(a.facts().confirmed());
        assertFalse(json.write(store.snapshot(a.id())).contains("一百万"));assertFalse(json.write(store.snapshot(a.id())).contains("80%"));assertEquals(6,a.templates().size());assertEquals(1,store.resumeChoices().size());}
    @Test void unconfirmedPendingAndUnavailableIndexCannotStartPretendTraining(){var p=store.importResume(new ImportResume(resumeId,0));index.sync(p.id());assertThrows(PracticeConflictException.class,()->start(store.get(p.id())));
        var ready=ready();doThrow(new KnowledgeUnavailableException("offline")).when(chroma).search(any(),anyList());assertThrows(KnowledgeUnavailableException.class,()->start(ready));assertEquals(0,sessions.count());}
    @Test void staleFactsUpdatesAreRejectedWithoutOverwriting(){var p=ready();var saved=store.facts(p.id(),new SaveFacts("改名",p.revision(),p.facts()));assertEquals(p.revision()+1,saved.revision());
        assertThrows(PracticeConflictException.class,()->store.facts(p.id(),new SaveFacts("过期编辑",p.revision(),p.facts())));assertEquals("改名",store.get(p.id()).name());assertThrows(KnowledgeUnavailableException.class,()->start(saved));}
    @Test void materialVersionsDeletionAndHistoryKeepTheirOwnEvidence(){var p=ready();p=store.material(p.id(),null,new SaveMaterial("设计说明","README 第三节","A 旧材料：库存只在单机验证",p.revision()));index.sync(p.id());p=store.get(p.id());
        var original=start(p);String material=p.materials().get(0).id();var changed=store.material(p.id(),material,new SaveMaterial("设计说明","README 第四节","A 新材料：补充失败边界测试",p.revision()));index.sync(p.id());
        assertEquals(2,changed.materials().get(0).version());var fresh=start(store.get(p.id()));assertTrue(json.write(original.snapshot()).contains("只在单机验证"));assertFalse(json.write(fresh.snapshot()).contains("只在单机验证"));
        var deleted=store.deleteMaterial(p.id(),material,changed.revision());index.sync(p.id());assertTrue(deleted.materials().get(0).deleted());assertFalse(json.write(start(store.get(p.id())).snapshot()).contains("补充失败边界测试"));
        assertEquals(original.snapshot(),practice.get(original.id()).snapshot());assertEquals(3,materials.count());}
    @Test void answerAndStartIdempotencyReanswerComparisonAndScopedFollowup(){var p=ready();String request=key();var s=practice.start(p.id(),new Start("INTRO",request,p.revision()));assertEquals(s.id(),practice.start(p.id(),new Start("INTRO",request,p.revision())).id());
        String submit=key();var a=answer(s,"我实现了库存校验。",null,submit);assertEquals(a.id(),answer(s,a.answer(),null,submit).id());assertEquals(1,attempts.count());run(a);
        assertThrows(PracticeConflictException.class,()->answer(s,"重答",null,key()));var second=answer(s,"我负责用 Lua 检查库存并扣减，结果仍需补充证据。",a.id(),key());run(second);
        var complete=practice.get(s.id());assertTrue(complete.attempts().get(1).comparison().comparable());
        var secondEvaluation=evaluations.findById(second.evaluation().id()).orElseThrow();secondEvaluation.setPromptVersion("older-rules");evaluations.save(secondEvaluation);
        assertFalse(practice.get(s.id()).attempts().get(1).comparison().comparable());secondEvaluation.setPromptVersion(ProjectAssessmentParser.VERSION);secondEvaluation.setModelName("different-model");evaluations.save(secondEvaluation);
        assertFalse(practice.get(s.id()).attempts().get(1).comparison().comparable());secondEvaluation.setModelName(model.modelName());evaluations.save(secondEvaluation);
        assertTrue(practice.get(s.id()).attempts().get(1).comparison().comparable());String follow=key();var child=practice.followup(second.id(),follow);
        assertEquals(child.id(),practice.followup(second.id(),follow).id());assertEquals(s.snapshot(),child.snapshot());assertEquals(second.id(),child.originAttemptId());
        assertEquals("COMPLETED",practice.complete(s.id()).status());assertThrows(PracticeConflictException.class,()->answer(s,"再回答",second.id(),key()));}
    @Test void modelFailureRetainsAnswerBoundedRetryAndRestartRecovery(){var s=start(ready());var a=answer(s,"我的回答需要保留。",null,key());doThrow(new IllegalStateException("offline")).when(model).assess(any(),anyString(),anyString());run(a);
        for(int n=2;n<=3;n++){String request=key();var retried=practice.retry(a.id(),request);assertEquals(n,retried.evaluations().size());assertEquals(n,practice.retry(a.id(),request).evaluations().size());run(retried);}
        assertThrows(PracticeConflictException.class,()->practice.retry(a.id(),key()));assertEquals(a.answer(),practice.get(s.id()).attempts().get(0).answer());assertEquals(1,attempts.count());
        var another=start(ready());var interrupted=answer(another,"中断回答",null,key());jobs.claim(interrupted.evaluation().id());jobs.recover();assertEquals("INTERRUPTED",practice.get(another.id()).attempts().get(0).evaluation().errorCode());}
    @Test void inventedModelOutputDoesNotBecomeSuccessfulFeedback(){var s=start(ready());var a=answer(s,"我完成了校验",null,key());doReturn("{\"rewrittenAnswer\":\"我优化了百分之九十\"}").when(model).assess(any(),anyString(),anyString());run(a);
        var e=practice.get(s.id()).attempts().get(0).evaluation();assertEquals("FAILED",e.status());assertEquals("INVALID_OUTPUT",e.errorCode());assertNull(e.result());}
    @Test void lateIndexCompletionCannotMarkANewerRevisionReady(){var p=ready();var changed=store.facts(p.id(),new SaveFacts(p.name(),p.revision(),p.facts()));store.indexFinished(p.id(),p.revision(),true);
        assertEquals("PENDING",store.get(p.id()).indexStatus());assertNotEquals(changed.revision(),store.get(p.id()).indexedRevision());index.sync(p.id());assertEquals(changed.revision(),store.get(p.id()).indexedRevision());}
    @Test void sameVersionConcurrentEditsCommitAtMostOne() throws Exception {var p=ready();var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);try{var tasks=new ArrayList<Future<Boolean>>();
        for(int i=0;i<2;i++)tasks.add(pool.submit(()->{barrier.await();try{store.facts(p.id(),new SaveFacts("并发编辑",p.revision(),p.facts()));return true;}catch(PracticeConflictException e){return false;}}));
        int committed=0;for(var task:tasks)if(task.get(15,TimeUnit.SECONDS))committed++;assertEquals(1,committed);assertEquals(p.revision()+1,store.get(p.id()).revision());}finally{pool.shutdownNow();}}
}
