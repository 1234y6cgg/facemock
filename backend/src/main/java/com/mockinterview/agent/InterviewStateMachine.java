package com.mockinterview.agent;

import org.springframework.stereotype.Component;

@Component
public class InterviewStateMachine {

    public static final int MAX_QUESTIONS = 14;
    public static final int MAX_STUCK = 2;
    public static final int MAX_DIFFICULTY = 5;
    public static final int MIN_DIFFICULTY = 1;

    public InterviewState apply(InterviewState state, AnswerAssessment assessment) {
        if (state.getStage() == Stage.CLOSING) {
            return state;
        }

        InterviewState advanced = state.toBuilder()
                .questionCount(state.getQuestionCount() + 1)
                .build();

        if (advanced.getQuestionCount() >= MAX_QUESTIONS) {
            return advanced.toBuilder().stage(Stage.CLOSING).build();
        }

        if (state.getStage() == Stage.OPENING) {
            return advanced.toBuilder()
                    .stage(Stage.PROJECT_DIG)
                    .layer(Layer.L2_SOLUTION)
                    .consecutiveStuck(0)
                    .build();
        }

        int difficulty = adjustDifficulty(advanced.getDifficulty(), assessment);

        if (assessment.stuck()) {
            int stuck = state.getConsecutiveStuck() + 1;
            if (stuck >= MAX_STUCK) {
                return jump(advanced.toBuilder()
                        .consecutiveStuck(0)
                        .difficulty(difficulty)
                        .build());
            }
            return advanced.toBuilder()
                    .consecutiveStuck(stuck)
                    .difficulty(difficulty)
                    .build();
        }

        InterviewState reset = advanced.toBuilder()
                .consecutiveStuck(0)
                .difficulty(difficulty)
                .build();

        if (state.getStage() == Stage.EXTENSION) {
            return reset.toBuilder().stage(Stage.CLOSING).build();
        }

        if (!state.getLayer().isLast()) {
            return reset.toBuilder().layer(state.getLayer().next()).build();
        }

        if (!state.isPressureUsed()) {
            return reset.toBuilder().pressureUsed(true).layer(Layer.L5_TRADEOFF).build();
        }

        return jump(reset);
    }

    private int adjustDifficulty(int current, AnswerAssessment a) {
        int score = (a.depth() + a.accuracy() + a.completeness()) / 3;
        int next = current;
        if (score >= 4) next++;
        else if (score <= 1) next--;
        return Math.max(MIN_DIFFICULTY, Math.min(MAX_DIFFICULTY, next));
    }

    private InterviewState jump(InterviewState s) {
        if (s.getCurrentProjectIdx() + 1 < s.getTotalProjects()) {
            return s.toBuilder()
                    .currentProjectIdx(s.getCurrentProjectIdx() + 1)
                    .layer(Layer.L1_BACKGROUND)
                    .pressureUsed(false)
                    .build();
        }
        return s.toBuilder().stage(Stage.EXTENSION).layer(Layer.L3_DETAILS).build();
    }
}
