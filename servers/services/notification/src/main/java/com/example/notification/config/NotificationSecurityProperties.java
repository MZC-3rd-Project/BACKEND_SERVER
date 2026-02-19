package com.example.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.security")
public class NotificationSecurityProperties {

    private boolean gatewayAuthEnabled = true;
    private String internalAuthHeader = "X-Internal-Auth";
    private String internalAuthToken = "";
    private String userIdHeader = "X-User-Id";

    public boolean isGatewayAuthEnabled() {
        return gatewayAuthEnabled;
    }

    public void setGatewayAuthEnabled(boolean gatewayAuthEnabled) {
        this.gatewayAuthEnabled = gatewayAuthEnabled;
    }

    public String getInternalAuthHeader() {
        return internalAuthHeader;
    }

    public void setInternalAuthHeader(String internalAuthHeader) {
        this.internalAuthHeader = internalAuthHeader;
    }

    public String getInternalAuthToken() {
        return internalAuthToken;
    }

    public void setInternalAuthToken(String internalAuthToken) {
        this.internalAuthToken = internalAuthToken;
    }

    public String getUserIdHeader() {
        return userIdHeader;
    }

    public void setUserIdHeader(String userIdHeader) {
        this.userIdHeader = userIdHeader;
    }
}
