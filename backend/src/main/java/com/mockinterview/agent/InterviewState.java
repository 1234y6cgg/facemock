package com.mockinterview.agent;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class InterviewState {

    Stage stage;
    Layer layer;
    int currentProjectIdx;
    int totalProjects;
    int questionCount;
    int consecutiveStuck;
    int difficulty;
    boolean pressureUsed;

    public static InterviewState initial(int totalProjects) {
        return InterviewState.builder()
                .stage(Stage.OPENING)
                .layer(Layer.L1_BACKGROUND)
                .currentProjectIdx(0)
                .totalProjects(totalProjects)
                .questionCount(0)
                .consecutiveStuck(0)
                .difficulty(2)
                .pressureUsed(false)
                .build();
    }
}
