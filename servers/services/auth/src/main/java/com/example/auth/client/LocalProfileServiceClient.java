package com.example.auth.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("local")
public class LocalProfileServiceClient implements ProfileServicePort {

    @Override
    public void createProfile(Long userId, String email, String nickname) {
        log.info("[LOCAL] Profile creation skipped: userId={}, email={}", userId, email);
    }
}
