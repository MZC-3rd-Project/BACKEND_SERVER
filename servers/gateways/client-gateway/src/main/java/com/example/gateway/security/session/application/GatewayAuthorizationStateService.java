package com.example.gateway.security.session.application;

import com.example.gateway.config.GatewaySessionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class GatewayAuthorizationStateService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewaySessionProperties sessionProperties;

    public Mono<Void> save(String state, String redirectPath) {
        if (!StringUtils.hasText(state)) {
            return Mono.empty();
        }
        String value = StringUtils.hasText(redirectPath) ? redirectPath : "/";
        return redisTemplate.opsForValue()
                .set(key(state), value, Duration.ofSeconds(sessionProperties.getAuthStateTtlSeconds()))
                .then();
    }

    public Mono<String> consume(String state) {
        if (!StringUtils.hasText(state)) {
            return Mono.empty();
        }
        String key = key(state);
        return redisTemplate.opsForValue()
                .get(key)
                .flatMap(value -> redisTemplate.delete(key).thenReturn(value));
    }

    private String key(String state) {
        return sessionProperties.getAuthStateKeyPrefix() + state;
    }
}
