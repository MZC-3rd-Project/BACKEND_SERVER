package com.example.profile.dto.response.internal;

public record ProfileSnapshotResponse(
    Long userId,
    String nickname,
    Long profileImageMediaId
) {
}
