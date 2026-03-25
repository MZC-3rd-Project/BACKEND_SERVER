package com.example.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.session")
public class GatewaySessionProperties {

    private String redisKeyPrefix = "gateway:sess:";
    private String authStateKeyPrefix = "gateway:auth:state:";
    private String userSessionsKeyPrefix = "gateway:user:sessions:";
    private String activeStatus = "ACTIVE";
    private String revokedStatus = "REVOKED";
    private String sessionCookieName = "SESSION";
    private String sessionCookiePath = "/";
    private String refreshTokenHashPepper = "";
    private String refreshTokenEncryptionSecret = "";
    private long authStateTtlSeconds = 300L;
}
