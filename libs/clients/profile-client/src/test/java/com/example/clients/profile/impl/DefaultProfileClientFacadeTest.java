package com.example.clients.profile.impl;

import com.example.clients.profile.dto.ProfileCreateCommand;
import com.example.clients.profile.exception.ProfileClientException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultProfileClientFacadeTest {

    private MockWebServer mockWebServer;
    private DefaultProfileClientFacade facade;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        facade = new DefaultProfileClientFacade(WebClient.builder(), mockWebServer.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void createProfile_succeeds_whenSuccessResponseReturned() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"success\":true}"));

        facade.createProfile(new ProfileCreateCommand(11L, "user11@example.com", "user11"));

        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/internal/v1/profiles");
        assertThat(request.getBody().readUtf8()).contains("\"userId\":11");
    }

    @Test
    void createProfile_throws_whenResponseSuccessFlagIsFalse() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"success\":false}"));

        assertThatThrownBy(() -> facade.createProfile(new ProfileCreateCommand(12L, "user12@example.com", "user12")))
                .isInstanceOf(ProfileClientException.class)
                .hasMessageContaining("response is invalid");
    }

    @Test
    void createProfile_throws_whenCommandIsInvalid() {
        assertThatThrownBy(() -> facade.createProfile(new ProfileCreateCommand(null, "user13@example.com", "user13")))
                .isInstanceOf(ProfileClientException.class)
                .hasMessageContaining("userId");
    }
}
