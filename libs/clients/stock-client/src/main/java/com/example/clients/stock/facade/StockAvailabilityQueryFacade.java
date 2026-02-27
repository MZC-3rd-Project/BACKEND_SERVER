package com.example.clients.stock.facade;

public interface StockAvailabilityQueryFacade {

    int fetchAvailableStockTotal(Long itemId);
}
