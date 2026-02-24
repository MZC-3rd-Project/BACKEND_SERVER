package com.example.hotdeal.client;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Internal product API facade for hot-deal service.
 *
 * Endpoints:
 * GET /internal/v1/items/{itemId}
 * GET /internal/v1/items/ending-soon
 */
public interface ProductClientFacade {

    /**
     * Gets a single item payload.
     */
    JsonNode findItem(Long itemId);

    /**
     * Gets items ending soon (D-3 window) for hot-deal selection.
     *
     * Response example:
     * {
     *   "success": true,
     *   "data": [
     *     {
     *       "id": 280995533466779648,
     *       "title": "Sample item",
     *       "status": "ON_SALE"
     *     }
     *   ]
     * }
     */
    JsonNode findItemsEndingSoon();
}
