package com.example.clients.profile.impl;

import com.example.clients.profile.dto.ProfileCreateCommand;
import com.example.clients.profile.exception.ProfileClientException;
import com.example.clients.profile.facade.ProfileClientFacade;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

public class DefaultProfileClientFacade implements ProfileClientFacade {

    private final WebClient webClient;

    public DefaultProfileClientFacade(WebClient.Builder webClientBuilder, String profileServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(profileServiceUrl).build();
    }

    @Override
    public void createProfile(ProfileCreateCommand command) {
        validate(command);

        try {
            JsonNode response = webClient.post()
                    .uri("/internal/v1/profiles")
                    .bodyValue(command)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("success").asBoolean()) {
                throw new ProfileClientException("profile create response is invalid");
            }
        } catch (WebClientResponseException e) {
            throw new ProfileClientException("profile create failed: " + e.getStatusCode(), e);
        } catch (ProfileClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ProfileClientException("profile create failed", e);
        }
    }

    private void validate(ProfileCreateCommand command) {
        if (command == null || command.userId() == null || command.userId() <= 0) {
            throw new ProfileClientException("profile create command is invalid: userId");
        }
        if (!StringUtils.hasText(command.email())) {
            throw new ProfileClientException("profile create command is invalid: email");
        }
        if (!StringUtils.hasText(command.nickname())) {
            throw new ProfileClientException("profile create command is invalid: nickname");
        }
    }
}
