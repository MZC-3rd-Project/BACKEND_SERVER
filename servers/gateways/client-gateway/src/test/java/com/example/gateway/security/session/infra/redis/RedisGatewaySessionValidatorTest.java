package com.example.gateway.security.session.infra.redis;

import com.example.gateway.config.GatewaySessionProperties;
import com.example.gateway.security.GatewaySessionPrincipal;
import com.example.gateway.security.session.application.port.GatewaySessionRepository;
import com.example.gateway.security.session.domain.SessionValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisGatewaySessionValidatorTest {

    @Mock
    private GatewaySessionRepository sessionRepository;

    private RedisGatewaySessionValidator validator;

    @BeforeEach
    void setUp() {
        GatewaySessionProperties properties = new GatewaySessionProperties();
        properties.setRedisKeyPrefix("gateway:sess:");
        properties.setStatusField("status");
        properties.setActiveStatus("ACTIVE");

        validator = new RedisGatewaySessionValidator(sessionRepository, properties);
    }

    @Test
    void validate_allowsWhenPrincipalIsNull() {
        SessionValidationResult result = validator.validate(null).block();

        assertThat(result).isNotNull();
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void validate_deniesWhenSessionIdMissing() {
        GatewaySessionPrincipal principal = new GatewaySessionPrincipal(11L, List.of("USER"), null);

        SessionValidationResult result = validator.validate(principal).block();

        assertThat(result).isNotNull();
        assertThat(result.allowed()).isFalse();
        assertThat(result.code()).isEqualTo("GW-AUTH-005");
        verifyNoInteractions(sessionRepository);
    }

    @Test
    void validate_allowsWhenStatusIsActive() {
        GatewaySessionPrincipal principal = new GatewaySessionPrincipal(12L, List.of("USER"), "sid-12");
        when(sessionRepository.findStatusBySid("sid-12")).thenReturn(Mono.just("ACTIVE"));
        when(sessionRepository.indexUserSession(12L, "sid-12")).thenReturn(Mono.empty());

        SessionValidationResult result = validator.validate(principal).block();

        assertThat(result).isNotNull();
        assertThat(result.allowed()).isTrue();
        verify(sessionRepository).indexUserSession(12L, "sid-12");
    }

    @Test
    void validate_activatesAndAllowsWhenStatusMissing() {
        GatewaySessionPrincipal principal = new GatewaySessionPrincipal(22L, List.of("USER"), "sid-22");
        when(sessionRepository.findStatusBySid("sid-22")).thenReturn(Mono.empty());
        when(sessionRepository.activateSession(22L, "sid-22")).thenReturn(Mono.empty());

        SessionValidationResult result = validator.validate(principal).block();

        assertThat(result).isNotNull();
        assertThat(result.allowed()).isTrue();
        verify(sessionRepository).activateSession(22L, "sid-22");
    }

    @Test
    void validate_deniesWhenStatusIsRevoked() {
        GatewaySessionPrincipal principal = new GatewaySessionPrincipal(13L, List.of("USER"), "sid-13");
        when(sessionRepository.findStatusBySid("sid-13")).thenReturn(Mono.just("REVOKED"));

        SessionValidationResult result = validator.validate(principal).block();

        assertThat(result).isNotNull();
        assertThat(result.allowed()).isFalse();
        assertThat(result.code()).isEqualTo("GW-AUTH-006");
    }

    @Test
    void validate_allowsWhenIndexingFails() {
        GatewaySessionPrincipal principal = new GatewaySessionPrincipal(14L, List.of("USER"), "sid-14");
        when(sessionRepository.findStatusBySid("sid-14")).thenReturn(Mono.just("ACTIVE"));
        when(sessionRepository.indexUserSession(anyLong(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("redis write error")));

        SessionValidationResult result = validator.validate(principal).block();

        assertThat(result).isNotNull();
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void validate_deniesWhenActivationFails() {
        GatewaySessionPrincipal principal = new GatewaySessionPrincipal(23L, List.of("USER"), "sid-23");
        when(sessionRepository.findStatusBySid("sid-23")).thenReturn(Mono.empty());
        when(sessionRepository.activateSession(anyLong(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("redis write error")));

        SessionValidationResult result = validator.validate(principal).block();

        assertThat(result).isNotNull();
        assertThat(result.allowed()).isFalse();
        assertThat(result.code()).isEqualTo("GW-AUTH-007");
    }
}
