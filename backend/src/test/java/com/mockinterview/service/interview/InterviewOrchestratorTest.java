package com.mockinterview.service.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.agent.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class InterviewOrchestratorTest {

    private final InterviewStateMachine machine = new InterviewStateMachine();
    private final AssessmentParser parser = new AssessmentParser(new ObjectMapper());
    private final ObjectMapper mapper = new ObjectMapper();
    private final FollowUpAgent followUp = mock(FollowUpAgent.class);

    private final AssessmentAgent goodAgent = (history, answer) ->
            "{\"depth\":4,\"accuracy\":4,\"completeness\":4,\"stuck\":false}";
    private final AssessmentAgent stuckAgent = (history, answer) ->
            "{\"depth\":1,\"accuracy\":1,\"completeness\":1,\"stuck\":true}";

    private static InterviewState state(Stage stage, Layer layer, int idx, int total, int q, int stuck) {
        return InterviewState.builder()
                .stage(stage).layer(layer)
                .currentProjectIdx(idx).totalProjects(total)
                .questionCount(q).consecutiveStuck(stuck)
                .build();
    }

    @Test
    void goodAnswerAdvancesLayerThroughOrchestrator() {
        InterviewOrchestrator o = new InterviewOrchestrator(machine, goodAgent, parser, followUp, mapper);
        InterviewState s = state(Stage.PROJECT_DIG, Layer.L2_SOLUTION, 0, 2, 1, 0);

        AnswerAssessment a = o.assess("历史", "我的回答");
        InterviewState next = o.nextState(s, a);

        assertEquals(Layer.L3_DETAILS, next.getLayer());
        assertEquals(0, next.getConsecutiveStuck());
    }

    @Test
    void stuckAnswerStaysSameLayer() {
        InterviewOrchestrator o = new InterviewOrchestrator(machine, stuckAgent, parser, followUp, mapper);
        InterviewState s = state(Stage.PROJECT_DIG, Layer.L2_SOLUTION, 0, 2, 2, 0);

        InterviewState next = o.nextState(s, o.assess("历史", "不会"));

        assertEquals(Layer.L2_SOLUTION, next.getLayer());
        assertEquals(1, next.getConsecutiveStuck());
    }
}
