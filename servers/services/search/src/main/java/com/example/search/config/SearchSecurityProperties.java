package com.example.search.config;

import com.example.contracts.http.HttpHeaderNames;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "search.security")
public class SearchSecurityProperties {

    private boolean gatewayAuthEnabled = false;
    private String internalAuthHeader = HttpHeaderNames.GATEWAY_AUTH;
    private String internalAuthToken = "";

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
}
