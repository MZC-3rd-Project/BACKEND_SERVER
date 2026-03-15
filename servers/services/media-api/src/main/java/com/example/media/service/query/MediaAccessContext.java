package com.example.media.service.query;

public record MediaAccessContext(MediaAccessScope scope, Long userId) {

    public enum MediaAccessScope {
        PUBLIC,
        AUTHENTICATED,
        INTERNAL
    }

    public static MediaAccessContext fromExternalRequest(Long userId) {
        if (userId != null && userId > 0) {
            return authenticated(userId);
        }
        return publicAccess();
    }

    public static MediaAccessContext publicAccess() {
        return new MediaAccessContext(MediaAccessScope.PUBLIC, null);
    }

    public static MediaAccessContext authenticated(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("authenticated access requires a positive userId");
        }
        return new MediaAccessContext(MediaAccessScope.AUTHENTICATED, userId);
    }

    public static MediaAccessContext internal() {
        return new MediaAccessContext(MediaAccessScope.INTERNAL, null);
    }

    public boolean isInternal() {
        return scope == MediaAccessScope.INTERNAL;
    }

    public boolean isAuthenticated() {
        return scope == MediaAccessScope.AUTHENTICATED;
    }
}
