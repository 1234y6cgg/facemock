package com.mockinterview.agent;

import org.springframework.stereotype.Component;

@Component
public class InterviewStateMachine {

    public static final int MAX_QUESTIONS = 8;
    public static final int MAX_STUCK = 2;

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

        // 开场（L1 背景）已答完，进入项目深挖，从 L2 方案开始
        if (state.getStage() == Stage.OPENING) {
            return advanced.toBuilder()
                    .stage(Stage.PROJECT_DIG)
                    .layer(Layer.L2_SOLUTION)
                    .consecutiveStuck(0)
                    .build();
        }

        if (assessment.stuck()) {
            int stuck = state.getConsecutiveStuck() + 1;
            if (stuck >= MAX_STUCK) {
                return jump(advanced.toBuilder().consecutiveStuck(0).build());
            }
            return advanced.toBuilder().consecutiveStuck(stuck).build();
        }

        InterviewState reset = advanced.toBuilder().consecutiveStuck(0).build();
        if (!state.getLayer().isLast()) {
            return reset.toBuilder().layer(state.getLayer().next()).build();
        }
        return jump(reset);
    }

    private InterviewState jump(InterviewState s) {
        if (s.getCurrentProjectIdx() + 1 < s.getTotalProjects()) {
            return s.toBuilder()
                    .currentProjectIdx(s.getCurrentProjectIdx() + 1)
                    .layer(Layer.L1_BACKGROUND)
                    .build();
        }
        return s.toBuilder().stage(Stage.CLOSING).build();
    }
}
