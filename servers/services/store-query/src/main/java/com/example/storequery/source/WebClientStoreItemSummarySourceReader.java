package com.example.storequery.source;

import com.example.clients.media.facade.MediaClientFacade;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class WebClientStoreItemSummarySourceReader implements StoreItemSummarySourceReader {

    private final WebClient webClient;
    private final MediaClientFacade mediaClientFacade;

    public WebClientStoreItemSummarySourceReader(
        WebClient.Builder webClientBuilder,
        MediaClientFacade mediaClientFacade,
        @Value("${app.service.product-url}") String productServiceUrl
    ) {
        this.webClient = webClientBuilder.baseUrl(productServiceUrl).build();
        this.mediaClientFacade = mediaClientFacade;
    }

    @Override
    public List<StoreItemSummarySource> readByStoreId(Long storeId) {
        if (storeId == null || storeId <= 0L) {
            return List.of();
        }

        try {
            JsonNode response = webClient.get()
                .uri("/internal/v1/items/stores/{storeId}/summaries", storeId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
            JsonNode data = response == null ? null : response.path("data");
            if (data == null || !data.isArray()) {
                return List.of();
            }

            List<StoreItemSummarySource> items = new ArrayList<>();
            List<Long> thumbnailMediaIds = new ArrayList<>();
            data.forEach(itemNode -> {
                StoreItemSummarySource item = toItemSummary(itemNode);
                if (item == null) {
                    return;
                }
                items.add(item);
                if (item.thumbnailMediaId() != null && item.thumbnailMediaId() > 0L) {
                    thumbnailMediaIds.add(item.thumbnailMediaId());
                }
            });

            Map<Long, String> mediaUrlMap = resolveMediaUrlMap(thumbnailMediaIds.stream()
                .filter(mediaId -> mediaId != null && mediaId > 0L)
                .toList());

            return items.stream()
                .map(item -> new StoreItemSummarySource(
                    item.itemId(),
                    item.storeId(),
                    item.sellerId(),
                    item.title(),
                    item.price(),
                    item.itemType(),
                    item.status(),
                    item.thumbnailMediaId(),
                    mediaUrlMap.get(item.thumbnailMediaId()),
                    item.sourceUpdatedAt()
                ))
                .toList();
        } catch (WebClientResponseException.NotFound exception) {
            return List.of();
        } catch (Exception exception) {
            log.warn("Item summary lookup failed. storeId={}", storeId, exception);
            throw new IllegalStateException("item summary lookup failed", exception);
        }
    }

    private StoreItemSummarySource toItemSummary(JsonNode itemNode) {
        Long itemId = longValue(itemNode.path("itemId"));
        if (itemId == null) {
            itemId = longValue(itemNode.path("id"));
        }

        Long thumbnailMediaId = longValue(itemNode.path("thumbnailMediaId"));
        if (thumbnailMediaId == null) {
            thumbnailMediaId = longValue(itemNode.path("images").path("thumbnail").path("mediaId"));
        }

        LocalDateTime sourceUpdatedAt = parseDateTime(textValue(itemNode.path("sourceUpdatedAt")));
        if (sourceUpdatedAt == null) {
            sourceUpdatedAt = parseDateTime(textValue(itemNode.path("updatedAt")));
        }

        Long storeId = longValue(itemNode.path("storeId"));
        Long sellerId = longValue(itemNode.path("sellerId"));
        String title = textValue(itemNode.path("title"));
        Long price = longValue(itemNode.path("price"));
        String itemType = textValue(itemNode.path("itemType"));
        String status = textValue(itemNode.path("status"));

        if (itemId == null || storeId == null || sellerId == null || title == null || price == null
            || itemType == null || status == null || sourceUpdatedAt == null) {
            return null;
        }

        return new StoreItemSummarySource(
            itemId,
            storeId,
            sellerId,
            title,
            price,
            itemType,
            status,
            thumbnailMediaId,
            null,
            sourceUpdatedAt
        );
    }

    private Map<Long, String> resolveMediaUrlMap(List<Long> mediaIds) {
        if (mediaIds.isEmpty()) {
            return Map.of();
        }

        try {
            return mediaClientFacade.getMediaUrlMap(mediaIds);
        } catch (Exception exception) {
            log.warn("Item media url lookup failed. mediaIds={}", mediaIds, exception);
            return Map.of();
        }
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return StringUtils.hasText(value) ? value : null;
    }

    private Long longValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asLong();
    }

    private LocalDateTime parseDateTime(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        return LocalDateTime.parse(rawValue);
    }
}
