package com.mockinterview.service.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.agent.*;
import com.mockinterview.capability.knowledge.TechKnowledgeService;
import com.mockinterview.capability.memory.LongTermMemoryService;
import org.junit.jupiter.api.Test;
import com.mockinterview.domain.ResumeStructured;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class InterviewOrchestratorTest {

    private final InterviewStateMachine machine = new InterviewStateMachine();
    private final AssessmentParser parser = new AssessmentParser(new ObjectMapper());
    private final ObjectMapper mapper = new ObjectMapper();
    private final FollowUpAgent followUp = mock(FollowUpAgent.class);
    private final PressureAgent pressure = mock(PressureAgent.class);
    private final ScenarioAgent scenario = mock(ScenarioAgent.class);
    private final FeedbackAgent feedback = mock(FeedbackAgent.class);
    private final TechKnowledgeService knowledge = mock(TechKnowledgeService.class);
    private final LongTermMemoryService memory = mock(LongTermMemoryService.class);

    private final AssessmentAgent goodAgent = (history, answer, references) ->
            "{\"depth\":4,\"accuracy\":4,\"completeness\":4,\"stuck\":false}";
    private final AssessmentAgent stuckAgent = (history, answer, references) ->
            "{\"depth\":1,\"accuracy\":1,\"completeness\":1,\"stuck\":true}";

    private static InterviewState state(Stage stage, Layer layer, int idx, int total, int q, int stuck) {
        return InterviewState.builder()
                .stage(stage).layer(layer)
                .currentProjectIdx(idx).totalProjects(total)
                .questionCount(q).consecutiveStuck(stuck)
                .difficulty(2).pressureUsed(false)
                .build();
    }

    private InterviewOrchestrator orchestrator(AssessmentAgent agent) {
        return new InterviewOrchestrator(machine, agent, parser, followUp,
                pressure, scenario, feedback, knowledge, memory, mapper);
    }

    @Test
    void goodAnswerAdvancesLayerThroughOrchestrator() {
        InterviewOrchestrator o = orchestrator(goodAgent);
        InterviewState s = state(Stage.PROJECT_DIG, Layer.L2_SOLUTION, 0, 2, 1, 0);

        AnswerAssessment a = o.assess("历史", "我的回答");
        InterviewState next = o.nextState(s, a);

        assertEquals(Layer.L3_DETAILS, next.getLayer());
        assertEquals(0, next.getConsecutiveStuck());
    }

    @Test
    void stuckAnswerStaysSameLayer() {
        InterviewOrchestrator o = orchestrator(stuckAgent);
        InterviewState s = state(Stage.PROJECT_DIG, Layer.L2_SOLUTION, 0, 2, 2, 0);

        InterviewState next = o.nextState(s, o.assess("历史", "不会"));

        assertEquals(Layer.L2_SOLUTION, next.getLayer());
        assertEquals(1, next.getConsecutiveStuck());
    }

    @Test
    void allQuestionBranchesReceiveRetrievedSources() {
        when(knowledge.defaultTopK()).thenReturn(3);
        when(knowledge.retrieve(anyString(), eq(3))).thenReturn("[K1] Redis 官方知识");
        when(memory.askedTopics(7L)).thenReturn(Set.of());
        var resume = ResumeStructured.builder().skills(List.of("Redis"))
                .projects(List.of(ResumeStructured.Project.builder().name("秒杀")
                        .techStack(List.of("Redis")).build())).build();
        var o = orchestrator(goodAgent);
        var regular = state(Stage.PROJECT_DIG, Layer.L3_DETAILS, 0, 1, 3, 0);
        o.nextQuestion(resume, regular, "库存怎么扣？", 7L, "后端", "");
        verify(followUp).generateQuestion(eq("后端"), anyString(), anyString(), anyString(),
                eq(Layer.L3_DETAILS.label()), anyInt(), anyString(), eq("[K1] Redis 官方知识"), eq("库存怎么扣？"));
        var pressureState = state(Stage.PROJECT_DIG, Layer.L5_TRADEOFF, 0, 1, 4, 0);
        pressureState = pressureState.toBuilder().pressureUsed(true).build();
        o.nextQuestion(resume, pressureState, "库存怎么扣？", 7L, "后端", "");
        verify(pressure).press(eq("后端"), anyString(), anyString(), anyString(), anyString(),
                anyInt(), eq("[K1] Redis 官方知识"));
        o.nextQuestion(resume, state(Stage.EXTENSION, Layer.L1_BACKGROUND, 0, 1, 5, 0),
                "库存怎么扣？", 7L, "后端", "");
        verify(scenario).pose(eq("后端"), anyString(), anyString(), anyString(), anyString(),
                anyInt(), eq("[K1] Redis 官方知识"));
    }

    @Test
    void assessmentReceivesEvidenceRelatedToLatestAnswer() {
        when(knowledge.defaultTopK()).thenReturn(3);
        when(knowledge.retrieve(contains("Lua"), eq(3))).thenReturn("[K1] 原子执行");
        AssessmentAgent agent = mock(AssessmentAgent.class);
        when(agent.assessAnswer(anyString(), anyString(), anyString()))
                .thenReturn("{\"depth\":3,\"accuracy\":4,\"completeness\":3,\"stuck\":false}");
        orchestrator(agent).assess("面试官：如何扣库存？", "用 Lua");
        verify(agent).assessAnswer("面试官：如何扣库存？", "用 Lua", "[K1] 原子执行");
    }
}
