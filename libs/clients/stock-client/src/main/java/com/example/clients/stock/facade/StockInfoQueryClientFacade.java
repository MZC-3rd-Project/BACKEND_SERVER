package com.example.clients.stock.facade;

import com.fasterxml.jackson.databind.JsonNode;

public interface StockInfoQueryClientFacade {

    JsonNode getStockInfo(Long stockItemId);

    JsonNode getStockInfoByItemId(Long itemId);
}
