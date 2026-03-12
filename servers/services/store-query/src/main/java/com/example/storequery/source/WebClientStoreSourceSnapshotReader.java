package com.example.storequery.source;

import com.example.clients.media.facade.MediaClientFacade;
import com.example.storequery.entity.StoreQueryAddressType;
import com.example.storequery.entity.StoreQueryContactType;
import com.example.storequery.entity.StoreQueryImageType;
import com.example.storequery.entity.StoreQueryStatus;
import com.example.storequery.projection.StoreReadImageSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class WebClientStoreSourceSnapshotReader implements StoreSourceSnapshotReader {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final MediaClientFacade mediaClientFacade;

    public WebClientStoreSourceSnapshotReader(
        WebClient.Builder webClientBuilder,
        ObjectMapper objectMapper,
        MediaClientFacade mediaClientFacade,
        @Value("${app.service.store-url}") String storeServiceUrl
    ) {
        this.webClient = webClientBuilder.baseUrl(storeServiceUrl).build();
        this.objectMapper = objectMapper;
        this.mediaClientFacade = mediaClientFacade;
    }

    @Override
    public Optional<StoreSourceSnapshot> read(Long storeId) {
        if (storeId == null || storeId <= 0L) {
            return Optional.empty();
        }

        try {
            JsonNode response = webClient.get()
                .uri("/internal/v1/stores/{storeId}/snapshot", storeId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
            JsonNode data = response == null || !response.path("success").asBoolean() ? null : response.path("data");
            if (data == null || data.isMissingNode() || data.isNull()) {
                return Optional.empty();
            }

            StoreSnapshotPayload payload = objectMapper.treeToValue(data, StoreSnapshotPayload.class);
            Map<Long, String> mediaUrlMap = resolveMediaUrlMap(payload.images().stream()
                .map(StoreSnapshotImagePayload::mediaId)
                .filter(mediaId -> mediaId != null && mediaId > 0L)
                .toList());
            List<StoreReadImageSnapshot> images = payload.images().stream()
                .map(image -> new StoreReadImageSnapshot(
                    toImageType(image.imageType()),
                    image.mediaId(),
                    mediaUrlMap.get(image.mediaId()),
                    image.sortOrder(),
                    image.sourceUpdatedAt()
                ))
                .toList();

            return Optional.of(new StoreSourceSnapshot(
                payload.storeId(),
                payload.userId(),
                payload.storeName(),
                toStatus(payload.status()),
                payload.description(),
                payload.defaultAddress(),
                toAddressType(payload.defaultAddressType()),
                payload.primaryContactValue(),
                toContactType(payload.primaryContactType()),
                images,
                payload.sourceCreatedAt(),
                payload.sourceUpdatedAt()
            ));
        } catch (WebClientResponseException.NotFound exception) {
            return Optional.empty();
        } catch (Exception exception) {
            log.warn("Store snapshot lookup failed. storeId={}", storeId, exception);
            throw new IllegalStateException("store snapshot lookup failed", exception);
        }
    }

    private Map<Long, String> resolveMediaUrlMap(List<Long> mediaIds) {
        if (mediaIds.isEmpty()) {
            return Map.of();
        }

        try {
            return mediaClientFacade.getMediaUrlMap(mediaIds);
        } catch (Exception exception) {
            log.warn("Store media url lookup failed. mediaIds={}", mediaIds, exception);
            return Map.of();
        }
    }

    private StoreQueryStatus toStatus(String rawStatus) {
        return rawStatus == null ? StoreQueryStatus.INACTIVE : StoreQueryStatus.valueOf(rawStatus);
    }

    private StoreQueryAddressType toAddressType(String rawType) {
        return StringUtils.hasText(rawType) ? StoreQueryAddressType.valueOf(rawType) : null;
    }

    private StoreQueryContactType toContactType(String rawType) {
        return StringUtils.hasText(rawType) ? StoreQueryContactType.valueOf(rawType) : null;
    }

    private StoreQueryImageType toImageType(String rawType) {
        return StringUtils.hasText(rawType) ? StoreQueryImageType.valueOf(rawType) : StoreQueryImageType.GALLERY;
    }

    private record StoreSnapshotPayload(
        Long storeId,
        Long userId,
        String storeName,
        String status,
        String description,
        String defaultAddress,
        String defaultAddressType,
        String primaryContactValue,
        String primaryContactType,
        List<StoreSnapshotImagePayload> images,
        LocalDateTime sourceCreatedAt,
        LocalDateTime sourceUpdatedAt
    ) {
        private StoreSnapshotPayload {
            images = images == null ? List.of() : images;
        }
    }

    private record StoreSnapshotImagePayload(
        String imageType,
        Long mediaId,
        Integer sortOrder,
        LocalDateTime sourceUpdatedAt
    ) {
    }
}
