package com.example.gateway.config;

import com.example.contracts.http.HttpHeaderNames;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.session")
public class GatewaySessionProperties {

    private boolean enabled = false;
    private boolean relayHeaderEnabled = true;
    private boolean opsEnabled = false;
    private boolean publicRefreshEnabled = false;
    private boolean keycloakLogoutEnabled = false;
    private boolean trustedHeaderAuthEnabled = false;
    private String redisKeyPrefix = "gateway:sess:";
    private String userSessionsKeyPrefix = "gateway:user:sessions:";
    private String refreshFamilyKeyPrefix = "gateway:rtfam:";
    private String statusField = "status";
    private String activeStatus = "ACTIVE";
    private String revokedStatus = "REVOKED";
    private String refreshCurrentHashField = "currentRtHash";
    private String refreshUidField = "uid";
    private String refreshSidField = "sid";
    private String refreshRotatedAtField = "rotatedAt";
    private String refreshReuseDetectedField = "reuseDetected";
    private String refreshReuseDetectedAtField = "reuseDetectedAt";
    private String refreshTokenHashPepper = "";
    private String refreshTokenEncryptionSecret = "";
    private String sessionCookieName = "SESSION";
    private String sessionCookiePath = "/";
    private String keycloakLogoutUrl = "";
    private String keycloakLogoutAuthHeader = HttpHeaderNames.GATEWAY_AUTH;
    private String keycloakLogoutAuthToken = "";
}
