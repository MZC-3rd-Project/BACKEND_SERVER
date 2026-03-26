package com.example.auth.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InternalUserSessionLogoutRequest(
        @NotNull
        @Positive
        Long userId
) {
}
