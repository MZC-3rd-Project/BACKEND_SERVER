package com.example.gateway.security.session.application;

public class GatewaySessionUnauthorizedException extends RuntimeException {

    public GatewaySessionUnauthorizedException(String message) {
        super(message);
    }
}
