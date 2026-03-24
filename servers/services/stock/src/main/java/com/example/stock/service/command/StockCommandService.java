package com.example.stock.service.command;

import com.example.config.lock.DistributedLock;
import com.example.core.exception.BusinessException;
import com.example.core.id.Snowflake;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.stock.dto.request.*;
import com.example.stock.dto.response.ReservationResponse;
import com.example.stock.dto.response.ReserveOrderStockResponse;
import com.example.stock.dto.response.StockResponse;
import com.example.stock.entity.*;
import com.example.stock.event.ItemAvailableStockChangedEvent;
import com.example.stock.event.StockDecreasedEvent;
import com.example.stock.event.StockDepletedEvent;
import com.example.stock.event.StockIncreasedEvent;
import com.example.stock.event.StockThresholdEvent;
import com.example.stock.exception.StockErrorCode;
import com.example.stock.repository.OrderReserveIdempotencyRepository;
import com.example.stock.repository.StockHistoryRepository;
import com.example.stock.repository.StockItemRepository;
import com.example.stock.repository.StockReservationRepository;
import com.example.stock.repository.StockSyncVersionRepository;
import com.example.stock.service.StockCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StockCommandService {

    private static final double THRESHOLD_PERCENT = 0.1; // 10%
    private static final int RESERVATION_TTL_MINUTES = 10;

    private final StockItemRepository stockItemRepository;
    private final StockReservationRepository stockReservationRepository;
    private final OrderReserveIdempotencyRepository orderReserveIdempotencyRepository;
    private final StockHistoryRepository stockHistoryRepository;
    private final StockSyncVersionRepository stockSyncVersionRepository;
    private final StockCacheService stockCacheService;
    private final EventPublisher eventPublisher;
    private final Snowflake snowflake;

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
        publishItemStockSnapshotEvent(stockItem.getItemId());

        log.info("Stock decreased. stockItemId={}, itemId={}, quantity={}, availableQuantity={}",
                stockItem.getId(), stockItem.getItemId(), request.getQuantity(), stockItem.getAvailableQuantity());

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
        publishItemStockSnapshotEvent(stockItem.getItemId());

        log.info("Stock increased. stockItemId={}, itemId={}, quantity={}, availableQuantity={}",
                stockItem.getId(), stockItem.getItemId(), request.getQuantity(), stockItem.getAvailableQuantity());

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
        publishItemStockSnapshotEvent(stockItem.getItemId());

        log.info("Stock reservation created. reservationId={}, stockItemId={}, userId={}, orderId={}, quantity={}",
                reservation.getId(), stockItem.getId(), request.getUserId(), request.getOrderId(), request.getQuantity());

        return ReservationResponse.from(reservation);
    }

    @DistributedLock(key = "'stock:order-reserve:' + #request.userId + ':' + #request.idempotencyKey", waitTime = 5, leaseTime = 10)
    public ReserveOrderStockResponse reserveOrderStock(ReserveOrderStockRequest request) {
        List<ReserveOrderStockRequest.LineItem> sortedItems = request.getLineItems().stream()
                .sorted(Comparator
                        .comparing(ReserveOrderStockRequest.LineItem::getItemId)
                        .thenComparing(ReserveOrderStockRequest.LineItem::getStockItemType)
                        .thenComparing(ReserveOrderStockRequest.LineItem::getReferenceId))
                .toList();
        String requestSignature = buildReserveOrderRequestSignature(request, sortedItems);

        OrderReserveIdempotency existingIdempotency = orderReserveIdempotencyRepository
                .findByUserIdAndIdempotencyKey(request.getUserId(), request.getIdempotencyKey())
                .orElse(null);
        if (existingIdempotency != null) {
            existingIdempotency.ensureSameRequestSignature(requestSignature);
            if (existingIdempotency.isExpired() || !isReplayable(existingIdempotency.getOrderId())) {
                throw new BusinessException(StockErrorCode.ORDER_RESERVE_IDEMPOTENCY_CONFLICT);
            }
            log.info("Order stock reservation replayed from idempotency record. userId={}, orderId={}, idempotencyKey={}",
                    request.getUserId(), existingIdempotency.getOrderId(), request.getIdempotencyKey());
            return toIdempotentReserveResponse(existingIdempotency, sortedItems);
        }

        Long orderId = snowflake.nextId();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(RESERVATION_TTL_MINUTES);

        List<ReserveOrderStockResponse.ReservedLineItem> reservedItems = new ArrayList<>();
        for (ReserveOrderStockRequest.LineItem lineItem : sortedItems) {
            StockItem stockItem = stockItemRepository.findByItemIdAndStockItemTypeAndReferenceIdWithLock(
                            lineItem.getItemId(), lineItem.getStockItemType(), lineItem.getReferenceId())
                    .orElseThrow(() -> new BusinessException(StockErrorCode.STOCK_ITEM_NOT_FOUND));

            if (stockItem.getAvailableQuantity() < lineItem.getQuantity()) {
                throw new BusinessException(StockErrorCode.INSUFFICIENT_STOCK);
            }

            stockItem.reserve(lineItem.getQuantity());

            StockReservation reservation = StockReservation.create(
                    stockItem.getId(),
                    request.getUserId(),
                    orderId,
                    lineItem.getQuantity(),
                    expiresAt
            );
            stockReservationRepository.save(reservation);

            stockHistoryRepository.save(StockHistory.create(
                    stockItem.getId(),
                    ChangeType.RESERVE,
                    lineItem.getQuantity(),
                    "주문 단위 예약 생성 (userId=" + request.getUserId() + ", orderId=" + orderId + ")",
                    reservation.getId()
            ));

            stockCacheService.cacheStock(stockItem.getId(), stockItem.getAvailableQuantity());
            publishItemStockSnapshotEvent(stockItem.getItemId());

            reservedItems.add(ReserveOrderStockResponse.ReservedLineItem.builder()
                    .itemId(lineItem.getItemId())
                    .stockItemType(lineItem.getStockItemType().name())
                    .referenceId(lineItem.getReferenceId())
                    .quantity(lineItem.getQuantity())
                    .build());
        }

        saveOrderReserveIdempotency(request, requestSignature, orderId, expiresAt);

        log.info("Order stock reservation created. userId={}, orderId={}, lineItemCount={}",
                request.getUserId(), orderId, reservedItems.size());

        return ReserveOrderStockResponse.builder()
                .orderId(orderId)
                .expiresAt(expiresAt)
                .reservedItems(reservedItems)
                .build();
    }

    // ─── TCC: Confirm ─────────────────────────
    @DistributedLock(key = "'stock:reservation:' + #request.reservationId")
    public ReservationResponse confirmReservation(ConfirmReservationRequest request) {
        StockReservation reservation = getReservationWithLock(request.getReservationId());
        if (reservation.isExpired()) {
            expireLockedReservation(reservation);
            throw new BusinessException(StockErrorCode.RESERVATION_EXPIRED);
        }
        reservation.confirm();

        StockItem stockItem = getStockItemWithLock(reservation.getStockItemId());
        stockItem.confirmReservation(reservation.getQuantity());

        stockHistoryRepository.save(StockHistory.create(
                stockItem.getId(), ChangeType.CONFIRM, reservation.getQuantity(),
                "예약 확정", reservation.getId()));

        log.info("Stock reservation confirmed. reservationId={}, stockItemId={}, quantity={}",
                reservation.getId(), stockItem.getId(), reservation.getQuantity());

        return ReservationResponse.from(reservation);
    }

    // ─── TCC: Confirm (by ID — 이벤트 기반) ────
    @DistributedLock(key = "'stock:reservation:' + #reservationId")
    public ReservationResponse confirmReservationById(Long reservationId) {
        StockReservation reservation = getReservationWithLock(reservationId);
        if (reservation.isExpired()) {
            expireLockedReservation(reservation);
            throw new BusinessException(StockErrorCode.RESERVATION_EXPIRED);
        }
        reservation.confirm();

        StockItem stockItem = getStockItemWithLock(reservation.getStockItemId());
        stockItem.confirmReservation(reservation.getQuantity());

        stockHistoryRepository.save(StockHistory.create(
                stockItem.getId(), ChangeType.CONFIRM, reservation.getQuantity(),
                "예약 확정 (결제 완료)", reservation.getId()));

        log.info("Stock reservation confirmed by event. reservationId={}, stockItemId={}, quantity={}",
                reservation.getId(), stockItem.getId(), reservation.getQuantity());

        return ReservationResponse.from(reservation);
    }

    @DistributedLock(key = "'stock:order:' + #orderId", waitTime = 5)
    public List<ReservationResponse> confirmReservationsByOrderId(Long orderId) {
        List<StockReservation> reservations = stockReservationRepository.findByOrderId(orderId);
        if (reservations.isEmpty()) {
            return List.of();
        }

        List<ReservationResponse> confirmedReservations = new ArrayList<>();
        for (StockReservation reservationSummary : reservations) {
            StockReservation reservation = getReservationWithLock(reservationSummary.getId());
            if (reservation.getStatus() != ReservationStatus.RESERVED) {
                continue;
            }

            if (reservation.isExpired()) {
                expireLockedReservation(reservation);
                continue;
            }

            reservation.confirm();

            StockItem stockItem = getStockItemWithLock(reservation.getStockItemId());
            stockItem.confirmReservation(reservation.getQuantity());

            stockHistoryRepository.save(StockHistory.create(
                    stockItem.getId(), ChangeType.CONFIRM, reservation.getQuantity(),
                    "orderId 기반 예약 확정", reservation.getId()));

            confirmedReservations.add(ReservationResponse.from(reservation));
        }

        log.info("Stock reservations confirmed by orderId. orderId={}, reservationCount={}",
                orderId, confirmedReservations.size());

        return confirmedReservations;
    }

    // ─── TCC: Cancel ──────────────────────────
    @DistributedLock(key = "'stock:reservation:' + #reservationId", waitTime = 5)
    public ReservationResponse cancelReservation(Long reservationId) {
        StockReservation reservation = getReservationWithLock(reservationId);
        reservation.cancel();

        StockItem stockItem = getStockItemWithLock(reservation.getStockItemId());
        stockItem.cancelReservation(reservation.getQuantity());

        stockHistoryRepository.save(StockHistory.create(
                stockItem.getId(), ChangeType.CANCEL, reservation.getQuantity(),
                "예약 취소", reservation.getId()));

        stockCacheService.cacheStock(stockItem.getId(), stockItem.getAvailableQuantity());
        publishItemStockSnapshotEvent(stockItem.getItemId());

        log.info("Stock reservation cancelled. reservationId={}, stockItemId={}, quantity={}",
                reservation.getId(), stockItem.getId(), reservation.getQuantity());

        return ReservationResponse.from(reservation);
    }

    @DistributedLock(key = "'stock:order:' + #orderId", waitTime = 5)
    public List<ReservationResponse> cancelReservationsByOrderId(Long orderId) {
        List<StockReservation> reservations = stockReservationRepository.findByOrderId(orderId);
        if (reservations.isEmpty()) {
            return List.of();
        }

        List<ReservationResponse> cancelledReservations = new ArrayList<>();
        for (StockReservation reservationSummary : reservations) {
            StockReservation reservation = getReservationWithLock(reservationSummary.getId());
            if (reservation.getStatus() != ReservationStatus.RESERVED) {
                continue;
            }

            reservation.cancel();

            StockItem stockItem = getStockItemWithLock(reservation.getStockItemId());
            stockItem.cancelReservation(reservation.getQuantity());

            stockHistoryRepository.save(StockHistory.create(
                    stockItem.getId(), ChangeType.CANCEL, reservation.getQuantity(),
                    "orderId 기반 예약 취소", reservation.getId()));

            stockCacheService.cacheStock(stockItem.getId(), stockItem.getAvailableQuantity());
            publishItemStockSnapshotEvent(stockItem.getItemId());
            cancelledReservations.add(ReservationResponse.from(reservation));
        }

        log.info("Stock reservations cancelled by orderId. orderId={}, reservationCount={}",
                orderId, cancelledReservations.size());

        return cancelledReservations;
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
        publishItemStockSnapshotEvent(stockItem.getItemId());

        log.info("Stock initialized. stockItemId={}, itemId={}, stockItemType={}, referenceId={}, totalQuantity={}",
                stockItem.getId(), stockItem.getItemId(), stockItem.getStockItemType(), stockItem.getReferenceId(),
                stockItem.getTotalQuantity());

        return StockResponse.from(stockItem);
    }

    @DistributedLock(key = "'stock:reservation:' + #reservationId", waitTime = 3)
    public void expireReservationById(Long reservationId) {
        StockReservation reservation = stockReservationRepository.findByIdWithLock(reservationId).orElse(null);
        if (reservation == null || reservation.getStatus() != ReservationStatus.RESERVED || !reservation.isExpired()) {
            return;
        }

        expireLockedReservation(reservation);
    }

    private void expireLockedReservation(StockReservation reservation) {
        reservation.expire();

        StockItem stockItem = stockItemRepository.findByIdWithLock(reservation.getStockItemId())
                .orElse(null);
        if (stockItem != null) {
            stockItem.cancelReservation(reservation.getQuantity());
            stockCacheService.cacheStock(stockItem.getId(), stockItem.getAvailableQuantity());

            stockHistoryRepository.save(StockHistory.create(
                    stockItem.getId(), ChangeType.EXPIRE, reservation.getQuantity(),
                    "예약 만료 자동 복원", reservation.getId()));
            publishItemStockSnapshotEvent(stockItem.getItemId());
            log.info("Stock reservation expired and restored. reservationId={}, stockItemId={}, quantity={}",
                    reservation.getId(), stockItem.getId(), reservation.getQuantity());
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

    private void publishItemStockSnapshotEvent(Long itemId) {
        if (itemId == null) {
            return;
        }
        long stockVersion = nextStockVersion(itemId);
        int availableStockTotal = sumAvailableStockByItemId(itemId);
        eventPublisher.publish(
                new ItemAvailableStockChangedEvent(itemId, availableStockTotal, stockVersion),
                EventMetadata.of("Item", String.valueOf(itemId))
        );
    }

    private long nextStockVersion(Long itemId) {
        for (int attempt = 0; attempt < 2; attempt++) {
            StockSyncVersion syncVersion = stockSyncVersionRepository.findByItemIdWithLock(itemId)
                    .orElse(null);
            if (syncVersion != null) {
                return syncVersion.incrementAndGet();
            }
            try {
                stockSyncVersionRepository.saveAndFlush(StockSyncVersion.initialize(itemId));
            } catch (DataIntegrityViolationException e) {
                log.debug("Stock sync version row already exists. itemId={}", itemId);
            }
        }

        StockSyncVersion syncVersion = stockSyncVersionRepository.findByItemIdWithLock(itemId)
                .orElseGet(() -> stockSyncVersionRepository.saveAndFlush(StockSyncVersion.initialize(itemId)));
        return syncVersion.incrementAndGet();
    }

    private int sumAvailableStockByItemId(Long itemId) {
        Long total = stockItemRepository.sumAvailableQuantityByItemId(itemId);
        if (total == null) {
            return 0;
        }
        return Math.toIntExact(total);
    }

    private void saveOrderReserveIdempotency(
            ReserveOrderStockRequest request,
            String requestSignature,
            Long orderId,
            LocalDateTime expiresAt
    ) {
        try {
            orderReserveIdempotencyRepository.save(OrderReserveIdempotency.create(
                    request.getUserId(),
                    request.getIdempotencyKey(),
                    requestSignature,
                    orderId,
                    expiresAt
            ));
        } catch (DataIntegrityViolationException e) {
            log.info("Order reserve idempotency row already exists. userId={}, idempotencyKey={}",
                    request.getUserId(), request.getIdempotencyKey());
        }
    }

    private boolean isReplayable(Long orderId) {
        List<StockReservation> reservations = stockReservationRepository.findByOrderId(orderId);
        if (reservations.isEmpty()) {
            return false;
        }
        return reservations.stream().allMatch(reservation -> reservation.getStatus() == ReservationStatus.RESERVED);
    }

    private ReserveOrderStockResponse toIdempotentReserveResponse(
            OrderReserveIdempotency idempotency,
            List<ReserveOrderStockRequest.LineItem> sortedItems
    ) {
        return ReserveOrderStockResponse.builder()
                .orderId(idempotency.getOrderId())
                .expiresAt(idempotency.getExpiresAt())
                .reservedItems(sortedItems.stream()
                        .map(lineItem -> ReserveOrderStockResponse.ReservedLineItem.builder()
                                .itemId(lineItem.getItemId())
                                .stockItemType(lineItem.getStockItemType().name())
                                .referenceId(lineItem.getReferenceId())
                                .quantity(lineItem.getQuantity())
                                .build())
                        .toList())
                .build();
    }

    private String buildReserveOrderRequestSignature(
            ReserveOrderStockRequest request,
            List<ReserveOrderStockRequest.LineItem> sortedItems
    ) {
        String normalizedChannelType = request.getChannelType() == null
                ? ""
                : request.getChannelType().trim().toUpperCase(Locale.ROOT);
        String normalizedChannelRefId = request.getChannelRefId() == null
                ? ""
                : String.valueOf(request.getChannelRefId());
        String lineSignature = sortedItems.stream()
                .map(lineItem -> lineItem.getItemId()
                        + ":" + lineItem.getStockItemType().name()
                        + ":" + lineItem.getReferenceId()
                        + ":" + lineItem.getQuantity())
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
        return normalizedChannelType + "#" + normalizedChannelRefId + "#" + lineSignature;
    }

    private StockItem getStockItemWithLock(Long stockItemId) {
        return stockItemRepository.findByIdWithLock(stockItemId)
                .orElseThrow(() -> new BusinessException(StockErrorCode.STOCK_NOT_FOUND));
    }

    private StockReservation getReservationWithLock(Long reservationId) {
        return stockReservationRepository.findByIdWithLock(reservationId)
                .orElseThrow(() -> new BusinessException(StockErrorCode.RESERVATION_NOT_FOUND));
    }
}
