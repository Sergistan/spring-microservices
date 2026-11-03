package com.utochkin.notificationservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Log4j2
public class RedisProcessedEventsStore {

    private static final Duration TTL = Duration.ofHours(24);
    private static final String KEY_PREFIX = "notification:processed-event:";

    private final StringRedisTemplate redisTemplate;

    public boolean tryAcquire(UUID eventId) {
        if (eventId == null) {
            return true;
        }
        String key = KEY_PREFIX + eventId;
        Boolean claimed = redisTemplate.opsForValue().setIfAbsent(key, Instant.now().toString(), TTL);
        log.debug("RedisProcessedEventsStore: tryAcquire {} -> {}", key, claimed);
        return Boolean.TRUE.equals(claimed);
    }

    public void release(UUID eventId) {
        if (eventId == null) {
            return;
        }
        String key = KEY_PREFIX + eventId;
        Boolean deleted = redisTemplate.delete(key);
        log.debug("RedisProcessedEventsStore: release {} -> {}", key, deleted);
    }
}
