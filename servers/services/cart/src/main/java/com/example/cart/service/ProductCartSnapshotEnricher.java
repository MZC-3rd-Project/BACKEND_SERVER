package com.example.cart.service;

import com.example.clients.product.facade.ProductItemQueryClientFacade;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCartSnapshotEnricher implements CartSnapshotEnricher {

    private final ProductItemQueryClientFacade productItemQueryClientFacade;
    private final StoreQueryStoreNameClient storeQueryStoreNameClient;

    @Override
    public CartSnapshotData enrich(Long itemId) {
        try {
            JsonNode item = productItemQueryClientFacade.findItem(itemId);
            Long storeId = nullableLong(item.path("storeId"));
            return new CartSnapshotData(
                    storeId,
                    nullableText(item.path("title")),
                    null,
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
}
