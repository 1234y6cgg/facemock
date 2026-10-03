package com.mockinterview.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewStateMachineTest {

    private final InterviewStateMachine machine = new InterviewStateMachine();

    private static final AnswerAssessment GOOD = new AnswerAssessment(4, 4, 4, false);
    private static final AnswerAssessment STUCK = new AnswerAssessment(1, 1, 1, true);

    private static InterviewState state(Stage stage, Layer layer, int idx, int total, int q, int stuck) {
        return InterviewState.builder()
                .stage(stage).layer(layer)
                .currentProjectIdx(idx).totalProjects(total)
                .questionCount(q).consecutiveStuck(stuck)
                .difficulty(2).pressureUsed(false)
                .build();
    }

    @Test
    void openingAnswerMovesToProjectDigAtL2() {
        InterviewState next = machine.apply(InterviewState.initial(2), GOOD);
        assertEquals(Stage.PROJECT_DIG, next.getStage());
        assertEquals(Layer.L2_SOLUTION, next.getLayer());
        assertEquals(1, next.getQuestionCount());
    }

    @Test
    void goodAnswerAdvancesLayer() {
        InterviewState s = state(Stage.PROJECT_DIG, Layer.L2_SOLUTION, 0, 2, 1, 0);
        InterviewState next = machine.apply(s, GOOD);
        assertEquals(Layer.L3_DETAILS, next.getLayer());
        assertEquals(0, next.getConsecutiveStuck());
    }

    @Test
    void goodAnswerAtLastLayerTriggersPressureFirst() {
        InterviewState s = state(Stage.PROJECT_DIG, Layer.L5_TRADEOFF, 0, 2, 4, 0);
        InterviewState next = machine.apply(s, GOOD);
        assertTrue(next.isPressureUsed());
        assertEquals(Layer.L5_TRADEOFF, next.getLayer());
    }

    @Test
    void goodAnswerAfterPressureJumpsToNextProject() {
        InterviewState s = state(Stage.PROJECT_DIG, Layer.L5_TRADEOFF, 0, 2, 5, 0)
                .toBuilder().pressureUsed(true).build();
        InterviewState next = machine.apply(s, GOOD);
        assertEquals(1, next.getCurrentProjectIdx());
        assertEquals(Layer.L1_BACKGROUND, next.getLayer());
        assertFalse(next.isPressureUsed());
    }

    @Test
    void goodAnswerAtLastProjectAfterPressureGoesExtension() {
        InterviewState s = state(Stage.PROJECT_DIG, Layer.L5_TRADEOFF, 1, 2, 5, 0)
                .toBuilder().pressureUsed(true).build();
        InterviewState next = machine.apply(s, GOOD);
        assertEquals(Stage.EXTENSION, next.getStage());
    }

    @Test
    void singleStuckStaysSameLayer() {
        InterviewState s = state(Stage.PROJECT_DIG, Layer.L2_SOLUTION, 0, 2, 2, 0);
        InterviewState next = machine.apply(s, STUCK);
        assertEquals(Layer.L2_SOLUTION, next.getLayer());
        assertEquals(1, next.getConsecutiveStuck());
    }

    @Test
    void doubleStuckJumps() {
        InterviewState s = state(Stage.PROJECT_DIG, Layer.L2_SOLUTION, 0, 2, 3, 1);
        InterviewState next = machine.apply(s, STUCK);
        assertEquals(1, next.getCurrentProjectIdx());
        assertEquals(Layer.L1_BACKGROUND, next.getLayer());
        assertEquals(0, next.getConsecutiveStuck());
    }

    @Test
    void goodAfterStuckResetsAndAdvances() {
        InterviewState s = state(Stage.PROJECT_DIG, Layer.L2_SOLUTION, 0, 2, 3, 1);
        InterviewState next = machine.apply(s, GOOD);
        assertEquals(Layer.L3_DETAILS, next.getLayer());
        assertEquals(0, next.getConsecutiveStuck());
    }

    @Test
    void maxQuestionsCloses() {
        InterviewState s = state(Stage.PROJECT_DIG, Layer.L2_SOLUTION, 0, 2, 13, 0);
        InterviewState next = machine.apply(s, GOOD);
        assertEquals(Stage.CLOSING, next.getStage());
    }
}
