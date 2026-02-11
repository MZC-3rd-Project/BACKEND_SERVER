package com.example.stock.service.query;

import com.example.core.exception.BusinessException;
import com.example.stock.dto.response.StockHistoryResponse;
import com.example.stock.dto.response.StockResponse;
import com.example.stock.dto.response.StockSummaryResponse;
import com.example.stock.entity.StockItem;
import com.example.stock.exception.StockErrorCode;
import com.example.stock.repository.StockHistoryRepository;
import com.example.stock.repository.StockItemRepository;
import com.example.stock.service.StockCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockQueryService {

    private final StockItemRepository stockItemRepository;
    private final StockHistoryRepository stockHistoryRepository;
    private final StockCacheService stockCacheService;

    public StockResponse getStock(Long stockItemId) {
        StockItem stockItem = stockItemRepository.findById(stockItemId)
                .orElseThrow(() -> new BusinessException(StockErrorCode.STOCK_NOT_FOUND));
        return StockResponse.from(stockItem);
    }

    public StockSummaryResponse getStocksByItemId(Long itemId) {
        List<StockItem> stockItems = stockItemRepository.findByItemId(itemId);
        return StockSummaryResponse.of(itemId, stockItems);
    }

    public List<StockHistoryResponse> getStockHistory(Long stockItemId, int page, int size) {
        // 재고 존재 확인
        stockItemRepository.findById(stockItemId)
                .orElseThrow(() -> new BusinessException(StockErrorCode.STOCK_NOT_FOUND));

        return stockHistoryRepository.findByStockItemIdOrderByCreatedAtDesc(
                        stockItemId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                .map(StockHistoryResponse::from)
                .toList();
    }
}
