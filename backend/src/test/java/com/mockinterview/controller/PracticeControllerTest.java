package com.mockinterview.controller;
import com.mockinterview.capability.ratelimit.RateLimiterService;
import com.mockinterview.service.practice.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PracticeController.class)
class PracticeControllerTest {
    @Autowired MockMvc mvc;
    @MockBean PracticeStore store;
    @MockBean RateLimiterService limiter;
    @Test void rejectsEmptyOversizedAndUnsupportedInput() throws Exception {
        mvc.perform(post("/api/practice/sessions").contentType("application/json").content("{\"questionId\":\"redis.lock\",\"clientRequestId\":\"bad\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/practice/sessions/s1/attempts").contentType("application/json")
            .content("{\"answer\":\" \",\"inputMode\":\"TEXT\",\"clientRequestId\":\"valid-request-01\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/practice/sessions/s1/attempts").contentType("application/json")
            .content("{\"answer\":\"abc\",\"inputMode\":\"AUDIO\",\"clientRequestId\":\"valid-request-01\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/practice/sessions/s1/attempts").contentType("application/json")
            .content("{\"answer\":\""+"a".repeat(10001)+"\",\"inputMode\":\"TEXT\",\"clientRequestId\":\"valid-request-01\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(store);
    }
    @Test void returnsStateConflictAndNotFoundWithoutPretendSuccess() throws Exception {
        when(store.retry(eq("a1"),any())).thenThrow(new PracticeConflictException("已达到上限"));
        mvc.perform(post("/api/practice/attempts/a1/retry-evaluation").contentType("application/json").content("{\"clientRequestId\":\"retry-request-01\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("已达到上限"));
        when(store.get("missing")).thenThrow(new NotFoundException("练习不存在"));
        mvc.perform(get("/api/practice/sessions/missing")).andExpect(status().isNotFound());
    }
    @Test void firstAnswerReferenceRequiresExplicitAssistanceAndHistoryUsesPaging() throws Exception {
        when(store.reference("s1",false)).thenThrow(new PracticeConflictException("请先回答"));
        mvc.perform(get("/api/practice/sessions/s1/reference")).andExpect(status().isConflict());
        mvc.perform(get("/api/practice/sessions?page=bad")).andExpect(status().isBadRequest());
        verify(store).reference("s1",false);
    }
}
