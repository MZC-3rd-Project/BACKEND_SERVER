package com.example.auth.client;

import com.example.core.exception.TechnicalException;
import com.example.auth.exception.AuthErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@Profile("!local")
@RequiredArgsConstructor
public class ProfileServiceClient implements ProfileServicePort {

    private final WebClient profileWebClient;

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Override
    public void createProfile(Long userId, String email, String nickname) {
        try {
            Map<String, Object> body = Map.of(
                    "userId", userId,
                    "email", email,
                    "nickname", nickname
            );

            profileWebClient.post()
                    .uri("/internal/v1/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block(TIMEOUT);

            log.info("Profile created for userId={}", userId);
        } catch (WebClientResponseException e) {
            log.error("Profile creation failed: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new TechnicalException(AuthErrorCode.SIGNUP_PROFILE_FAILED,
                    "Profile 생성 실패: " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Profile service communication error", e);
            throw new TechnicalException(AuthErrorCode.PROFILE_SERVICE_ERROR,
                    "Profile 서비스 통신 실패", e);
        }
    }
}
