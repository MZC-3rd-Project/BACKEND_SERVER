package com.example.gateway.security.session.domain;

public enum RefreshTokenFamilyRotateResult {
    ROTATED,
    CURRENT_HASH_MISMATCH,
    FAMILY_NOT_FOUND
}
