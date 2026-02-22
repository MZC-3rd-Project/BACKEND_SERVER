package com.example.product.client;

import com.example.core.exception.BusinessException;
import com.example.config.resilience.CircuitBreakerHelper;
import com.example.product.exception.ProductErrorCode;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.List;

@Slf4j
@Component
public class MediaClient {

    private static final String GET_MEDIA_URL_RESILIENCE_NAME = "product-media-get-url";
    private static final String SYNC_MEDIA_LINKS_RESILIENCE_NAME = "product-media-sync-links";

    private final WebClient webClient;
    private final CircuitBreakerHelper circuitBreakerHelper;

    public MediaClient(WebClient.Builder webClientBuilder,
                       @Value("${app.service.media-url}") String mediaUrl,
                       CircuitBreakerHelper circuitBreakerHelper) {
        this.webClient = webClientBuilder.baseUrl(mediaUrl).build();
        this.circuitBreakerHelper = circuitBreakerHelper;
    }

    public String getMediaUrl(Long mediaId) {
        try {
            return circuitBreakerHelper.executeWithCircuitBreakerAndRetry(
                    GET_MEDIA_URL_RESILIENCE_NAME,
                    (Callable<String>) () -> getMediaUrlInternal(mediaId)
            );
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            if (e.getCause() instanceof BusinessException businessException) {
                throw businessException;
            }
            log.error("Media service call failed after resilience retries: mediaId={}", mediaId, e);
            throw new BusinessException(ProductErrorCode.MEDIA_SERVICE_ERROR);
        }
    }

    public void syncItemLinks(Long itemId, Long thumbnailMediaId, List<Long> galleryMediaIds) {
        try {
            circuitBreakerHelper.executeWithCircuitBreakerAndRetry(
                    SYNC_MEDIA_LINKS_RESILIENCE_NAME,
                    (Callable<Boolean>) () -> {
                        syncItemLinksInternal(itemId, thumbnailMediaId, galleryMediaIds);
                        return Boolean.TRUE;
                    }
            );
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            if (e.getCause() instanceof BusinessException businessException) {
                throw businessException;
            }
            log.error("Media link sync failed after resilience retries: itemId={}", itemId, e);
            throw new BusinessException(ProductErrorCode.MEDIA_SERVICE_ERROR);
        }
    }

    private String getMediaUrlInternal(Long mediaId) throws IOException {
        try {
            JsonNode response = webClient.get()
                    .uri("/internal/v1/media/{mediaId}/url", mediaId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("success").asBoolean()) {
                throw new BusinessException(ProductErrorCode.MEDIA_SERVICE_ERROR);
            }

            JsonNode data = response.path("data");
            String status = data.path("status").asText();
            String mediaUrl = data.path("mediaUrl").asText();
            if (!StringUtils.hasText(mediaUrl)
                    || (!"CONFIRMED".equals(status) && !"READY".equals(status))) {
                throw new BusinessException(ProductErrorCode.INVALID_MEDIA_REFERENCE);
            }
            return mediaUrl;
        } catch (WebClientRequestException e) {
            throw new IOException("Media service request failed (network/timeout)", e);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new BusinessException(ProductErrorCode.INVALID_MEDIA_REFERENCE);
            }
            if (e.getStatusCode().is5xxServerError()) {
                throw new IOException("Media service 5xx response", e);
            }
            log.error("Media service call failed: mediaId={}, status={}", mediaId, e.getStatusCode().value(), e);
            throw new BusinessException(ProductErrorCode.MEDIA_SERVICE_ERROR);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Media service call failed: mediaId={}", mediaId, e);
            throw new BusinessException(ProductErrorCode.MEDIA_SERVICE_ERROR);
        }
    }

    private void syncItemLinksInternal(Long itemId, Long thumbnailMediaId, List<Long> galleryMediaIds) throws IOException {
        try {
            List<MediaUsageSetRequest> sets = List.of(
                    MediaUsageSetRequest.of("THUMBNAIL", thumbnailMediaId == null ? List.of() : List.of(thumbnailMediaId)),
                    MediaUsageSetRequest.of("GALLERY", galleryMediaIds == null ? List.of() : galleryMediaIds)
            );

            MediaLinksSyncRequest request = MediaLinksSyncRequest.builder()
                    .ownerType("ITEM")
                    .ownerId(itemId)
                    .sets(sets)
                    .build();

            JsonNode response = webClient.put()
                    .uri("/internal/v1/media/links/sync")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("success").asBoolean()) {
                throw new BusinessException(ProductErrorCode.MEDIA_SERVICE_ERROR);
            }
        } catch (WebClientRequestException e) {
            throw new IOException("Media link sync request failed (network/timeout)", e);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new BusinessException(ProductErrorCode.INVALID_MEDIA_REFERENCE);
            }
            if (e.getStatusCode().is5xxServerError()) {
                throw new IOException("Media link sync 5xx response", e);
            }
            log.error("Media link sync failed: itemId={}, status={}", itemId, e.getStatusCode().value(), e);
            throw new BusinessException(ProductErrorCode.MEDIA_SERVICE_ERROR);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Media link sync failed: itemId={}", itemId, e);
            throw new BusinessException(ProductErrorCode.MEDIA_SERVICE_ERROR);
        }
    }

    @Getter
    @Builder
    private static class MediaLinksSyncRequest {
        private String ownerType;
        private Long ownerId;
        private List<MediaUsageSetRequest> sets;
    }

    @Getter
    @NoArgsConstructor
    private static class MediaUsageSetRequest {
        private String usageType;
        private List<Long> mediaIds;

        private MediaUsageSetRequest(String usageType, List<Long> mediaIds) {
            this.usageType = usageType;
            this.mediaIds = mediaIds;
        }

        private static MediaUsageSetRequest of(String usageType, List<Long> mediaIds) {
            return new MediaUsageSetRequest(usageType, mediaIds);
        }
    }
}
