package com.example.gateway.security.session.application.port;

import reactor.core.publisher.Mono;

public interface GatewayKeycloakSessionClient {

    Mono<Boolean> logoutUserSessions(Long userId);
}
