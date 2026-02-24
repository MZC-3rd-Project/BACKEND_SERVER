package com.example.sales.client;

/**
 * Internal stock API facade for sales service.
 *
 * Endpoints:
 * POST /internal/v1/stock/reserve
 * POST /internal/v1/stock/cancel
 *
 * Reserve request example:
 * {
 *   "stockItemId": 101,
 *   "userId": 2001,
 *   "quantity": 2,
 *   "orderId": 99901
 * }
 *
 * Reserve response example:
 * {
 *   "success": true,
 *   "data": {
 *     "id": 34567
 *   }
 * }
 */
public interface StockClientFacade {

    /**
     * Reserves stock for order processing.
     *
     * @return reservation id
     */
    Long reserveStock(Long stockItemId, Long userId, int quantity, Long orderId);

    /**
     * Cancels a previously reserved stock quantity.
     */
    void cancelReservation(Long reservationId);
}
