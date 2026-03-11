package com.example.clients.stock.facade;

public interface StockClientFacade extends
        StockOrderReservationClientFacade,
        StockReservationClientFacade,
        StockItemReferenceQueryFacade,
        StockInfoQueryClientFacade,
        StockAvailabilityQueryFacade {
}
