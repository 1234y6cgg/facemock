package com.mockinterview.controller;

import com.mockinterview.capability.ratelimit.RateLimiterService;
import com.mockinterview.domain.question.QuestionTopic;
import com.mockinterview.service.question.QuestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QuestionController.class)
class QuestionControllerTest {
    @Autowired MockMvc mvc;
    @MockBean QuestionService service;
    @MockBean RateLimiterService limiter;
    private final QuestionService.PublicQuestion publicQuestion =
            new QuestionService.PublicQuestion("redis.lua-stock", 1, QuestionTopic.REDIS, 3, "库存", "如何避免超卖？", 120);

    @Test
    void listAndDetailNeverExposeRubricOrReference() throws Exception {
        when(service.list(null, null, 0, 12, "")).thenReturn(new QuestionService.QuestionPage(List.of(publicQuestion), 0, 12, 1, 1));
        when(service.detail("redis.lua-stock", null)).thenReturn(publicQuestion);
        mvc.perform(get("/api/questions")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].prompt").value("如何避免超卖？"))
                .andExpect(jsonPath("$.items[0].criteria").doesNotExist())
                .andExpect(jsonPath("$.items[0].explanation").doesNotExist())
                .andExpect(jsonPath("$.items[0].sources").doesNotExist());
        mvc.perform(get("/api/questions/redis.lua-stock")).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.criteria").doesNotExist())
                .andExpect(jsonPath("$.explanation").doesNotExist()).andExpect(jsonPath("$.followups").doesNotExist());
    }

    @Test
    void malformedFiltersAre400AndMissingQuestions404() throws Exception {
        mvc.perform(get("/api/questions").param("topic", "BAD")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/questions").param("difficulty", "not-number")).andExpect(status().isBadRequest());
        when(service.list(isNull(), eq(7), anyInt(), anyInt(), anyString())).thenThrow(new IllegalArgumentException("难度非法"));
        mvc.perform(get("/api/questions").param("difficulty", "7")).andExpect(status().isBadRequest());
        when(service.detail("missing", null)).thenThrow(new NotFoundException("题目不存在"));
        mvc.perform(get("/api/questions/missing")).andExpect(status().isNotFound());
    }

    @Test void keywordAndTopicSummaryRemainPublicWithoutAnswers() throws Exception {
        when(service.list(null, null, 0, 12, "HashMap")).thenReturn(new QuestionService.QuestionPage(List.of(), 0, 12, 0, 0));
        when(service.summary()).thenReturn(new QuestionService.CatalogSummary(180, List.of(new QuestionService.TopicSummary(QuestionTopic.NETWORK, 12))));
        mvc.perform(get("/api/questions").param("q", "HashMap")).andExpect(status().isOk());
        verify(service).list(null, null, 0, 12, "HashMap");
        mvc.perform(get("/api/questions/topics")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuestions").value(180))
                .andExpect(jsonPath("$.topics[0].topic").value("NETWORK"))
                .andExpect(jsonPath("$.topics[0].count").value(12))
                .andExpect(jsonPath("$.questions").doesNotExist());
    }
}
