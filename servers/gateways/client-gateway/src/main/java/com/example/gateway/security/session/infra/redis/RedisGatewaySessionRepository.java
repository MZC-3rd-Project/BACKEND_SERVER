package com.example.gateway.security.session.infra.redis;

import com.example.gateway.config.GatewaySessionProperties;
import com.example.gateway.security.session.application.port.GatewaySessionRepository;
import com.example.gateway.security.session.domain.GatewaySessionView;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "gateway.session", name = "enabled", havingValue = "true")
public class RedisGatewaySessionRepository implements GatewaySessionRepository {

    private static final String STATUS_UNKNOWN = "UNKNOWN";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewaySessionProperties sessionProperties;

    @Override
    public Mono<String> findStatusBySid(String sessionId) {
        return redisTemplate.opsForHash()
                .get(sessionKey(sessionId), sessionProperties.getStatusField())
                .map(String::valueOf);
    }

    @Override
    public Mono<Void> indexUserSession(Long userId, String sessionId) {
        if (userId == null || sessionId == null || sessionId.isBlank()) {
            return Mono.empty();
        }
        return redisTemplate.opsForSet()
                .add(userSessionsKey(userId), sessionId)
                .then();
    }

    @Override
    public Flux<GatewaySessionView> findSessionsByUserId(Long userId) {
        if (userId == null) {
            return Flux.empty();
        }
        return redisTemplate.opsForSet()
                .members(userSessionsKey(userId))
                .flatMap(sessionId -> findStatusBySid(sessionId)
                        .defaultIfEmpty(STATUS_UNKNOWN)
                        .map(status -> new GatewaySessionView(sessionId, status)));
    }

    @Override
    public Mono<Long> revokeAllByUserId(Long userId) {
        if (userId == null) {
            return Mono.just(0L);
        }
        return redisTemplate.opsForSet()
                .members(userSessionsKey(userId))
                .flatMap(sessionId -> markRevoked(sessionId).thenReturn(sessionId))
                .count();
    }

    private Mono<Void> markRevoked(String sessionId) {
        return redisTemplate.opsForHash()
                .put(sessionKey(sessionId), sessionProperties.getStatusField(), sessionProperties.getRevokedStatus())
                .then();
    }

    private String sessionKey(String sessionId) {
        return sessionProperties.getRedisKeyPrefix() + sessionId;
    }

    private String userSessionsKey(Long userId) {
        return sessionProperties.getUserSessionsKeyPrefix() + userId;
    }
}
