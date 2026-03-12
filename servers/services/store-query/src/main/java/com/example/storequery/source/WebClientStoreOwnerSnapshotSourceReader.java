package com.example.storequery.source;

import com.example.clients.media.facade.MediaClientFacade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Optional;

@Slf4j
@Component
public class WebClientStoreOwnerSnapshotSourceReader implements StoreOwnerSnapshotSourceReader {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final MediaClientFacade mediaClientFacade;

    public WebClientStoreOwnerSnapshotSourceReader(
        WebClient.Builder webClientBuilder,
        ObjectMapper objectMapper,
        MediaClientFacade mediaClientFacade,
        @Value("${app.service.profile-url}") String profileServiceUrl
    ) {
        this.webClient = webClientBuilder.baseUrl(profileServiceUrl).build();
        this.objectMapper = objectMapper;
        this.mediaClientFacade = mediaClientFacade;
    }

    @Override
    public Optional<StoreOwnerSnapshot> readByUserId(Long userId) {
        if (userId == null || userId <= 0L) {
            return Optional.empty();
        }

        try {
            JsonNode response = webClient.get()
                .uri("/internal/v1/query/profile/snapshot/{userId}", userId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
            JsonNode data = response == null || !response.path("success").asBoolean() ? null : response.path("data");
            if (data == null || data.isMissingNode() || data.isNull()) {
                return Optional.empty();
            }

            ProfileSnapshotPayload payload = objectMapper.treeToValue(data, ProfileSnapshotPayload.class);
            return Optional.of(new StoreOwnerSnapshot(
                payload.userId(),
                payload.nickname(),
                resolveMediaUrl(payload.profileImageMediaId())
            ));
        } catch (WebClientResponseException.NotFound exception) {
            return Optional.empty();
        } catch (Exception exception) {
            log.warn("Profile snapshot lookup failed. userId={}", userId, exception);
            throw new IllegalStateException("profile snapshot lookup failed", exception);
        }
    }

    private String resolveMediaUrl(Long mediaId) {
        if (mediaId == null || mediaId <= 0L) {
            return null;
        }

        try {
            return mediaClientFacade.getMediaUrl(mediaId);
        } catch (Exception exception) {
            log.warn("Profile media url lookup failed. mediaId={}", mediaId, exception);
            return null;
        }
    }

    private record ProfileSnapshotPayload(
        Long userId,
        String nickname,
        Long profileImageMediaId
    ) {
    }
}
