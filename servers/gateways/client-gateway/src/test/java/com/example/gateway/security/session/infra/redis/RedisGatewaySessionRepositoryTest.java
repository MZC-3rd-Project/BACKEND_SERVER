package com.example.gateway.security.session.infra.redis;

import com.example.gateway.config.GatewaySessionProperties;
import com.example.gateway.security.session.domain.GatewaySessionView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisGatewaySessionRepositoryTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveHashOperations<String, Object, Object> hashOperations;

    @Mock
    private ReactiveSetOperations<String, String> setOperations;

    private RedisGatewaySessionRepository repository;

    @BeforeEach
    void setUp() {
        GatewaySessionProperties properties = new GatewaySessionProperties();
        properties.setRedisKeyPrefix("gateway:sess:");
        properties.setUserSessionsKeyPrefix("gateway:user:sessions:");
        properties.setStatusField("status");
        properties.setRevokedStatus("REVOKED");

        repository = new RedisGatewaySessionRepository(redisTemplate, properties);
    }

    @Test
    void findStatusBySid_returnsStatus() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("gateway:sess:sid-1", "status")).thenReturn(Mono.just("ACTIVE"));

        String status = repository.findStatusBySid("sid-1").block();

        assertThat(status).isEqualTo("ACTIVE");
    }

    @Test
    void indexUserSession_addsSessionIdToUserSet() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.add("gateway:user:sessions:11", "sid-11")).thenReturn(Mono.just(1L));

        repository.indexUserSession(11L, "sid-11").block();

        verify(setOperations).add("gateway:user:sessions:11", "sid-11");
    }

    @Test
    void findSessionsByUserId_returnsSessionViews() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(setOperations.members("gateway:user:sessions:12")).thenReturn(Flux.just("sid-1", "sid-2"));
        when(hashOperations.get("gateway:sess:sid-1", "status")).thenReturn(Mono.just("ACTIVE"));
        when(hashOperations.get("gateway:sess:sid-2", "status")).thenReturn(Mono.empty());

        List<GatewaySessionView> sessions = repository.findSessionsByUserId(12L).collectList().block();

        assertThat(sessions).isNotNull();
        assertThat(sessions).extracting(GatewaySessionView::sessionId)
                .containsExactly("sid-1", "sid-2");
        assertThat(sessions).extracting(GatewaySessionView::status)
                .containsExactly("ACTIVE", "UNKNOWN");
    }

    @Test
    void revokeAllByUserId_marksEverySessionAsRevoked() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(setOperations.members("gateway:user:sessions:13")).thenReturn(Flux.just("sid-3", "sid-4"));
        when(hashOperations.put("gateway:sess:sid-3", "status", "REVOKED")).thenReturn(Mono.just(true));
        when(hashOperations.put("gateway:sess:sid-4", "status", "REVOKED")).thenReturn(Mono.just(true));

        Long revokedCount = repository.revokeAllByUserId(13L).block();

        assertThat(revokedCount).isEqualTo(2L);
        verify(hashOperations).put("gateway:sess:sid-3", "status", "REVOKED");
        verify(hashOperations).put("gateway:sess:sid-4", "status", "REVOKED");
    }

    @Test
    void indexUserSession_returnsWithoutRedisCallWhenInputInvalid() {
        repository.indexUserSession(null, "sid-14").block();
        repository.indexUserSession(14L, " ").block();

        verifyNoInteractions(setOperations);
    }
}
