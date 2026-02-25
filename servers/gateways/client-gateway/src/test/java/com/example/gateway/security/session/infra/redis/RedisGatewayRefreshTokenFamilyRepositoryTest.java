package com.example.gateway.security.session.infra.redis;

import com.example.gateway.config.GatewaySessionProperties;
import com.example.gateway.security.session.domain.GatewayRefreshTokenFamilyState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisGatewayRefreshTokenFamilyRepositoryTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveHashOperations<String, Object, Object> hashOperations;

    private RedisGatewayRefreshTokenFamilyRepository repository;

    @BeforeEach
    void setUp() {
        GatewaySessionProperties properties = new GatewaySessionProperties();
        properties.setRefreshFamilyKeyPrefix("gateway:rtfam:");
        properties.setRefreshUidField("uid");
        properties.setRefreshSidField("sid");
        properties.setRefreshCurrentHashField("currentRtHash");
        properties.setRefreshRotatedAtField("rotatedAt");
        properties.setRefreshReuseDetectedField("reuseDetected");
        properties.setRefreshReuseDetectedAtField("reuseDetectedAt");
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        repository = new RedisGatewayRefreshTokenFamilyRepository(redisTemplate, properties);
    }

    @Test
    void findByFamilyId_returnsStateWhenCurrentHashExists() {
        String key = "gateway:rtfam:family-1";
        when(hashOperations.get(key, "uid")).thenReturn(Mono.just("11"));
        when(hashOperations.get(key, "sid")).thenReturn(Mono.just("sid-11"));
        when(hashOperations.get(key, "currentRtHash")).thenReturn(Mono.just("hash-11"));
        when(hashOperations.get(key, "reuseDetected")).thenReturn(Mono.just("false"));

        GatewayRefreshTokenFamilyState state = repository.findByFamilyId("family-1").block();

        assertThat(state).isNotNull();
        assertThat(state.familyId()).isEqualTo("family-1");
        assertThat(state.userId()).isEqualTo(11L);
        assertThat(state.sessionId()).isEqualTo("sid-11");
        assertThat(state.currentRefreshTokenHash()).isEqualTo("hash-11");
        assertThat(state.reuseDetected()).isFalse();
    }

    @Test
    void findByFamilyId_returnsEmptyWhenCurrentHashMissing() {
        String key = "gateway:rtfam:family-2";
        when(hashOperations.get(key, "uid")).thenReturn(Mono.just("22"));
        when(hashOperations.get(key, "sid")).thenReturn(Mono.just("sid-22"));
        when(hashOperations.get(key, "currentRtHash")).thenReturn(Mono.empty());
        when(hashOperations.get(key, "reuseDetected")).thenReturn(Mono.just("false"));

        GatewayRefreshTokenFamilyState state = repository.findByFamilyId("family-2").block();

        assertThat(state).isNull();
    }

    @Test
    void upsert_writesExpectedFields() {
        String key = "gateway:rtfam:family-3";
        when(hashOperations.putAll(key, Map.of(
                "uid", "33",
                "sid", "sid-33",
                "currentRtHash", "hash-33",
                "rotatedAt", "1000",
                "reuseDetected", "false"
        ))).thenReturn(Mono.just(true));

        repository.upsert("family-3", 33L, "sid-33", "hash-33", false, 1000L).block();

        ArgumentCaptor<Map<Object, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(eq(key), captor.capture());
        assertThat(captor.getValue()).containsEntry("uid", "33");
        assertThat(captor.getValue()).containsEntry("sid", "sid-33");
        assertThat(captor.getValue()).containsEntry("currentRtHash", "hash-33");
        assertThat(captor.getValue()).containsEntry("rotatedAt", "1000");
        assertThat(captor.getValue()).containsEntry("reuseDetected", "false");
    }

    @Test
    void markReuseDetected_setsReuseFlag() {
        String key = "gateway:rtfam:family-4";
        when(hashOperations.putAll(key, Map.of(
                "reuseDetected", "true",
                "reuseDetectedAt", "2000"
        ))).thenReturn(Mono.just(true));

        repository.markReuseDetected("family-4", 2000L).block();

        verify(hashOperations).putAll(key, Map.of(
                "reuseDetected", "true",
                "reuseDetectedAt", "2000"
        ));
    }
}
