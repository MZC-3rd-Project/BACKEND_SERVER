package com.example.security.starter.servlet.properties;

public interface GatewayHeaderValidationProperties {

    boolean isGatewayAuthEnabled();

    String getInternalAuthHeader();

    String getInternalAuthToken();

    String getUserIdHeader();
}
