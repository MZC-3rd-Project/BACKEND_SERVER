package com.example.security.context;

import com.example.contracts.http.HttpHeaderNames;

/**
 * @deprecated Use {@link HttpHeaderNames} in new code.
 */
@Deprecated(forRemoval = false, since = "0.0.1")
public final class SecurityConstants {

    private SecurityConstants() {
    }

    public static final String HEADER_USER_ID = HttpHeaderNames.USER_ID;
    public static final String HEADER_USER_ROLES = HttpHeaderNames.USER_ROLES;
    public static final String HEADER_NONCE = HttpHeaderNames.NONCE;
    public static final String HEADER_TIMESTAMP = HttpHeaderNames.TIMESTAMP;
    public static final String HEADER_SIGNATURE = HttpHeaderNames.SIGNATURE;
    public static final String HEADER_GATEWAY_AUTH = HttpHeaderNames.GATEWAY_AUTH;
}
