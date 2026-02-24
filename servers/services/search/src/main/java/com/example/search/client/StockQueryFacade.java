package com.example.search.client;

/**
 * Internal stock query facade for search service.
 *
 * Endpoint:
 * GET /internal/v1/stock/items/{itemId}
 *
 * Response example:
 * {
 *   "success": true,
 *   "data": {
 *     "itemId": 280995533466779648,
 *     "stocks": [
 *       { "availableQuantity": 100 },
 *       { "availableQuantity": 40 }
 *     ]
 *   }
 * }
 */
public interface StockQueryFacade {

    /**
     * Aggregates available quantity across stock rows for one item.
     */
    int fetchAvailableStockTotal(Long itemId);
}
