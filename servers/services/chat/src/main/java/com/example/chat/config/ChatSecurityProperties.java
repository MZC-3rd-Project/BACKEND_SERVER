package com.example.chat.config;

import com.example.contracts.http.HttpHeaderNames;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chat.security")
public class ChatSecurityProperties {

    private boolean gatewayAuthEnabled = true;
    private String internalAuthHeader = HttpHeaderNames.GATEWAY_AUTH;
    private String internalAuthToken = "";
    private String userIdHeader = HttpHeaderNames.USER_ID;

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
