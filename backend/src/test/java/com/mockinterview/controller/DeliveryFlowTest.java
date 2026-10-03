package com.mockinterview.controller;
import com.mockinterview.service.practice.*;
import com.mockinterview.service.question.QuestionJson;
import com.mockinterview.repository.practice.PracticeSessionRepository;
import com.mockinterview.capability.evaluation.PracticeAssessmentModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:p6delivery;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","knowledge.enabled=false","questions.index-knowledge=false",
    "practice.jobs.enabled=false","projects.jobs.enabled=false","speech.jobs.enabled=false","speech.enabled=false","deepseek.api-key=delivery-test-secret-not-exported"})
@AutoConfigureMockMvc
class DeliveryFlowTest {
    @Autowired MockMvc mvc;@Autowired PracticeStore practice;@Autowired QuestionJson json;
    @MockBean PracticeAssessmentModel model;
    @Autowired com.mockinterview.repository.ResumeRepository resumes;
    @Autowired com.mockinterview.repository.InterviewSessionRepository interviews;
    @Autowired com.mockinterview.repository.InterviewMessageRepository messages;
    @Autowired com.mockinterview.service.speech.SpeechStore speech;
    @Autowired com.mockinterview.config.SpeechProperties speechSettings;
    @Autowired com.mockinterview.capability.speech.SpeechFileStore files;
    @Autowired com.mockinterview.repository.speech.SpeechRecordingRepository recordings;
    @Autowired com.mockinterview.repository.speech.TranscriptionJobRepository transcriptions;
    @Test void deletingMockInterviewThroughHttpAlsoDeletesItsAudioAndTranscriptionJobs() throws Exception {
        var resume=resumes.saveAndFlush(com.mockinterview.domain.Resume.builder().filename("test-only-resume").build());
        var session=interviews.saveAndFlush(com.mockinterview.domain.InterviewSession.builder().resumeId(resume.getId())
            .status(com.mockinterview.domain.InterviewStatus.IN_PROGRESS).build());
        messages.saveAndFlush(com.mockinterview.domain.InterviewMessage.builder().sessionId(session.getId())
            .role(com.mockinterview.domain.MessageRole.INTERVIEWER).content("test-only-question").build());
        var wav=new byte[32044];var buffer=java.nio.ByteBuffer.wrap(wav).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes()).putInt(wav.length-8).put("WAVEfmt ".getBytes()).putInt(16)
            .putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16)
            .put("data".getBytes()).putInt(32000);while(buffer.hasRemaining())buffer.putShort((short)1000);
        speechSettings.setEnabled(true);speechSettings.setAppId("test");speechSettings.setApiKey("test");speechSettings.setApiSecret("test");
        String recordingId=null;
        try {
            var record=speech.uploadInterview(session.getId(),1,UUID.randomUUID().toString(),wav);recordingId=record.id();
            String job=record.jobs().get(0).id();
            mvc.perform(delete("/api/interviews/"+session.getId())).andExpect(status().isOk());
            assertFalse(interviews.existsById(session.getId()));assertFalse(resumes.existsById(resume.getId()));
            assertFalse(recordings.existsById(record.id()));assertFalse(transcriptions.existsById(job));
            assertTrue(messages.findBySessionIdOrderByCreatedAtAsc(session.getId()).isEmpty());
            assertThrows(IllegalStateException.class,()->files.read(record.id()));
        } finally {speechSettings.setEnabled(false);if(recordingId!=null)files.delete(recordingId);}
    }
    @Test void newRoutesPlanParametersAndExportCoexistWithOriginalEndpoints()throws Exception{
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/api/review/today")).andExpect(status().isOk()).andExpect(jsonPath("$.recommendations.length()").value(3));
        mvc.perform(get("/api/progress/knowledge-points")).andExpect(status().isOk()).andExpect(jsonPath("$.points.length()").value(540));
        mvc.perform(put("/api/review/preferences").contentType("application/json").content("{\"timezone\":\"invalid-zone\",\"dailyLimit\":3,\"intervals\":[1,3,7],\"requiredPasses\":2,\"paused\":false}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/interviews")).andExpect(status().isOk());mvc.perform(get("/api/projects")).andExpect(status().isOk());
        mvc.perform(get("/api/speech/status")).andExpect(status().isOk());
        var s=practice.create(new PracticeDtos.CreateSession("redis.lua-stock",1,UUID.randomUUID().toString()));
        practice.submit(s.id(),new PracticeDtos.SubmitAnswer("这是只在测试数据库的导出样例",null,"TEXT",UUID.randomUUID().toString()));
        var result=mvc.perform(get("/api/data/export")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.schemaVersion").value(1)).andExpect(jsonPath("$.practiceSessions.length()").value(1))
            .andExpect(jsonPath("$.practiceAttempts[0].answer").value("这是只在测试数据库的导出样例")).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(result.contains("delivery-test-secret-not-exported"));assertTrue(result.contains("projectRevisions"));assertTrue(result.contains("transcriptions"));
    }
}
