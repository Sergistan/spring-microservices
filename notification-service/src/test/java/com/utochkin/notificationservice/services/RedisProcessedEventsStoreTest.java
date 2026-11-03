package com.utochkin.notificationservice.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RedisProcessedEventsStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RedisProcessedEventsStore store;

    @Test
    @DisplayName("tryAcquire: SET NX EX вернул true — событие захвачено первым")
    void tryAcquire_claimed_returnsTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        UUID eventId = UUID.randomUUID();

        assertThat(store.tryAcquire(eventId)).isTrue();
        verify(valueOperations).setIfAbsent(
                eq("notification:processed-event:" + eventId), anyString(), eq(Duration.ofHours(24)));
    }

    @Test
    @DisplayName("tryAcquire: SET NX EX вернул false — дубликат")
    void tryAcquire_duplicate_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertThat(store.tryAcquire(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("tryAcquire: неопределённый ответ Redis трактуется как «не захвачено»")
    void tryAcquire_nullResponse_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(null);

        assertThat(store.tryAcquire(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("tryAcquire: eventId=null — true без обращения к Redis (события без дедупа)")
    void tryAcquire_nullEventId_trueWithoutRedis() {
        assertThat(store.tryAcquire(null)).isTrue();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("release: удаляет ключ дедупликации")
    void release_deletesKey() {
        UUID eventId = UUID.randomUUID();

        store.release(eventId);

        verify(redisTemplate).delete("notification:processed-event:" + eventId);
    }

    @Test
    @DisplayName("release: eventId=null — без обращения к Redis")
    void release_nullEventId_noRedis() {
        store.release(null);
        verifyNoInteractions(redisTemplate);
    }
}
