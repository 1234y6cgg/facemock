package com.mockinterview.service.practice;
import com.mockinterview.capability.evaluation.*;
import com.mockinterview.capability.knowledge.*;
import com.mockinterview.domain.question.*;
import com.mockinterview.repository.practice.*;
import com.mockinterview.service.question.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.mockinterview.service.practice.PracticeDtos.*;

@DataJpaTest(showSql=false,properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false"})
@ContextConfiguration(classes=P2JpaConfiguration.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class PracticeFlowTest {
    @Autowired PracticeStore store;
    @Autowired PracticeEvaluationStore jobs;
    @Autowired PracticeEvaluationWorker worker;
    @Autowired QuestionCatalogLoader loader;
    @Autowired QuestionImportService importer;
    @Autowired QuestionJson json;
    @Autowired PracticeSessionRepository sessions;
    @Autowired PracticeAttemptRepository attempts;
    @Autowired PracticeEvaluationRepository evaluations;
    @MockBean TechKnowledgeService knowledge;
    @MockBean PracticeAssessmentModel model;

    @BeforeEach void init() {
        evaluations.deleteAll(); attempts.deleteAll(); sessions.deleteAll(); importer.importCatalog(loader.loadBuiltin());
        reset(model,knowledge); when(model.modelName()).thenReturn("fixture-test-model");
        when(knowledge.search(anyString(),anyInt())).thenThrow(new KnowledgeUnavailableException("test outage"));
        doAnswer(invocation->correct(invocation.getArgument(0),invocation.getArgument(1))).when(model).assess(any(),anyString(),any());
    }
    private String correct(QuestionSnapshot snapshot,String answer) {
        var q=snapshot.question();
        var results=q.criteria().stream().map(c->{
            var source=snapshot.sources().stream().filter(s->c.sourceIds().contains(s.sourceId())).findFirst().orElseThrow();
            return new TrainingEvaluation.CriterionAssessment(c.id(),CriterionStatus.COVERED,List.of(answer),"测试用完整回答",
                List.of(new TrainingEvaluation.SourceCitation(source.sourceId(),source.documentRevision(),c.expected())));
        }).toList();
        return json.write(new TrainingEvaluation(q.id(),q.version(),q.rubricVersion(),QuestionJson.sha256(answer),results,List.of()));
    }
    private SessionView start() { return store.create(new CreateSession("redis.lua-stock",1,UUID.randomUUID().toString())); }
    private AttemptView submit(SessionView s,String answer,String parent,String key) { return store.submit(s.id(),new SubmitAnswer(answer,parent,"TEXT",key)); }
    private void run(AttemptView a) { worker.run(a.evaluation().id()); }

    @Test void lifecycleRetainsFirstAnswerAndTracksAssistanceComparisonFollowupAndHistory() throws Exception {
        var s=start();
        assertEquals(0,s.attempts().size());
        assertThrows(PracticeConflictException.class,()->store.reference(s.id(),false));
        var sample=EvaluationFixtures.cases(json).get(0);
        var answer=sample.path("answer").asText();
        doAnswer(inv->{
            var snapshot=(QuestionSnapshot)inv.getArgument(0);
            var out=EvaluationFixtures.expected(sample,snapshot);
            var partial=out.criteria().stream().map(c->c.criterionId().equals("scope")?new TrainingEvaluation.CriterionAssessment(
                c.criterionId(),CriterionStatus.MISSING,List.of(),"测试遗漏",c.references()):c).toList();
            return json.write(new TrainingEvaluation(out.questionId(),out.questionVersion(),out.rubricVersion(),out.answerHash(),partial,List.of()));
        }).when(model).assess(any(),anyString(),any());
        var first=submit(s,answer,null,"first-request-01"); run(first);
        var restored=store.get(s.id()).attempts().get(0);
        assertEquals("SUCCEEDED",restored.evaluation().status()); assertFalse(restored.referenceViewed()); assertFalse(restored.hintsUsed());
        assertEquals("UNAVAILABLE",restored.evaluation().referenceState());
        assertTrue(store.reference(s.id(),false).explanation().contains("原子"));
        store.hint(s.id());
        doAnswer(inv->correct(inv.getArgument(0),inv.getArgument(1))).when(model).assess(any(),anyString(),any());
        var second=submit(s,answer+" 我补充数据库需要独立处理。",first.id(),"second-request-01"); run(second);
        second=store.getAttempt(second.id());
        assertEquals(first.id(),second.parentAttemptId()); assertTrue(second.referenceViewed()); assertTrue(second.hintsUsed());
        assertTrue(second.comparison().comparable());
        assertTrue(second.comparison().criteria().stream().anyMatch(c->"scope".equals(c.criterionId())&&"NEWLY_COVERED".equals(c.change())));
        assertEquals(answer,store.getAttempt(first.id()).answer()); assertFalse(store.getAttempt(first.id()).referenceViewed());
        var child=store.followup(second.id(),new RequestKey("followup-request-01"));
        assertEquals("FOLLOWUP",child.kind()); assertEquals(s.id(),child.originSessionId());
        assertTrue(child.referenceViewed()); assertTrue(child.hintsUsed());
        assertTrue(child.question().prompt().contains("有人认为"));
        assertEquals(child.id(),store.followup(second.id(),new RequestKey("followup-request-01")).id());
        var follow=submit(child,"这个场景要控制并发检查与扣减的边界。",null,"follow-answer-01"); run(follow);
        assertEquals("SUCCEEDED",store.getAttempt(follow.id()).evaluation().status());
        assertEquals(1,store.getAttempt(follow.id()).evaluation().result().criteria().size());
        assertEquals(2,store.history(0,12).totalElements());
        assertEquals("COMPLETED",store.complete(s.id()).status());
        var secondId=second.id();
        assertThrows(PracticeConflictException.class,()->submit(s,"新回答",secondId,"after-close-request"));
    }

    @Test void concurrentDuplicateSubmissionCreatesOneAttemptAndOneJob() throws Exception {
        var s=start(); var pool=Executors.newFixedThreadPool(2);
        try {
            var gate=new CountDownLatch(1);
            Callable<AttemptView> action=()->{gate.await();return submit(s,"并发回答",null,"duplicate-request-01");};
            var f1=pool.submit(action); var f2=pool.submit(action); gate.countDown();
            var a=f1.get(10,TimeUnit.SECONDS); var b=f2.get(10,TimeUnit.SECONDS);
            assertEquals(a.id(),b.id()); assertEquals(1,attempts.count()); assertEquals(1,evaluations.count());
            run(a); run(b); verify(model,times(1)).assess(any(),anyString(),any());
            assertThrows(PracticeConflictException.class,()->submit(s,"不同回答",null,"duplicate-request-01"));
        } finally { pool.shutdownNow(); }
    }

    @Test void concurrentSessionCreationAndCorrectedComparisonAreStable() throws Exception {
        var pool=Executors.newFixedThreadPool(2);
        SessionView s;
        try {
            var request=new CreateSession("redis.lua-stock",1,"same-create-request-01");
            var first=pool.submit(()->store.create(request)); var second=pool.submit(()->store.create(request));
            s=first.get(10,TimeUnit.SECONDS);
            assertEquals(s.id(),second.get(10,TimeUnit.SECONDS).id()); assertEquals(1,sessions.count());
        } finally { pool.shutdownNow(); }
        doAnswer(inv->{
            var out=json.read(correct(inv.getArgument(0),inv.getArgument(1)),TrainingEvaluation.class);
            return json.write(new TrainingEvaluation(out.questionId(),out.questionVersion(),out.rubricVersion(),out.answerHash(),
                out.criteria().stream().map(c->new TrainingEvaluation.CriterionAssessment(c.criterionId(),CriterionStatus.INCORRECT,
                    c.candidateQuotes(),"测试错误",c.references())).toList(),List.of()));
        }).when(model).assess(any(),anyString(),any());
        var first=submit(s,"错误回答",null,"incorrect-request-01"); run(first);
        doAnswer(inv->correct(inv.getArgument(0),inv.getArgument(1))).when(model).assess(any(),anyString(),any());
        var second=submit(s,"纠正回答",first.id(),"corrected-request-01"); run(second);
        assertTrue(store.getAttempt(second.id()).comparison().criteria().stream().allMatch(c->"CORRECTED".equals(c.change())));
        assertEquals(PracticeAssessmentPrompt.VERSION,store.getAttempt(second.id()).evaluation().promptVersion());
    }

    @Test void malformedOutputFailsWithoutFabricatedResultAndRetryIsIdempotent() {
        var s=start(); doReturn("{\"fake\":99}").when(model).assess(any(),anyString(),any());
        var a=submit(s,"回答始终保留",null,"failed-request-01"); run(a);
        a=store.getAttempt(a.id()); assertEquals("FAILED",a.evaluation().status()); assertNull(a.evaluation().result());
        assertEquals("INVALID_OUTPUT",a.evaluation().errorCode());
        final var failed=a;
        assertThrows(PracticeConflictException.class,()->submit(s,"不能跳过失败",failed.id(),"blocked-request-01"));
        doAnswer(inv->correct(inv.getArgument(0),inv.getArgument(1))).when(model).assess(any(),anyString(),any());
        var retry=store.retry(a.id(),new RequestKey("retry-request-01"));
        assertEquals(2,retry.evaluations().size()); run(retry);
        assertEquals(2,store.retry(a.id(),new RequestKey("retry-request-01")).evaluations().size());
        var success=store.getAttempt(a.id()); assertEquals("SUCCEEDED",success.evaluation().status());
        assertEquals("回答始终保留",success.answer()); assertEquals("FAILED",success.evaluations().get(0).status());
        assertEquals(1,attempts.count()); assertEquals(2,evaluations.count());
    }

    @Test void interruptedRunningJobsBecomeRetryableAndPendingSurviveRestart() {
        var a=submit(start(),"中断回答",null,"interrupt-request-01");
        assertNotNull(jobs.claim(a.evaluation().id())); assertNull(jobs.claim(a.evaluation().id()));
        assertEquals(1,jobs.recoverInterrupted()); assertEquals(0,jobs.recoverInterrupted());
        assertEquals("INTERRUPTED",store.getAttempt(a.id()).evaluation().errorCode());
        var pending=submit(start(),"等待中的回答",null,"pending-request-01");
        assertEquals("PENDING",store.getAttempt(pending.id()).evaluation().status());
        var retried=store.retry(a.id(),new RequestKey("restart-retry-01")); run(retried);
        assertEquals("SUCCEEDED",store.getAttempt(a.id()).evaluation().status());
        run(pending); assertEquals("SUCCEEDED",store.getAttempt(pending.id()).evaluation().status());
    }

    @Test void retryLimitAndStateGuardsPreventOverlappingOrUnlimitedEvaluation() {
        doThrow(new IllegalStateException("test timeout")).when(model).assess(any(),anyString(),any());
        var s=start(); var a=submit(s,"原回答",null,"timeout-request-01");
        assertThrows(PracticeConflictException.class,()->store.complete(s.id()));
        assertEquals(1,store.retry(a.id(),new RequestKey("still-pending-request")).evaluations().size());
        run(a);
        for(int generation=2;generation<=3;generation++) { a=store.retry(a.id(),new RequestKey("limit-retry-"+generation));run(a); }
        final var exhausted=a;
        assertThrows(PracticeConflictException.class,()->store.retry(exhausted.id(),new RequestKey("limit-retry-4")));
        assertEquals(3,evaluations.count()); assertNull(store.getAttempt(a.id()).evaluation().result());
        assertEquals(1,attempts.count());
    }

    @Test void lackOfAuditedSourcesProducesUncertainWithoutCallingModel() {
        var s=start(); var entity=sessions.findById(s.id()).orElseThrow();
        var snapshot=json.read(entity.getSnapshotJson(),QuestionSnapshot.class);
        entity.setSnapshotJson(json.write(new QuestionSnapshot(snapshot.question(),snapshot.knowledgePoints(),List.of())));
        sessions.save(entity);
        var a=submit(s,"无法确认的回答",null,"no-evidence-request"); run(a);
        var result=store.getAttempt(a.id()).evaluation();
        assertEquals("SUCCEEDED",result.status()); assertEquals("not-called",result.modelName());
        assertTrue(result.result().criteria().stream().allMatch(c->c.status()==CriterionStatus.UNCERTAIN));
        verify(model,never()).assess(any(),anyString(),any());
    }

    @Test void earlyReferenceRecordsAssistanceWithoutLeakingNormalSessionRubric() {
        var s=start(); store.reference(s.id(),true);
        var a=submit(s,"有辅助练习",null,"assisted-request-01");
        assertTrue(a.referenceViewed()); assertNull(a.evaluation().result()); assertTrue(a.evaluation().sources().isEmpty());
    }
}
