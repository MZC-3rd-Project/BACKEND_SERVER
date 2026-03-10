package com.example.clients.stock.facade;

import com.example.clients.stock.dto.ReserveOrderStockRequest;
import com.example.clients.stock.dto.ReserveOrderStockResponse;

public interface StockOrderReservationClientFacade {

    ReserveOrderStockResponse reserveOrderStock(ReserveOrderStockRequest request);
}
