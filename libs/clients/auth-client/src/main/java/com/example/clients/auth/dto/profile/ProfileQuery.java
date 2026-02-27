package com.example.clients.auth.dto.profile;

public record ProfileQuery(
    Long userId,
    String email,
    String nickname,
    String phoneNumber
    ) {


}
