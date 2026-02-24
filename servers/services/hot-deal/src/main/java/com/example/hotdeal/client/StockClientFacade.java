package com.example.hotdeal.client;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Internal stock API facade for hot-deal service.
 *
 * Endpoints:
 * GET /internal/v1/stock/{stockItemId}
 * GET /internal/v1/stock/items/{itemId}
 */
public interface StockClientFacade {

    /**
     * Gets stock detail by stock item id.
     *
     * Response example:
     * {
     *   "success": true,
     *   "data": {
     *     "stockItemId": 501,
     *     "availableQuantity": 120
     *   }
     * }
     */
    JsonNode getStockInfo(Long stockItemId);

    /**
     * Gets stock summary by item id.
     */
    JsonNode getStockInfoByItemId(Long itemId);
}
