package com.example.gateway.security.session.domain;

import java.util.List;

public record GatewaySessionListResponse(Long userId, long sessionCount, List<GatewaySessionView> sessions) {
}
