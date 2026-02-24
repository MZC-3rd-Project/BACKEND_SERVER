package com.example.notification.client;

/**
 * Internal product API facade for notification service.
 *
 * Endpoint:
 * GET /internal/v1/items/{itemId}
 *
 * Response example:
 * {
 *   "success": true,
 *   "data": {
 *     "id": 280995533466779648,
 *     "sellerId": 1001,
 *     "title": "Sample item title"
 *   }
 * }
 */
public interface ProductClientFacade {

    /**
     * Fetches a minimal item summary used by notification payload enrichment.
     *
     * @param itemId target item id
     * @return summary when found and valid, otherwise null
     */
    ProductClient.ProductItemSummary findItemSummary(Long itemId);
}
