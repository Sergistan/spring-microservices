package com.utochkin.notificationservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataAccessException;
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
        try {
            Boolean claimed = redisTemplate.opsForValue().setIfAbsent(key, Instant.now().toString(), TTL);
            log.debug("RedisProcessedEventsStore: tryAcquire {} -> {}", key, claimed);
            return Boolean.TRUE.equals(claimed);
        } catch (DataAccessException e) {
            log.warn("RedisProcessedEventsStore: Redis недоступен ({}), дедупликация пропущена для {} — fail-open", e.getMessage(), key);
            return true;
        }
    }

    public void release(UUID eventId) {
        if (eventId == null) {
            return;
        }
        String key = KEY_PREFIX + eventId;
        try {
            Boolean deleted = redisTemplate.delete(key);
            log.debug("RedisProcessedEventsStore: release {} -> {}", key, deleted);
        } catch (DataAccessException e) {
            log.warn("RedisProcessedEventsStore: Redis недоступен при release ({}), ключ {} истечёт по TTL — fail-open", e.getMessage(), key);
        }
    }
}
