package com.mockinterview.controller;

import com.mockinterview.domain.dto.CreateInterviewRequest;
import com.mockinterview.domain.dto.MessageDto;
import com.mockinterview.service.interview.InterviewService;
import org.junit.jupiter.api.Test;
import com.mockinterview.capability.ratelimit.RateLimiterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterviewController.class)
class InterviewControllerTest {
    @MockBean
    private RateLimiterService limiter;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private InterviewService service;

    @Test
    void createReturnsSessionId() throws Exception {
        when(service.create(any())).thenReturn(7L);

        mvc.perform(post("/api/interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(7));
    }

    @Test
    void messagesReturnsList() throws Exception {
        when(service.messages(7L)).thenReturn(List.of(
                new MessageDto("INTERVIEWER", "你好", "L1_BACKGROUND"),
                new MessageDto("CANDIDATE", "你好", "L1_BACKGROUND")));

        mvc.perform(get("/api/interviews/7/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("INTERVIEWER"))
                .andExpect(jsonPath("$[1].role").value("CANDIDATE"));
    }
}
