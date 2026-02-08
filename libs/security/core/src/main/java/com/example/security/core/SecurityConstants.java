package com.example.security.core;

public final class SecurityConstants {

    private SecurityConstants() {
    }

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String HEADER_NONCE = "X-Nonce";
    public static final String HEADER_TIMESTAMP = "X-Timestamp";
    public static final String HEADER_SIGNATURE = "X-Signature";
    public static final String HEADER_GATEWAY_AUTH = "X-Gateway-Auth";
}
