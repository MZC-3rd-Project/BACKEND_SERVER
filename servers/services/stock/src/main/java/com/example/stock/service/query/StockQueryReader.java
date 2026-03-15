package com.example.stock.service.query;

import com.example.core.exception.BusinessException;
import com.example.stock.exception.StockErrorCode;
import com.example.stock.repository.StockHistoryRepository;
import com.example.stock.repository.StockItemRepository;
import com.example.stock.repository.StockReservationRepository;
import com.example.stock.service.query.view.StockHistoryQueryView;
import com.example.stock.service.query.view.StockItemQueryView;
import com.example.stock.service.query.view.StockReservationQueryView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StockQueryReader {

    private final StockItemRepository stockItemRepository;
    private final StockHistoryRepository stockHistoryRepository;
    private final StockReservationRepository stockReservationRepository;

    public StockItemQueryView readStock(Long stockItemId) {
        return stockItemRepository.findViewById(stockItemId)
                .orElseThrow(() -> new BusinessException(StockErrorCode.STOCK_NOT_FOUND));
    }

    public List<StockItemQueryView> readStocksByItemId(Long itemId) {
        return stockItemRepository.findViewsByItemId(itemId);
    }

    public List<StockHistoryQueryView> readStockHistory(Long stockItemId, int page, int size) {
        ensureStockExists(stockItemId);
        return stockHistoryRepository.findViewsByStockItemIdOrderByCreatedAtDesc(
                        stockItemId,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
    }

    public List<StockReservationQueryView> readReservationsByOrderId(Long orderId) {
        return stockReservationRepository.findViewsByOrderId(orderId);
    }

    public void ensureStockExists(Long stockItemId) {
        if (!stockItemRepository.existsById(stockItemId)) {
            throw new BusinessException(StockErrorCode.STOCK_NOT_FOUND);
        }
    }
}
