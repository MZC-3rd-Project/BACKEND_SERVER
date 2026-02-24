package com.example.clients.media;

import com.example.config.resilience.CircuitBreakerHelper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

@Slf4j
public class DefaultMediaClientFacade implements MediaClientFacade {

    private static final String GET_MEDIA_URL_RESILIENCE_NAME = "media-client-get-url";
    private static final String GET_MEDIA_URL_BATCH_RESILIENCE_NAME = "media-client-get-url-batch";
    private static final String SYNC_MEDIA_LINKS_RESILIENCE_NAME = "media-client-sync-links";

    private final WebClient webClient;
    private final CircuitBreakerHelper circuitBreakerHelper;

    public DefaultMediaClientFacade(
            WebClient.Builder webClientBuilder,
            String mediaServiceUrl,
            CircuitBreakerHelper circuitBreakerHelper
    ) {
        this(webClientBuilder.baseUrl(mediaServiceUrl).build(), circuitBreakerHelper);
    }

    DefaultMediaClientFacade(WebClient webClient, CircuitBreakerHelper circuitBreakerHelper) {
        this.webClient = webClient;
        this.circuitBreakerHelper = circuitBreakerHelper;
    }

    @Override
    public String getMediaUrl(Long mediaId) {
        if (mediaId == null || mediaId <= 0) {
            throw new InvalidMediaReferenceException("mediaId must be positive");
        }

        try {
            return circuitBreakerHelper.executeWithCircuitBreakerAndRetry(
                    GET_MEDIA_URL_RESILIENCE_NAME,
                    (Callable<String>) () -> getMediaUrlInternal(mediaId)
            );
        } catch (RuntimeException e) {
            throw unwrapRuntimeException("getMediaUrl", e);
        }
    }

    @Override
    public Map<Long, String> getMediaUrlMap(List<Long> mediaIds) {
        List<Long> uniqueIds = mediaIds == null ? List.of() : mediaIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        if (uniqueIds.isEmpty()) {
            return Map.of();
        }

        try {
            return circuitBreakerHelper.executeWithCircuitBreakerAndRetry(
                    GET_MEDIA_URL_BATCH_RESILIENCE_NAME,
                    (Callable<Map<Long, String>>) () -> getMediaUrlMapInternal(uniqueIds)
            );
        } catch (RuntimeException e) {
            throw unwrapRuntimeException("getMediaUrlMap", e);
        }
    }

    @Override
    public void syncLinks(MediaLinksSyncCommand command) {
        if (command == null || !StringUtils.hasText(command.ownerType()) || command.ownerId() == null || command.ownerId() <= 0) {
            throw new InvalidMediaReferenceException("syncLinks command is invalid");
        }

        try {
            circuitBreakerHelper.executeWithCircuitBreakerAndRetry(
                    SYNC_MEDIA_LINKS_RESILIENCE_NAME,
                    (Callable<Boolean>) () -> {
                        syncLinksInternal(command);
                        return Boolean.TRUE;
                    }
            );
        } catch (RuntimeException e) {
            throw unwrapRuntimeException("syncLinks", e);
        }
    }

    private String getMediaUrlInternal(Long mediaId) {
        try {
            JsonNode response = webClient.get()
                    .uri("/internal/v1/media/{mediaId}/url", mediaId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("success").asBoolean()) {
                throw new MediaClientException("media url response is invalid");
            }

            JsonNode data = response.path("data");
            String status = data.path("status").asText();
            String mediaUrl = data.path("mediaUrl").asText();
            if (!StringUtils.hasText(mediaUrl)
                    || (!"CONFIRMED".equals(status) && !"READY".equals(status))) {
                throw new InvalidMediaReferenceException("media status is not readable: " + status);
            }
            return mediaUrl;
        } catch (WebClientRequestException e) {
            throw new MediaClientRetriableException("media service request failed", e);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new InvalidMediaReferenceException("invalid media reference", e);
            }
            if (e.getStatusCode().is5xxServerError()) {
                throw new MediaClientRetriableException("media service 5xx response", e);
            }
            throw new MediaClientException("media service call failed", e);
        }
    }

    private Map<Long, String> getMediaUrlMapInternal(List<Long> mediaIds) {
        try {
            JsonNode response = webClient.post()
                    .uri("/internal/v1/media/urls/batch")
                    .bodyValue(Map.of("mediaIds", mediaIds))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("success").asBoolean()) {
                throw new MediaClientException("media batch url response is invalid");
            }

            JsonNode data = response.path("data");
            if (!data.isArray()) {
                return Map.of();
            }

            Map<Long, String> urlMap = new LinkedHashMap<>();
            for (JsonNode node : data) {
                long mediaId = node.path("mediaId").asLong(-1L);
                String mediaUrl = node.path("mediaUrl").asText();
                if (mediaId > 0 && StringUtils.hasText(mediaUrl)) {
                    urlMap.put(mediaId, mediaUrl);
                }
            }
            return urlMap;
        } catch (WebClientRequestException e) {
            throw new MediaClientRetriableException("media batch url request failed", e);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new InvalidMediaReferenceException("invalid media reference in batch", e);
            }
            if (e.getStatusCode().is5xxServerError()) {
                throw new MediaClientRetriableException("media batch url 5xx response", e);
            }
            throw new MediaClientException("media batch url call failed", e);
        }
    }

    private void syncLinksInternal(MediaLinksSyncCommand command) {
        try {
            List<MediaLinksSyncCommand.MediaUsageSet> normalizedSets = command.sets().stream()
                    .filter(Objects::nonNull)
                    .map(set -> new MediaLinksSyncCommand.MediaUsageSet(
                            set.usageType(),
                            new LinkedHashSet<>(set.mediaIds()).stream()
                                    .filter(Objects::nonNull)
                                    .filter(id -> id > 0)
                                    .toList()
                    ))
                    .toList();

            JsonNode response = webClient.put()
                    .uri("/internal/v1/media/links/sync")
                    .bodyValue(new MediaLinksSyncCommand(command.ownerType(), command.ownerId(), normalizedSets))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("success").asBoolean()) {
                throw new MediaClientException("media links sync response is invalid");
            }
        } catch (WebClientRequestException e) {
            throw new MediaClientRetriableException("media links sync request failed", e);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new InvalidMediaReferenceException("invalid media reference in sync request", e);
            }
            if (e.getStatusCode().is5xxServerError()) {
                throw new MediaClientRetriableException("media links sync 5xx response", e);
            }
            throw new MediaClientException("media links sync failed", e);
        }
    }

    private RuntimeException unwrapRuntimeException(String operation, RuntimeException e) {
        if (e instanceof MediaClientException mediaClientException) {
            return mediaClientException;
        }

        Throwable cause = e.getCause();
        if (cause instanceof MediaClientException mediaClientException) {
            return mediaClientException;
        }

        log.error("Media client {} failed after resilience retries", operation, e);
        return new MediaClientException("media client " + operation + " failed", e);
    }
}
