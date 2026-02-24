package com.example.sales.client;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Internal product API facade for sales service.
 *
 * Endpoint:
 * GET /internal/v1/items/{itemId}
 *
 * Response example:
 * {
 *   "success": true,
 *   "data": {
 *     "id": 280995533466779648,
 *     "itemType": "GOODS",
 *     "title": "Sample item title",
 *     "price": 12900
 *   }
 * }
 */
public interface ProductClientFacade {

    /**
     * Fetches the raw product payload required for purchase validation.
     *
     * @param itemId target item id
     * @return response.data payload as JsonNode
     */
    JsonNode findItem(Long itemId);
}
