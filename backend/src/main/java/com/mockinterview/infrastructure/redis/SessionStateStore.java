package com.mockinterview.infrastructure.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.agent.InterviewState;
import com.mockinterview.agent.Layer;
import com.mockinterview.agent.Stage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Component
public class SessionStateStore {

    private static final String KEY = "session:%d:state";
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public SessionStateStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void saveState(Long sessionId, InterviewState state) {
        try {
            Map<String, Object> m = Map.of(
                    "stage", state.getStage().name(),
                    "layer", state.getLayer().name(),
                    "currentProjectIdx", state.getCurrentProjectIdx(),
                    "totalProjects", state.getTotalProjects(),
                    "questionCount", state.getQuestionCount(),
                    "consecutiveStuck", state.getConsecutiveStuck());
            redis.opsForValue().set(KEY.formatted(sessionId),
                    objectMapper.writeValueAsString(m), TTL);
        } catch (Exception ignored) {
            // Redis 不可用时降级为仅用 MySQL
        }
    }

    public Optional<InterviewState> loadState(Long sessionId) {
        String json = redis.opsForValue().get(KEY.formatted(sessionId));
        if (json == null) {
            return Optional.empty();
        }
        try {
            Map<String, Object> m = objectMapper.readValue(json, new TypeReference<>() {
            });
            return Optional.of(InterviewState.builder()
                    .stage(Stage.valueOf((String) m.get("stage")))
                    .layer(Layer.valueOf((String) m.get("layer")))
                    .currentProjectIdx(((Number) m.get("currentProjectIdx")).intValue())
                    .totalProjects(((Number) m.get("totalProjects")).intValue())
                    .questionCount(((Number) m.get("questionCount")).intValue())
                    .consecutiveStuck(((Number) m.get("consecutiveStuck")).intValue())
                    .build());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
