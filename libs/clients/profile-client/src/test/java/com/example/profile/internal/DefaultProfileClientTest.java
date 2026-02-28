package com.example.profile.internal;

import com.example.profile.client.exception.ProfileClientException;
import com.example.profile.client.facade.DefaultProfileClient;
import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class DefaultProfileClientTest {
    private MockWebServer mockWebServer;
    private DefaultProfileClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/").toString();
        client = new DefaultProfileClient(WebClient.builder(), baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }
    @Test
    void 정상적으로_프로필_리스트를_반환한다() {
        mockWebServer.enqueue(new MockResponse()
            .setBody("""
                {
                  "success": true,
                  "data": [
                    {"userId": 1, "name": "Alice"},
                    {"userId": 2, "name": "Bob"}
                  ]
                }
                """)
            .addHeader("Content-Type", "application/json"));

        List<JsonNode> result = client.findProfileList(List.of(1L, 2L));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).path("name").asText()).isEqualTo("Alice");
    }

    @Test
    void success가_false이면_예외를_던진다() {
        mockWebServer.enqueue(new MockResponse()
            .setBody("""
                {"success": false}
                """)
            .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> client.findProfileList(List.of(1L)))
            .isInstanceOf(ProfileClientException.class)
            .hasMessageContaining("Can't find profile List");
    }

    @Test
    void data가_없으면_예외를_던진다() {
        mockWebServer.enqueue(new MockResponse()
            .setBody("""
                {"success": true, "data": null}
                """)
            .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> client.findProfileList(List.of(1L)))
            .isInstanceOf(ProfileClientException.class)
            .hasMessageContaining("empty data");
    }

    @Test
    void 서버_에러시_예외를_던진다() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        assertThatThrownBy(() -> client.findProfileList(List.of(1L)))
            .isInstanceOf(ProfileClientException.class)
            .hasMessageContaining("profile lookup failed");
    }

}
