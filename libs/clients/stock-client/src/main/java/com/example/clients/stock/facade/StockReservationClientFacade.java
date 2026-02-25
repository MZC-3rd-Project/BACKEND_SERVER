package com.example.clients.stock.facade;

public interface StockReservationClientFacade {

    Long reserveStock(Long stockItemId, Long userId, int quantity, Long orderId);


    void cancelReservation(Long reservationId);
}
