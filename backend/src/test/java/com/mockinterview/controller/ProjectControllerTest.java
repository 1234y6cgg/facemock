package com.mockinterview.controller;
import com.mockinterview.capability.ratelimit.RateLimiterService;
import com.mockinterview.service.project.*;
import com.mockinterview.service.practice.PracticeConflictException;
import com.mockinterview.capability.knowledge.KnowledgeUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProjectController.class)
class ProjectControllerTest {
    @Autowired MockMvc mvc;@MockBean ProjectStore store;@MockBean ProjectPractice practice;@MockBean RateLimiterService limiter;
    @Test void rejectsInvalidIndexesVersionsEmptyMaterialsAndAnswers() throws Exception {
        mvc.perform(post("/api/projects/from-resume").contentType("application/json").content("{\"resumeId\":1,\"projectIndex\":-1}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/projects/p1/facts").contentType("application/json").content("{\"name\":\"A\",\"expectedRevision\":0,\"facts\":{}}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/projects/p1/materials").contentType("application/json").content("{\"title\":\"A\",\"origin\":\"\",\"content\":\"\",\"expectedRevision\":1}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/projects/sessions/s1/answers").contentType("application/json").content("{\"answer\":\" \",\"clientRequestId\":\"valid-request-01\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(store,practice);
    }
    @Test void returnsConflictsAndIndexUnavailableWithoutPretendResults() throws Exception {
        when(practice.start(eq("p1"),any())).thenThrow(new KnowledgeUnavailableException("项目材料尚未同步"));
        mvc.perform(post("/api/projects/p1/sessions").contentType("application/json").content("{\"template\":\"INTRO\",\"expectedRevision\":1,\"clientRequestId\":\"valid-request-01\"}")).andExpect(status().isServiceUnavailable());
        when(store.deleteMaterial("p1","m1",1)).thenThrow(new PracticeConflictException("版本已更新"));
        mvc.perform(delete("/api/projects/p1/materials/m1?expectedRevision=1")).andExpect(status().isConflict());
    }
    @Test void existingResumeSelectionAndSessionRoutesAreDistinct() throws Exception {
        mvc.perform(get("/api/projects/resumes")).andExpect(status().isOk());verify(store).resumeChoices();
        when(practice.get("missing")).thenThrow(new NotFoundException("训练不存在"));
        mvc.perform(get("/api/projects/sessions/missing")).andExpect(status().isNotFound());verify(practice).get("missing");
    }
}
