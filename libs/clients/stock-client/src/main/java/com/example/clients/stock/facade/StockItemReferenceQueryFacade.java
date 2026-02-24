package com.example.clients.stock.facade;

public interface StockItemReferenceQueryFacade {

    Long findStockItemId(Long itemId, Long referenceId);
}
