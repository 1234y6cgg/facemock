package com.mockinterview.controller;

import com.mockinterview.capability.knowledge.*;
import com.mockinterview.capability.ratelimit.RateLimiterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(KnowledgeController.class)
class KnowledgeControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private TechKnowledgeService knowledge;
    @MockBean private RateLimiterService limiter;

    @Test
    void searchReturnsSourceAndSimilarity() throws Exception {
        when(knowledge.defaultTopK()).thenReturn(3);
        when(knowledge.search("Redis 库存", 3)).thenReturn(List.of(new KnowledgeHit("chunk", "doc", "库存",
                "官方文档", "https://redis.io", "Lua 原子执行", .78)));
        mvc.perform(get("/api/knowledge/search").param("query", "Redis 库存"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].sourceUrl").value("https://redis.io"))
                .andExpect(jsonPath("$[0].score").value(.78));
    }

    @Test
    void invalidDocumentsNeverReachImporter() throws Exception {
        mvc.perform(post("/api/knowledge/documents").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"../bad\",\"title\":\"\",\"content\":\"\",\"source\":\"\"}"))
                .andExpect(status().isBadRequest());
        verify(knowledge, never()).upsert(any());
    }

    @Test
    void validDocumentIsImported() throws Exception {
        when(knowledge.upsert(any())).thenReturn(new TechKnowledgeService.ImportResult("note", "rev", 2, true));
        mvc.perform(post("/api/knowledge/documents").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"note\",\"title\":\"库存\",\"content\":\"Lua 扣减库存\",\"source\":\"项目笔记\",\"tags\":[\"Redis\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.chunks").value(2));
    }

    @Test
    void versionedQuestionKnowledgeCannotBeOverwrittenOrDeleted() throws Exception {
        mvc.perform(post("/api/knowledge/documents").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"p1.redis.lock.v1\",\"title\":\"锁\",\"content\":\"替换资料\",\"source\":\"笔记\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/knowledge/documents/p1.redis.lock.v1")).andExpect(status().isBadRequest());
        verify(knowledge, never()).upsert(any());
        verify(knowledge, never()).deleteDocument(anyString());
    }

    @Test
    void unavailableSearchIs503AndMalformedParametersAre400() throws Exception {
        when(knowledge.search(anyString(), anyInt())).thenThrow(new KnowledgeUnavailableException("ChromaDB 不可用"));
        mvc.perform(get("/api/knowledge/search").param("query", "Redis").param("topK", "3"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(503));
        mvc.perform(get("/api/knowledge/search").param("query", "Redis").param("topK", "invalid"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/knowledge/search")).andExpect(status().isBadRequest());
    }
}
