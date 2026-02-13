package com.example.stock.service.command;

import com.example.config.redis.lock.DistributedLock;
import com.example.core.exception.BusinessException;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.stock.dto.request.*;
import com.example.stock.dto.response.ReservationResponse;
import com.example.stock.dto.response.StockResponse;
import com.example.stock.entity.*;
import com.example.stock.event.StockDecreasedEvent;
import com.example.stock.event.StockDepletedEvent;
import com.example.stock.event.StockIncreasedEvent;
import com.example.stock.event.StockThresholdEvent;
import com.example.stock.exception.StockErrorCode;
import com.example.stock.repository.StockHistoryRepository;
import com.example.stock.repository.StockItemRepository;
import com.example.stock.repository.StockReservationRepository;
import com.example.stock.service.StockCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StockCommandService {

    private static final double THRESHOLD_PERCENT = 0.1; // 10%
    private static final int RESERVATION_TTL_MINUTES = 10;

    private final StockItemRepository stockItemRepository;
    private final StockReservationRepository stockReservationRepository;
    private final StockHistoryRepository stockHistoryRepository;
    private final StockCacheService stockCacheService;
    private final EventPublisher eventPublisher;

    @DistributedLock(key = "'stock:' + #request.stockItemId")
    public StockResponse decreaseStock(StockDecreaseRequest request) {
        StockItem stockItem = getStockItemWithLock(request.getStockItemId());

        if (stockItem.getAvailableQuantity() < request.getQuantity()) {
            throw new BusinessException(StockErrorCode.INSUFFICIENT_STOCK);
        }

        stockItem.decrease(request.getQuantity());

        stockHistoryRepository.save(StockHistory.create(
                stockItem.getId(), ChangeType.DECREASE, request.getQuantity(), request.getReason()));

        // Redis 캐시 동기화
        stockCacheService.cacheStock(stockItem.getId(), stockItem.getAvailableQuantity());

        // 이벤트 발행
        publishStockEvents(stockItem, request.getQuantity());

        return StockResponse.from(stockItem);
    }

    @DistributedLock(key = "'stock:' + #request.stockItemId")
    public StockResponse increaseStock(StockIncreaseRequest request) {
        StockItem stockItem = getStockItemWithLock(request.getStockItemId());

        stockItem.increase(request.getQuantity());

        stockHistoryRepository.save(StockHistory.create(
                stockItem.getId(), ChangeType.INCREASE, request.getQuantity(), request.getReason()));

        stockCacheService.cacheStock(stockItem.getId(), stockItem.getAvailableQuantity());

        eventPublisher.publish(
                new StockIncreasedEvent(stockItem.getId(), stockItem.getItemId(), request.getQuantity(), stockItem.getAvailableQuantity()),
                EventMetadata.of("StockItem", String.valueOf(stockItem.getId())));

        return StockResponse.from(stockItem);
    }

    // ─── TCC: Try ─────────────────────────────
    @DistributedLock(key = "'stock:' + #request.stockItemId")
    public ReservationResponse reserveStock(ReserveStockRequest request) {
        StockItem stockItem = getStockItemWithLock(request.getStockItemId());

        if (stockItem.getAvailableQuantity() < request.getQuantity()) {
            throw new BusinessException(StockErrorCode.INSUFFICIENT_STOCK);
        }

        stockItem.reserve(request.getQuantity());

        StockReservation reservation = StockReservation.create(
                stockItem.getId(), request.getUserId(), request.getOrderId(), request.getQuantity(), RESERVATION_TTL_MINUTES);
        stockReservationRepository.save(reservation);

        stockHistoryRepository.save(StockHistory.create(
                stockItem.getId(), ChangeType.RESERVE, request.getQuantity(),
                "예약 생성 (userId=" + request.getUserId() + ")", reservation.getId()));

        stockCacheService.cacheStock(stockItem.getId(), stockItem.getAvailableQuantity());

        return ReservationResponse.from(reservation);
    }

    // ─── TCC: Confirm ─────────────────────────
    public ReservationResponse confirmReservation(ConfirmReservationRequest request) {
        StockReservation reservation = getReservation(request.getReservationId());
        reservation.confirm();

        StockItem stockItem = getStockItemWithLock(reservation.getStockItemId());
        stockItem.confirmReservation(reservation.getQuantity());

        stockHistoryRepository.save(StockHistory.create(
                stockItem.getId(), ChangeType.CONFIRM, reservation.getQuantity(),
                "예약 확정", reservation.getId()));

        return ReservationResponse.from(reservation);
    }

    // ─── TCC: Confirm (by ID — 이벤트 기반) ────
    public ReservationResponse confirmReservationById(Long reservationId) {
        StockReservation reservation = getReservation(reservationId);
        reservation.confirm();

        StockItem stockItem = getStockItemWithLock(reservation.getStockItemId());
        stockItem.confirmReservation(reservation.getQuantity());

        stockHistoryRepository.save(StockHistory.create(
                stockItem.getId(), ChangeType.CONFIRM, reservation.getQuantity(),
                "예약 확정 (결제 완료)", reservation.getId()));

        return ReservationResponse.from(reservation);
    }

    // ─── TCC: Cancel ──────────────────────────
    @DistributedLock(key = "'stock:' + #reservationId", waitTime = 5)
    public ReservationResponse cancelReservation(Long reservationId) {
        StockReservation reservation = getReservation(reservationId);
        reservation.cancel();

        StockItem stockItem = getStockItemWithLock(reservation.getStockItemId());
        stockItem.cancelReservation(reservation.getQuantity());

        stockHistoryRepository.save(StockHistory.create(
                stockItem.getId(), ChangeType.CANCEL, reservation.getQuantity(),
                "예약 취소", reservation.getId()));

        stockCacheService.cacheStock(stockItem.getId(), stockItem.getAvailableQuantity());

        return ReservationResponse.from(reservation);
    }

    public StockResponse initializeStock(InitializeStockRequest request) {
        StockItem stockItem = stockItemRepository.findByItemIdAndStockItemTypeAndReferenceId(
                request.getItemId(), request.getStockItemType(), request.getReferenceId()
        ).orElse(null);

        if (stockItem != null) {
            // Upsert: 기존 재고 업데이트
            stockItem.updateTotal(request.getTotalQuantity());

            stockHistoryRepository.save(StockHistory.create(
                    stockItem.getId(), ChangeType.INCREASE, request.getTotalQuantity(), "재고 재초기화 (Upsert)"));
        } else {
            // Create: 신규 재고 생성
            stockItem = StockItem.create(
                    request.getItemId(), request.getStockItemType(),
                    request.getReferenceId(), request.getTotalQuantity());
            stockItemRepository.save(stockItem);

            stockHistoryRepository.save(StockHistory.create(
                    stockItem.getId(), ChangeType.INCREASE, request.getTotalQuantity(), "재고 초기화"));
        }

        stockCacheService.cacheStock(stockItem.getId(), stockItem.getAvailableQuantity());

        return StockResponse.from(stockItem);
    }

    @DistributedLock(key = "'stock:reservation:' + #reservationId", waitTime = 3)
    public void expireReservationById(Long reservationId) {
        StockReservation reservation = stockReservationRepository.findById(reservationId).orElse(null);
        if (reservation == null || reservation.getStatus() != ReservationStatus.RESERVED || !reservation.isExpired()) {
            return;
        }

        reservation.expire();

        StockItem stockItem = stockItemRepository.findByIdWithLock(reservation.getStockItemId())
                .orElse(null);
        if (stockItem != null) {
            stockItem.cancelReservation(reservation.getQuantity());
            stockCacheService.cacheStock(stockItem.getId(), stockItem.getAvailableQuantity());

            stockHistoryRepository.save(StockHistory.create(
                    stockItem.getId(), ChangeType.EXPIRE, reservation.getQuantity(),
                    "예약 만료 자동 복원", reservation.getId()));
        }
    }

    private void publishStockEvents(StockItem stockItem, int quantity) {
        eventPublisher.publish(
                new StockDecreasedEvent(stockItem.getId(), stockItem.getItemId(), quantity, stockItem.getAvailableQuantity()),
                EventMetadata.of("StockItem", String.valueOf(stockItem.getId())));

        if (stockItem.isDepleted()) {
            eventPublisher.publish(
                    new StockDepletedEvent(stockItem.getId(), stockItem.getItemId()),
                    EventMetadata.of("StockItem", String.valueOf(stockItem.getId())));
        } else if (stockItem.isThresholdReached(THRESHOLD_PERCENT)) {
            eventPublisher.publish(
                    new StockThresholdEvent(stockItem.getId(), stockItem.getItemId(),
                            stockItem.getAvailableQuantity(), stockItem.getTotalQuantity()),
                    EventMetadata.of("StockItem", String.valueOf(stockItem.getId())));
        }
    }

    private StockItem getStockItemWithLock(Long stockItemId) {
        return stockItemRepository.findByIdWithLock(stockItemId)
                .orElseThrow(() -> new BusinessException(StockErrorCode.STOCK_NOT_FOUND));
    }

    private StockReservation getReservation(Long reservationId) {
        return stockReservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(StockErrorCode.RESERVATION_NOT_FOUND));
    }
}
