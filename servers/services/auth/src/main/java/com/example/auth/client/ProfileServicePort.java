package com.example.auth.client;

public interface ProfileServicePort {
    void createProfile(Long userId, String email, String nickname);
}
