package com.mockinterview.controller;

import com.mockinterview.domain.ResumeStatus;
import com.mockinterview.domain.dto.ResumeStatusResponse;
import com.mockinterview.service.resume.ResumeService;
import org.junit.jupiter.api.Test;
import com.mockinterview.capability.ratelimit.RateLimiterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResumeController.class)
class ResumeControllerTest {
    @MockBean
    private RateLimiterService limiter;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ResumeService service;

    @Test
    void uploadReturnsResumeId() throws Exception {
        when(limiter.allow(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        when(service.upload(any())).thenReturn(42L);

        mvc.perform(multipart("/api/resumes")
                        .file(new MockMultipartFile("file", "resume.pdf",
                                "application/pdf", new byte[]{1, 2, 3})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumeId").value(42));
    }

    @Test
    void getReturnsStatus() throws Exception {
        when(service.get(42L))
                .thenReturn(new ResumeStatusResponse(42L, ResumeStatus.PARSED, null));

        mvc.perform(get("/api/resumes/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARSED"));
    }
}
