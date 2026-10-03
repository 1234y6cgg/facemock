package com.mockinterview.capability.knowledge;

import com.mockinterview.agent.*;
import com.mockinterview.domain.ResumeStructured;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InterviewKnowledgeQueryTest {
    @Test
    void queryUsesCurrentProjectAndRecentConversationWithinModelBudget() {
        var resume = ResumeStructured.builder().skills(List.of("Python", "Java", "Redis"))
                .projects(List.of(
                        ResumeStructured.Project.builder().name("过往图像识别").techStack(List.of("Python")).build(),
                        ResumeStructured.Project.builder().name("秒杀库存").techStack(List.of("Redis", "MySQL")).build())).build();
        var state = InterviewState.builder().currentProjectIdx(1).layer(Layer.L3_DETAILS).build();
        String query = InterviewKnowledgeQuery.question(resume, state, "早期问题".repeat(200) + "最后问题：怎样用 Lua 保证库存原子扣减？", "Java 后端");
        assertTrue(query.contains("最后问题"));
        assertTrue(query.contains("秒杀库存"));
        assertTrue(query.contains("Redis"));
        assertFalse(query.contains("Python"));
        assertFalse(query.contains("过往图像识别"));
        assertTrue(query.length() <= 480);
        assertTrue(InterviewKnowledgeQuery.assessment("历史".repeat(500), "回答".repeat(500)).length() <= 500);
    }
}
