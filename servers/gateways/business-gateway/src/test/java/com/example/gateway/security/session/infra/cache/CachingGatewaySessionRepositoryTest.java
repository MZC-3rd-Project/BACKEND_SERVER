package com.example.gateway.security.session.infra.cache;

import com.example.gateway.config.BusinessGatewaySessionProperties;
import com.example.gateway.security.session.application.port.GatewaySessionRepository;
import com.example.gateway.security.session.domain.GatewayServerSession;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CachingGatewaySessionRepositoryTest {

    @Test
    void shouldReuseCachedSessionAfterFirstRedisLookup() {
        GatewaySessionRepository delegate = mock(GatewaySessionRepository.class);
        BusinessGatewaySessionProperties sessionProperties = new BusinessGatewaySessionProperties();
        CachingGatewaySessionRepository repository = new CachingGatewaySessionRepository(
                delegate,
                Caffeine.newBuilder().build(),
                sessionProperties
        );
        GatewayServerSession session = new GatewayServerSession(
                "sid-1",
                42L,
                List.of("USER"),
                "kc-sub",
                "kc-sid",
                "seller@example.com",
                "enc-token",
                "hash-token",
                "family-1",
                sessionProperties.getActiveStatus(),
                1L,
                2L,
                3L,
                4L
        );
        when(delegate.findSessionById("sid-1")).thenReturn(Mono.just(session));

        GatewayServerSession firstLookup = repository.findSessionById("sid-1").block();
        GatewayServerSession secondLookup = repository.findSessionById("sid-1").block();

        Assertions.assertEquals(session, firstLookup);
        Assertions.assertEquals(session, secondLookup);
        verify(delegate, times(1)).findSessionById("sid-1");
    }
}
