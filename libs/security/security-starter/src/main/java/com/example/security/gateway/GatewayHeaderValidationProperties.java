package com.example.security.gateway;

public interface GatewayHeaderValidationProperties {

    boolean isGatewayAuthEnabled();

    String getInternalAuthHeader();

    String getInternalAuthToken();

    String getUserIdHeader();
}
