package com.example.security.starter.servlet.properties;

import com.example.contracts.http.HttpHeaderNames;

public abstract class GatewayHeaderValidationBaseProperties implements GatewayHeaderValidationProperties {

    private boolean gatewayAuthEnabled = true;
    private String internalAuthHeader = HttpHeaderNames.GATEWAY_AUTH;
    private String internalAuthToken = "";
    private String userIdHeader = HttpHeaderNames.USER_ID;

    @Override
    public boolean isGatewayAuthEnabled() {
        return gatewayAuthEnabled;
    }

    public void setGatewayAuthEnabled(boolean gatewayAuthEnabled) {
        this.gatewayAuthEnabled = gatewayAuthEnabled;
    }

    @Override
    public String getInternalAuthHeader() {
        return internalAuthHeader;
    }

    public void setInternalAuthHeader(String internalAuthHeader) {
        this.internalAuthHeader = internalAuthHeader;
    }

    @Override
    public String getInternalAuthToken() {
        return internalAuthToken;
    }

    public void setInternalAuthToken(String internalAuthToken) {
        this.internalAuthToken = internalAuthToken;
    }

    @Override
    public String getUserIdHeader() {
        return userIdHeader;
    }

    public void setUserIdHeader(String userIdHeader) {
        this.userIdHeader = userIdHeader;
    }
}
