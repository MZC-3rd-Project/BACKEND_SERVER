package com.example.funding.client;

/**
 * Internal stock API facade for funding service.
 *
 * Endpoints:
 * POST /internal/v1/stock/reserve
 * POST /internal/v1/stock/cancel
 * GET /internal/v1/stock/items/{itemId}
 */
public interface StockClientFacade {

    /**
     * Reserve stock for a funding participation.
     */
    Long reserveStock(Long stockItemId, Long userId, int quantity, Long orderId);

    /**
     * Cancel previously reserved stock.
     */
    void cancelReservation(Long reservationId);

    /**
     * Finds stockItemId by itemId + referenceId from stock summary response.
     *
     * Stock summary response example:
     * {
     *   "success": true,
     *   "data": {
     *     "itemId": 280995533466779648,
     *     "stocks": [
     *       {
     *         "stockItemId": 501,
     *         "referenceId": 9001,
     *         "availableQuantity": 120
     *       }
     *     ]
     *   }
     * }
     */
    Long findStockItemId(Long itemId, Long referenceId);
}
