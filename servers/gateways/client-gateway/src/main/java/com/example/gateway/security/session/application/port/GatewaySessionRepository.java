package com.example.gateway.security.session.application.port;

import com.example.gateway.security.session.domain.GatewaySessionView;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface GatewaySessionRepository {

    Mono<String> findStatusBySid(String sessionId);

    Mono<Void> activateSession(Long userId, String sessionId);

    Mono<Void> indexUserSession(Long userId, String sessionId);

    Flux<GatewaySessionView> findSessionsByUserId(Long userId);

    Mono<Long> revokeAllByUserId(Long userId);
}
