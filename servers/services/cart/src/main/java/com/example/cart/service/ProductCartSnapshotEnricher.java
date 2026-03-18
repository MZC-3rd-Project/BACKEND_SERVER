package com.example.cart.service;

import com.example.clients.media.facade.MediaClientFacade;
import com.example.clients.product.facade.ProductItemQueryClientFacade;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCartSnapshotEnricher implements CartSnapshotEnricher {

    private final ProductItemQueryClientFacade productItemQueryClientFacade;
    private final StoreQueryStoreNameClient storeQueryStoreNameClient;
    private final MediaClientFacade mediaClientFacade;

    @Override
    public CartSnapshotData enrich(Long itemId) {
        try {
            JsonNode item = productItemQueryClientFacade.findItem(itemId);
            Long storeId = nullableLong(item.path("storeId"));
            return new CartSnapshotData(
                    storeId,
                    nullableText(item.path("title")),
                    resolveThumbnailUrl(item),
                    storeQueryStoreNameClient.findStoreName(storeId),
                    nullableLong(item.path("price")),
                    nullableText(item.path("status"))
            );
        } catch (Exception e) {
            log.warn("Cart snapshot enrichment failed. itemId={}", itemId, e);
            return CartSnapshotData.empty();
        }
    }

    private Long nullableLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asLong();
    }

    private String nullableText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return org.springframework.util.StringUtils.hasText(value) ? value : null;
    }

    private String resolveThumbnailUrl(JsonNode item) {
        String thumbnailUrl = nullableText(item.path("thumbnailUrl"));
        if (StringUtils.hasText(thumbnailUrl)) {
            return thumbnailUrl;
        }

        Long thumbnailMediaId = nullableLong(item.path("images").path("thumbnail").path("mediaId"));
        if (thumbnailMediaId == null || thumbnailMediaId <= 0L) {
            return null;
        }

        try {
            return mediaClientFacade.getMediaUrl(thumbnailMediaId);
        } catch (Exception e) {
            log.warn("Cart thumbnail lookup failed. mediaId={}", thumbnailMediaId, e);
            return null;
        }
    }
}
