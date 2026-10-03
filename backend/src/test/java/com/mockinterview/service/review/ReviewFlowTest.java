package com.mockinterview.service.review;
import com.mockinterview.service.practice.*;
import com.mockinterview.service.question.*;
import com.mockinterview.repository.practice.*;
import com.mockinterview.repository.review.*;
import com.mockinterview.domain.review.ReviewProfile;
import com.mockinterview.capability.evaluation.*;
import com.mockinterview.capability.knowledge.*;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.mockinterview.service.practice.PracticeDtos.*;
import static com.mockinterview.service.review.ReviewDtos.*;
@DataJpaTest(showSql=false,properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false"})
@ContextConfiguration(classes=ReviewFlowTest.Config.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ReviewFlowTest {
    @Configuration @Import({P2JpaConfiguration.class,ReviewService.class})
    @EntityScan(basePackageClasses={ReviewProfile.class,com.mockinterview.domain.practice.PracticeSession.class,com.mockinterview.domain.question.QuestionEntity.class})
    @EnableJpaRepositories(basePackageClasses=ReviewProfileRepository.class)
    static class Config {@Bean Clock clock(){return Clock.fixed(Instant.parse("2026-10-12T03:00:00Z"),ZoneOffset.UTC);}}
    @Autowired ReviewService review;@Autowired PracticeStore practice;@Autowired PracticeEvaluationStore jobs;
    @Autowired PracticeSessionRepository sessions;@Autowired PracticeAttemptRepository attempts;@Autowired PracticeEvaluationRepository evaluations;
    @Autowired KnowledgeProgressRepository progress;@Autowired ReviewTaskRepository tasks;@Autowired ReviewProfileRepository profiles;
    @Autowired QuestionCatalogLoader loader;@Autowired QuestionImportService importer;@Autowired QuestionJson json;@Autowired QuestionService questions;
    @Autowired PlatformTransactionManager manager;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockBean TechKnowledgeService knowledge;@MockBean PracticeAssessmentModel model;
    @BeforeEach void init(){tasks.deleteAll();progress.deleteAll();profiles.deleteAll();evaluations.deleteAll();attempts.deleteAll();sessions.deleteAll();importer.importCatalog(loader.loadBuiltin());}
    private String point(){return questions.snapshot("redis.lua-stock",1).question().criteria().get(0).knowledgePointId();}
    private AttemptView answer(SessionView s,String status,int day){var a=practice.submit(s.id(),new SubmitAnswer("测试回答",null,"TEXT",UUID.randomUUID().toString()));
        // Test database only: inject historical days without making immutable production answers editable.
        jdbc.update("update practice_attempt set created_at=? where id=?",java.sql.Timestamp.from(Instant.parse("2026-10-01T03:00:00Z").plusSeconds(day*86400L)),a.id());
        jobs.claim(a.evaluation().id());var snap=questions.snapshot("redis.lua-stock",1);var q=snap.question();
        var results=q.criteria().stream().map(c->new TrainingEvaluation.CriterionAssessment(c.id(),CriterionStatus.valueOf(status),
            status.equals("MISSING")?List.of():List.of("测试回答"),"业务规则替身",List.of())).toList();
        jobs.success(a.evaluation().id(),new TrainingEvaluation(q.id(),1,1,QuestionJson.sha256("测试回答"),results,List.of()),12);return a;}
    private SessionView initial(){return practice.create(new CreateSession("redis.lua-stock",1,UUID.randomUUID().toString()));}
    private Point current(){return review.overview().points().stream().filter(p->p.id().equals(point())).findFirst().orElseThrow();}
    @Test void persistedProjectionReplaysIdempotentlyAndEachDuePointInIndependentFirstAnswerCounts(){
        answer(initial(),"COVERED",0);var baseline=current();assertEquals(0,baseline.independentPasses());assertEquals(LocalDate.of(2026,10,2),baseline.dueDate());
        var start=new Start(point(),"delayed-start-001");var r=review.start(start);assertEquals("REVIEW",r.kind());assertEquals(r.id(),review.start(start).id());
        assertThrows(PracticeConflictException.class,()->practice.reference(r.id(),true));assertThrows(PracticeConflictException.class,()->practice.hint(r.id()));
        answer(r,"COVERED",1);assertEquals(1,current().independentPasses());var r2=review.start(new Start(point(),"delayed-start-002"));answer(r2,"COVERED",4);
        assertEquals("CONSOLIDATED",current().state());assertEquals(2,current().independentPasses());assertEquals(2,current().independentPasses());
        assertEquals(3,tasks.count());assertEquals(loader.loadBuiltin().knowledgePoints().size(),progress.count());assertEquals(6,review.overview().independentPasses());
        assertEquals(r2.id(),review.start(new Start(point(),"delayed-start-002")).id());
        progress.deleteAll();tasks.deleteAll();assertEquals(2,current().independentPasses());assertEquals(3,tasks.count());
    }
    @Test void failureUncertainAndAssistanceCannotCreateConsolidation(){var s=initial();practice.reference(s.id(),true);answer(s,"COVERED",0);assertEquals(0,current().independentPasses());
        var u=initial();answer(u,"UNCERTAIN",1);assertEquals(1,current().validAnswers());assertEquals(3,review.overview().uncertainItems());
        var f=initial();var a=practice.submit(f.id(),new SubmitAnswer("失败",null,"TEXT","failure-key-001"));jobs.claim(a.evaluation().id());jobs.failure(a.evaluation().id(),"TEST","不可用",1);assertEquals(1,current().validAnswers());assertEquals(1,review.overview().failedEvaluations());}
    @Test void recommendationPrioritySkipPauseAndDeleteReplayPreserveDayAndNoOverdueMultiplication(){var s=initial();answer(s,"INCORRECT",0);var today=review.today();assertEquals(3,today.duePoints());assertEquals("REVIEW",today.recommendations().get(0).kind());
        assertFalse(review.skip("redis.lua-stock").recommendations().stream().anyMatch(r->r.question().id().equals("redis.lua-stock")));
        review.configure(new Configure("Asia/Shanghai",5,List.of(1,3,7),2,true));assertEquals(0,review.today().recommendations().size());assertEquals(3,tasks.count());
        review.configure(new Configure("Asia/Shanghai",5,List.of(1,3,7),2,false));assertEquals(5,review.today().recommendations().size());
        new TransactionTemplate(manager).executeWithoutResult(tx->{for(var a:attempts.findBySession_IdOrderByAttemptNumber(s.id())){evaluations.deleteAll(evaluations.findByAttempt_IdOrderByGeneration(a.getId()));attempts.delete(a);}sessions.deleteById(s.id());});
        assertEquals("UNPRACTICED",current().state());assertEquals(0,tasks.count());assertEquals(0,current().validAnswers());}
    @Test void invalidRuleAndNotDueStartAreRejected(){assertThrows(IllegalArgumentException.class,()->review.configure(new Configure("Asia/Shanghai",3,List.of(3,1,7),2,false)));assertThrows(PracticeConflictException.class,()->review.start(new Start(point(),"too-early-001")));}
    @Test void differentFollowupQuestionAccumulatesByStableKnowledgePoint(){var s=initial();var a=answer(s,"COVERED",0);var child=practice.followup(a.id(),new RequestKey("shared-point-followup"));
        var follow=practice.submit(child.id(),new SubmitAnswer("追问回答",null,"TEXT","shared-point-answer"));jobs.claim(follow.evaluation().id());
        var snap=json.read(sessions.findById(child.id()).orElseThrow().getSnapshotJson(),com.mockinterview.domain.question.QuestionSnapshot.class);
        var c=snap.question().criteria().get(0);jobs.success(follow.evaluation().id(),new TrainingEvaluation(snap.question().id(),1,1,QuestionJson.sha256("追问回答"),
            List.of(new TrainingEvaluation.CriterionAssessment(c.id(),CriterionStatus.COVERED,List.of("追问回答"),"替身",List.of())),List.of()),1);
        var p=review.overview().points().stream().filter(x->x.id().equals(c.knowledgePointId())).findFirst().orElseThrow();assertEquals(2,p.validAnswers());
        assertEquals(2,p.evidence().stream().map(Evidence::questionId).distinct().count());assertEquals(0,p.independentPasses());}
}
