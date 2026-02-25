package com.example.gateway.security.session.application.port;

import com.example.gateway.security.session.domain.GatewayRefreshTokenFamilyState;
import com.example.gateway.security.session.domain.RefreshTokenFamilyRotateResult;
import reactor.core.publisher.Mono;

public interface GatewayRefreshTokenFamilyRepository {

    Mono<GatewayRefreshTokenFamilyState> findByFamilyId(String familyId);

    Mono<Void> upsert(String familyId,
                      Long userId,
                      String sessionId,
                      String currentRefreshTokenHash,
                      boolean reuseDetected,
                      long rotatedAtEpochMillis);

    Mono<RefreshTokenFamilyRotateResult> rotateIfCurrentHashMatches(String familyId,
                                                                    String expectedCurrentHash,
                                                                    String nextRefreshTokenHash,
                                                                    Long userId,
                                                                    String sessionId,
                                                                    long rotatedAtEpochMillis);

    Mono<Void> markReuseDetected(String familyId, long detectedAtEpochMillis);
}
