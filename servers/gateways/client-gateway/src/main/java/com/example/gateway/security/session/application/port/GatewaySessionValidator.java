package com.example.gateway.security.session.application.port;

import com.example.gateway.security.GatewaySessionPrincipal;
import com.example.gateway.security.session.domain.SessionValidationResult;
import reactor.core.publisher.Mono;

public interface GatewaySessionValidator {

    Mono<SessionValidationResult> validate(GatewaySessionPrincipal principal);
}
