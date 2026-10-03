package com.mockinterview.controller;

import com.mockinterview.capability.ratelimit.RateLimiterService;
import com.mockinterview.service.speech.*;
import com.mockinterview.service.practice.PracticeConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SpeechController.class)
class SpeechControllerTest {
    @Autowired MockMvc mvc;
    @MockBean SpeechStore speech;
    @MockBean PracticeDeletion deletion;
    @MockBean RateLimiterService limiter;

    @Test void invalidOrMissingAudioAndConfirmationDoNotReachStorage() throws Exception {
        mvc.perform(multipart("/api/speech/recordings").param("sessionId","s1").param("clientRequestId","valid-request-01"))
            .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/speech/recordings").file(new MockMultipartFile("file",new byte[0]))
            .param("sessionId","s1").param("clientRequestId","valid-request-01")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/speech/recordings/r1/confirm").contentType("application/json")
            .content("{\"text\":\" \",\"clientRequestId\":\"valid-request-01\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/speech/recordings/r1/retry").contentType("application/json")
            .content("{\"clientRequestId\":\"bad\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(speech);
    }
    @Test void unconfiguredStateIsExplicitAndConflictIsNotSuccess() throws Exception {
        when(speech.status()).thenReturn(new SpeechDtos.Status(false,"xfyun-iat",180,30,"尚未配置"));
        mvc.perform(get("/api/speech/status")).andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false));
        when(speech.audio("expired")).thenThrow(new PracticeConflictException("录音已过期"));
        mvc.perform(get("/api/speech/recordings/expired/audio")).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("录音已过期"));
    }
    @Test void replayAvoidsCachingAndRecordingDeletionIsExplicit() throws Exception {
        when(speech.audio("r1")).thenReturn(new byte[]{1,2});
        mvc.perform(get("/api/speech/recordings/r1/audio")).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store")).andExpect(content().contentType("audio/wav"));
        mvc.perform(delete("/api/speech/recordings/r1")).andExpect(status().isNoContent());
        verify(speech).delete("r1");
    }
    @Test void deletingPracticeReturnsIdsNeededToClearBrowserDrafts() throws Exception {
        when(deletion.delete("s1")).thenReturn(new PracticeDeletion.Deleted(List.of("s1","followup"),List.of("r1")));
        mvc.perform(delete("/api/practice/sessions/s1")).andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionIds[1]").value("followup")).andExpect(jsonPath("$.recordingIds[0]").value("r1"));
    }
    @Test void mockInterviewUploadUsesItsOwnScopeAndRejectsMissingOrEmptyAudio() throws Exception {
        mvc.perform(multipart("/api/interviews/7/speech/recordings").param("turn","1").param("clientRequestId","valid-request-01"))
            .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/interviews/7/speech/recordings").file(new MockMultipartFile("file",new byte[0]))
            .param("turn","1").param("clientRequestId","valid-request-01")).andExpect(status().isBadRequest());
        verifyNoInteractions(speech);
        byte[] audio={1,2};mvc.perform(multipart("/api/interviews/7/speech/recordings").file(new MockMultipartFile("file",audio))
            .param("turn","1").param("clientRequestId","valid-request-01")).andExpect(status().isOk());
        verify(speech).uploadInterview(7L,1,"valid-request-01",audio);
        when(speech.listInterview(7L)).thenReturn(List.of());mvc.perform(get("/api/interviews/7/speech/recordings"))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
    }
}
