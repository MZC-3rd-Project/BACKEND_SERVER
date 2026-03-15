package com.example.stock.service.query;

import com.example.stock.dto.query.response.StockHistoryQueryResponse;
import com.example.stock.dto.query.response.StockItemQueryResponse;
import com.example.stock.dto.query.response.StockReservationQueryResponse;
import com.example.stock.dto.query.response.StockSummaryQueryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockQueryService {

    private final StockQueryReader stockQueryReader;
    private final StockQueryAssembler stockQueryAssembler;

    public StockItemQueryResponse getStock(Long stockItemId) {
        return stockQueryAssembler.toStockResponse(stockQueryReader.readStock(stockItemId));
    }

    public StockSummaryQueryResponse getStocksByItemId(Long itemId) {
        return stockQueryAssembler.toStockSummaryResponse(itemId, stockQueryReader.readStocksByItemId(itemId));
    }

    public List<StockHistoryQueryResponse> getStockHistory(Long stockItemId, int page, int size) {
        return stockQueryReader.readStockHistory(stockItemId, page, size)
                .stream()
                .map(stockQueryAssembler::toStockHistoryResponse)
                .toList();
    }

    public List<StockReservationQueryResponse> getReservationsByOrderId(Long orderId) {
        return stockQueryReader.readReservationsByOrderId(orderId).stream()
                .map(stockQueryAssembler::toReservationResponse)
                .toList();
    }
}
