package com.mockinterview.capability.ratelimit;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RateLimiterService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> slidingWindowScript;

    public RateLimiterService(StringRedisTemplate redis) {
        this.redis = redis;
        this.slidingWindowScript = new DefaultRedisScript<>();
        this.slidingWindowScript.setLocation(new ClassPathResource("scripts/ratelimit.lua"));
        this.slidingWindowScript.setResultType(Long.class);
    }

    public boolean allow(String bucket, String ip, int limitPerWindow, int windowSec) {
        String key = "rl:sw:" + bucket + ":" + ip;
        long now = System.currentTimeMillis();
        Long result = redis.execute(slidingWindowScript, List.of(key),
                String.valueOf(now),
                String.valueOf(windowSec * 1000L),
                String.valueOf(limitPerWindow),
                UUID.randomUUID().toString());
        return Long.valueOf(1L).equals(result);
    }
}
