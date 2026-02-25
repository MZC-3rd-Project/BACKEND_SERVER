package com.example.contracts.http;

public final class HttpHeaderNames {

    private HttpHeaderNames() {
    }

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLES = "X-User-Roles";
    public static final String NONCE = "X-Nonce";
    public static final String TIMESTAMP = "X-Timestamp";
    public static final String SIGNATURE = "X-Signature";
    public static final String GATEWAY_CONTEXT = "X-Gateway-Context";
    public static final String GATEWAY_AUTH = "X-Gateway-Auth";
    public static final String SESSION_ID = "X-Session-Id";
}
