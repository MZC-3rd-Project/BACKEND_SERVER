package com.example.auth.dto.response;

public record UserExistsResponse(
        Long userId,
        boolean exists
) {
    public static UserExistsResponse found(Long userId) {
        return new UserExistsResponse(userId, true);
    }

    public static UserExistsResponse notFound() {
        return new UserExistsResponse(null, false);
    }
}
