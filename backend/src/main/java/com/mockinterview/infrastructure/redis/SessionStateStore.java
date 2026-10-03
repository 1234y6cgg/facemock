package com.mockinterview.infrastructure.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.agent.InterviewState;
import com.mockinterview.agent.Layer;
import com.mockinterview.agent.Stage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
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
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stage", state.getStage().name());
            m.put("layer", state.getLayer().name());
            m.put("currentProjectIdx", state.getCurrentProjectIdx());
            m.put("totalProjects", state.getTotalProjects());
            m.put("questionCount", state.getQuestionCount());
            m.put("consecutiveStuck", state.getConsecutiveStuck());
            m.put("difficulty", state.getDifficulty());
            m.put("pressureUsed", state.isPressureUsed());
            redis.opsForValue().set(KEY.formatted(sessionId),
                    objectMapper.writeValueAsString(m), TTL);
        } catch (Exception ignored) {
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
                    .currentProjectIdx(intOf(m.get("currentProjectIdx")))
                    .totalProjects(intOf(m.get("totalProjects")))
                    .questionCount(intOf(m.get("questionCount")))
                    .consecutiveStuck(intOf(m.get("consecutiveStuck")))
                    .difficulty(intOrDefault(m.get("difficulty"), 2))
                    .pressureUsed(boolOrDefault(m.get("pressureUsed"), false))
                    .build());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private int intOf(Object o) {
        return ((Number) o).intValue();
    }

    private int intOrDefault(Object o, int dft) {
        return o == null ? dft : ((Number) o).intValue();
    }

    private boolean boolOrDefault(Object o, boolean dft) {
        return o == null ? dft : (Boolean) o;
    }
}
