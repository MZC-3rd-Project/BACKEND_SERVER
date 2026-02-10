package com.example.stock.service.query;

import com.example.core.exception.BusinessException;
import com.example.stock.dto.response.StockResponse;
import com.example.stock.dto.response.StockSummaryResponse;
import com.example.stock.entity.StockItem;
import com.example.stock.exception.StockErrorCode;
import com.example.stock.repository.StockItemRepository;
import com.example.stock.service.StockCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockQueryService {

    private final StockItemRepository stockItemRepository;
    private final StockCacheService stockCacheService;

    public StockResponse getStock(Long stockItemId) {
        // Redis 캐시 우선 조회는 availableQuantity만 해당 — 전체 정보는 DB에서
        StockItem stockItem = stockItemRepository.findById(stockItemId)
                .orElseThrow(() -> new BusinessException(StockErrorCode.STOCK_NOT_FOUND));
        return StockResponse.from(stockItem);
    }

    public StockSummaryResponse getStocksByItemId(Long itemId) {
        List<StockItem> stockItems = stockItemRepository.findByItemId(itemId);
        return StockSummaryResponse.of(itemId, stockItems);
    }
}
