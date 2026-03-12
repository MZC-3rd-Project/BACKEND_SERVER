package com.example.storequery.source;

public record StoreOwnerSnapshot(
    Long userId,
    String nickname,
    String profileImageUrl
) {
}
