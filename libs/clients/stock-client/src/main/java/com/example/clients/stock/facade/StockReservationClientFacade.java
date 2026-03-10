package com.example.clients.stock.facade;

public interface StockReservationClientFacade {

    void reserveStock(Long stockItemId, Long userId, int quantity, Long orderId);


    void cancelReservationsByOrderId(Long orderId);
}
