package com.mockinterview.service.speech;
import com.mockinterview.capability.speech.*;
import com.mockinterview.capability.knowledge.*;
import com.mockinterview.capability.evaluation.*;
import com.mockinterview.domain.question.*;
import com.mockinterview.repository.speech.*;
import com.mockinterview.repository.practice.*;
import com.mockinterview.service.question.*;
import com.mockinterview.service.practice.*;
import com.mockinterview.config.SpeechProperties;
import com.mockinterview.controller.NotFoundException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@DataJpaTest(showSql=false,properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false"})
@ContextConfiguration(classes=P3JpaConfiguration.class) @Transactional(propagation=Propagation.NOT_SUPPORTED)
class SpeechFlowTest {
    private static final Path ROOT=temp();
    private static Path temp(){try{return Files.createTempDirectory("interview-p3-test-");}catch(Exception e){throw new IllegalStateException(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("speech.storage-path",()->ROOT.toString());}
    @Autowired SpeechStore speech;@Autowired TranscriptionStore transcription;@Autowired TranscriptionWorker worker;@Autowired SpeechFileStore files;
    @Autowired PracticeStore practice;@Autowired PracticeDeletion deletion;@Autowired SpeechProperties settings;
    @Autowired PracticeEvaluationWorker scoring;@Autowired QuestionJson json;
    @Autowired SpeechRecordingRepository recordings;@Autowired TranscriptionJobRepository jobs;@Autowired PracticeSessionRepository sessions;
    @Autowired PracticeAttemptRepository attempts;@Autowired PracticeEvaluationRepository evaluations;@Autowired QuestionCatalogLoader loader;@Autowired QuestionImportService importer;
    @Autowired com.mockinterview.repository.InterviewSessionRepository interviews;
    @Autowired com.mockinterview.repository.InterviewMessageRepository interviewMessages;
    @MockBean SpeechTranscriber provider;@MockBean TechKnowledgeService knowledge;@MockBean PracticeAssessmentModel model;
    private byte[] audio(){return AudioRulesFixture.wav(2);}
    private String key(){return UUID.randomUUID().toString();}
    private PracticeDtos.SessionView start(){return practice.create(new PracticeDtos.CreateSession("redis.lua-stock",1,key()));}
    private SpeechDtos.RecordingView upload(PracticeDtos.SessionView s,String key){return speech.upload(s.id(),null,key,audio());}
    private void run(SpeechDtos.RecordingView r){worker.run(r.jobs().get(r.jobs().size()-1).id());}
    @BeforeEach void init(){
        for(var r:recordings.findAll())files.delete(r.getId());jobs.deleteAll();recordings.deleteAll();evaluations.deleteAll();attempts.deleteAll();sessions.deleteAll();
        interviewMessages.deleteAll();interviews.deleteAll();
        importer.importCatalog(loader.loadBuiltin());settings.setEnabled(true);settings.setAppId("test");settings.setApiKey("test");settings.setApiSecret("test");
        reset(provider,knowledge,model);when(provider.provider()).thenReturn("test-only-asr");when(provider.transcribe(any())).thenReturn("原始转写：Lua 可以避免并发扣减。");
        when(model.modelName()).thenReturn("fixture-only-model");when(knowledge.search(anyString(),anyInt())).thenThrow(new KnowledgeUnavailableException("test"));
        doAnswer(invocation->{var snapshot=(QuestionSnapshot)invocation.getArgument(0);String answer=invocation.getArgument(1);var q=snapshot.question();
            var criteria=q.criteria().stream().map(c->{var source=snapshot.sources().stream().filter(s->c.sourceIds().contains(s.sourceId())).findFirst().orElseThrow();
                return new TrainingEvaluation.CriterionAssessment(c.id(),CriterionStatus.COVERED,List.of(answer),"业务测试替身结果",List.of(new TrainingEvaluation.SourceCitation(source.sourceId(),source.documentRevision(),c.expected())));}).toList();
            return json.write(new TrainingEvaluation(q.id(),q.version(),q.rubricVersion(),QuestionJson.sha256(answer),criteria,List.of()));}).when(model).assess(any(),anyString(),any());
    }
    @Test void immutableOriginalConfirmedTextSingleAttemptAndOralMetadata(){
        var s=start();var r=upload(s,key());run(r);
        var body=new SpeechDtos.Confirm("嗯，我会把库存检查和扣减放到同一脚本里。",key());
        var a=speech.confirm(r.id(),body);var again=speech.confirm(r.id(),body);
        assertEquals(a.id(),again.id());assertEquals(1,practice.get(s.id()).attempts().size());assertEquals("SPEECH",a.inputMode());
        assertEquals(body.text(),a.answer());assertEquals(2000,a.oralFeedback().durationMs());assertEquals(1,a.oralFeedback().observations().size());
        assertEquals("原始转写：Lua 可以避免并发扣减。",speech.get(r.id()).jobs().get(0).originalText());
        assertThrows(PracticeConflictException.class,()->speech.confirm(r.id(),new SpeechDtos.Confirm("覆盖原回答",key())));
        verify(provider,times(1)).transcribe(any());
    }
    @Test void concurrentUploadOnlyCreatesOneRecordingAndOneJob() throws Exception {
        var s=start();var key=key();var pool=Executors.newFixedThreadPool(2);
        try{List<Callable<SpeechDtos.RecordingView>> calls=List.of(()->upload(s,key),()->upload(s,key));var tasks=pool.invokeAll(calls);assertEquals(tasks.get(0).get().id(),tasks.get(1).get().id());}
        finally{pool.shutdownNow();}assertEquals(1,recordings.count());assertEquals(1,jobs.count());
    }
    @Test void failureKeepsAudioRetryIsBoundedAndNeverCreatesAnswers(){
        var s=start();var r=upload(s,key());when(provider.transcribe(any())).thenThrow(new IllegalStateException("failure"));
        for(int generation=1;generation<=3;generation++){run(r);assertArrayEquals(audio(),speech.audio(r.id()));
            assertEquals("FAILED",speech.get(r.id()).jobs().get(generation-1).status());if(generation<3){String k=key();r=speech.retry(r.id(),k);assertEquals(generation+1,speech.retry(r.id(),k).jobs().size());}}
        String id=r.id();assertThrows(PracticeConflictException.class,()->speech.retry(id,key()));assertEquals(0,attempts.count());verify(provider,times(3)).transcribe(any());
    }
    @Test void emptyTranscriptFailsAndRestartMakesInterruptedRetryable(){
        var r=upload(start(),key());when(provider.transcribe(any())).thenReturn(" ");run(r);assertEquals("FAILED",speech.get(r.id()).jobs().get(0).status());
        var retry=speech.retry(r.id(),key());assertNotNull(transcription.claim(retry.jobs().get(1).id()));transcription.recover();
        assertEquals("INTERRUPTED",speech.get(r.id()).jobs().get(1).errorCode());assertArrayEquals(audio(),speech.audio(r.id()));
    }
    @Test void deletedRecordingCannotBeRestoredByLateAsrAndClearsTranscriptReference(){
        var r=upload(start(),key());var job=r.jobs().get(0).id();assertNotNull(transcription.claim(job));speech.delete(r.id());
        transcription.finish(job,"迟到结果","fixture",null);var deleted=speech.get(r.id());assertEquals("DELETED",deleted.state());assertNull(deleted.jobs().get(0).originalText());
        assertNull(deleted.attemptId());assertThrows(PracticeConflictException.class,()->speech.audio(r.id()));assertThrows(IllegalStateException.class,()->files.read(r.id()));
    }
    @Test void expirationDeletesFileAndBlocksPlaybackAndConfirmation(){
        var r=upload(start(),key());run(r);var entity=recordings.findById(r.id()).orElseThrow();entity.setExpiresAt(Instant.now().minusSeconds(1));recordings.saveAndFlush(entity);
        assertEquals("EXPIRED",speech.get(r.id()).state());assertNull(speech.get(r.id()).jobs().get(0).originalText());speech.expire();
        assertThrows(IllegalStateException.class,()->files.read(r.id()));assertThrows(PracticeConflictException.class,()->speech.confirm(r.id(),new SpeechDtos.Confirm("回答",key())));
    }
    @Test void deletingPracticeClearsAllAudioAndJobsAndRespectsPendingEvaluation(){
        var s=start();var r=upload(s,key());run(r);var a=speech.confirm(r.id(),new SpeechDtos.Confirm("我的确认回答",key()));
        assertThrows(PracticeConflictException.class,()->deletion.delete(s.id()));
        var evaluation=evaluations.findById(a.evaluation().id()).orElseThrow();evaluation.setStatus("FAILED");evaluations.saveAndFlush(evaluation);
        deletion.delete(s.id());assertEquals(0,recordings.count());assertEquals(0,jobs.count());assertEquals(0,attempts.count());assertEquals(0,evaluations.count());
        assertThrows(NotFoundException.class,()->practice.get(s.id()));assertThrows(IllegalStateException.class,()->files.read(r.id()));
    }
    @Test void unconfiguredServiceLeavesTextPracticeAvailableAndRejectsBadParents(){
        var s=start();settings.setApiSecret("");assertFalse(speech.status().configured());assertThrows(PracticeConflictException.class,()->upload(s,key()));
        var text=practice.submit(s.id(),new PracticeDtos.SubmitAnswer("文字仍可使用",null,"TEXT",key()));assertEquals("TEXT",text.inputMode());
        settings.setApiSecret("test");assertThrows(PracticeConflictException.class,()->upload(s,key()));
    }
    @Test void speechReanswerUsesLatestParentAndExistingComparisonWithoutChangingFirstText(){
        var s=start();var first=upload(s,key());run(first);var a=speech.confirm(first.id(),new SpeechDtos.Confirm("首答文本",key()));scoring.run(a.evaluation().id());
        var next=speech.upload(s.id(),a.id(),key(),audio());run(next);var b=speech.confirm(next.id(),new SpeechDtos.Confirm("重答确认文本",key()));scoring.run(b.evaluation().id());
        var result=practice.getAttempt(b.id());assertEquals(a.id(),result.parentAttemptId());assertTrue(result.comparison().comparable());assertEquals("SPEECH",result.inputMode());
        assertEquals("首答文本",practice.getAttempt(a.id()).answer());assertEquals(2,practice.get(s.id()).attempts().size());
    }
    @Test void deletingRootAlsoRemovesFollowupAudioAndAllReferences(){
        var s=start();var root=upload(s,key());run(root);var a=speech.confirm(root.id(),new SpeechDtos.Confirm("首答文本",key()));scoring.run(a.evaluation().id());
        var child=practice.followup(a.id(),new PracticeDtos.RequestKey(key()));var childAudio=upload(child,key());
        var deleted=deletion.delete(s.id());assertEquals(Set.of(s.id(),child.id()),Set.copyOf(deleted.sessionIds()));
        assertEquals(Set.of(root.id(),childAudio.id()),Set.copyOf(deleted.recordingIds()));assertEquals(0,jobs.count());assertEquals(0,recordings.count());
        assertThrows(NotFoundException.class,()->practice.get(child.id()));assertThrows(IllegalStateException.class,()->files.read(root.id()));assertThrows(IllegalStateException.class,()->files.read(childAudio.id()));
    }
    private Long interview() {
        var session=interviews.saveAndFlush(com.mockinterview.domain.InterviewSession.builder().resumeId(1L)
            .status(com.mockinterview.domain.InterviewStatus.IN_PROGRESS).mode("VOICE").build());
        interviewMessages.saveAndFlush(com.mockinterview.domain.InterviewMessage.builder().sessionId(session.getId())
            .role(com.mockinterview.domain.MessageRole.INTERVIEWER).content("请介绍你负责的项目").createdAt(java.time.LocalDateTime.now()).build());
        return session.getId();
    }
    @Test void mockInterviewUsesSameTranscriberAndRetainsOriginalWithoutCreatingPracticeAttempts(){
        Long id=interview();String request=key();var r=speech.uploadInterview(id,1,request,audio());
        assertEquals(r.id(),speech.uploadInterview(id,1,request,audio()).id());assertEquals("interview:"+id,r.sessionId());
        assertEquals("turn-1",r.parentAttemptId());run(r);
        assertEquals("原始转写：Lua 可以避免并发扣减。",speech.listInterview(id).get(0).jobs().get(0).originalText());
        assertEquals(0,attempts.count());assertEquals(1,recordings.count());assertEquals(1,jobs.count());
        verify(provider,times(1)).transcribe(any());assertArrayEquals(audio(),speech.audio(r.id()));
        assertThrows(PracticeConflictException.class,()->speech.confirm(r.id(),new SpeechDtos.Confirm("确认文本",key())));
        assertThrows(IllegalArgumentException.class,()->speech.upload("interview:"+id,null,key(),audio()));
    }
    @Test void mockInterviewRejectsUnconfiguredCompletedAndStaleTurnsBeforeCallingProvider(){
        Long id=interview();settings.setApiSecret("");assertThrows(PracticeConflictException.class,()->speech.uploadInterview(id,1,key(),audio()));
        settings.setApiSecret("test");assertThrows(PracticeConflictException.class,()->speech.uploadInterview(id,0,key(),audio()));
        var r=speech.uploadInterview(id,1,key(),audio());when(provider.transcribe(any())).thenThrow(new IllegalStateException("test"));run(r);
        interviewMessages.saveAndFlush(com.mockinterview.domain.InterviewMessage.builder().sessionId(id)
            .role(com.mockinterview.domain.MessageRole.CANDIDATE).content("已回答").createdAt(java.time.LocalDateTime.now()).build());
        assertThrows(PracticeConflictException.class,()->speech.uploadInterview(id,2,key(),audio()));
        assertThrows(PracticeConflictException.class,()->speech.retry(r.id(),key()));
        var s=interviews.findById(id).orElseThrow();s.setStatus(com.mockinterview.domain.InterviewStatus.COMPLETED);interviews.saveAndFlush(s);
        assertThrows(PracticeConflictException.class,()->speech.uploadInterview(id,2,key(),audio()));assertArrayEquals(audio(),speech.audio(r.id()));
        speech.delete(r.id());assertEquals("DELETED",speech.get(r.id()).state());
    }
    @Test void mockInterviewRetryAndDeletionKeepPracticeAudioAndDiscardLateResults(){
        var practiceAudio=upload(start(),key());Long id=interview();var r=speech.uploadInterview(id,1,key(),audio());
        when(provider.transcribe(any())).thenThrow(new IllegalStateException("test"));run(r);
        var retry=speech.retry(r.id(),key());assertEquals(2,retry.jobs().size());assertArrayEquals(audio(),speech.audio(r.id()));
        String job=retry.jobs().get(1).id();assertNotNull(transcription.claim(job));speech.deleteInterview(id);
        transcription.finish(job,"迟到的识别结果","fixture",null);
        assertTrue(speech.listInterview(id).isEmpty());assertFalse(jobs.existsById(job));
        assertThrows(IllegalStateException.class,()->files.read(r.id()));assertArrayEquals(audio(),speech.audio(practiceAudio.id()));
        assertEquals(1,recordings.count());
    }
    @AfterAll static void clean() throws Exception {try(var paths=Files.list(ROOT)){for(var path:paths.toList())Files.deleteIfExists(path);}Files.deleteIfExists(ROOT);}
}
