package com.example.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.session")
public class BusinessGatewaySessionProperties {

    private boolean enabled = false;
    private boolean relayHeaderEnabled = true;
    // client-gateway와 동일한 Redis 키 프리픽스 사용 → 세션 공유
    private String redisKeyPrefix = "gateway:sess:";
    private String statusField = "status";
    private String activeStatus = "ACTIVE";
    private String revokedStatus = "REVOKED";
    private String sessionCookieName = "SESSION";
    private String sessionCookiePath = "/";
}
