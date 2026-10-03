package com.mockinterview.capability.memory;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Service
public class LongTermMemoryService {

    private static final String WEAKNESS_KEY = "weakness:global";
    private static final String ASKED_PREFIX = "session:%d:asked";
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;

    public LongTermMemoryService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Set<String> knownWeaknesses() {
        return redis.opsForSet().members(WEAKNESS_KEY);
    }

    public void rememberWeaknesses(List<String> weaknesses) {
        if (weaknesses == null || weaknesses.isEmpty()) return;
        redis.opsForSet().add(WEAKNESS_KEY, weaknesses.toArray(new String[0]));
    }

    public void rememberTopic(Long sessionId, String topic) {
        if (topic == null || topic.isBlank()) return;
        redis.opsForSet().add(ASKED_PREFIX.formatted(sessionId), topic);
    }

    public Set<String> askedTopics(Long sessionId) {
        return redis.opsForSet().members(ASKED_PREFIX.formatted(sessionId));
    }
}
