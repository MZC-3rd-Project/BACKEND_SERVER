package com.example.sales.service.command;

import com.example.clients.order.dto.OrderCreateLineItem;
import com.example.clients.order.dto.OrderCreateRequest;
import com.example.clients.order.dto.OrderCreateResponse;
import com.example.clients.order.exception.OrderClientException;
import com.example.clients.order.facade.OrderCreateClientFacade;
import com.example.clients.stock.dto.ReserveOrderStockLineItem;
import com.example.clients.stock.dto.ReserveOrderStockRequest;
import com.example.clients.stock.dto.ReserveOrderStockResponse;
import com.example.clients.stock.dto.ReservedOrderStockLineItem;
import com.example.clients.stock.exception.StockClientConflictException;
import com.example.clients.stock.exception.StockClientException;
import com.example.clients.stock.facade.StockOrderReservationClientFacade;
import com.example.clients.stock.facade.StockReservationClientFacade;
import com.example.config.lock.DistributedLock;
import com.example.core.exception.BusinessException;
import com.example.sales.domain.checkout.CheckoutLineItemKey;
import com.example.sales.domain.checkout.ReserveIntent;
import com.example.sales.dto.checkout.CheckoutDraft;
import com.example.sales.dto.checkout.request.CheckoutCancelRequest;
import com.example.sales.dto.checkout.request.CheckoutReserveRequest;
import com.example.sales.dto.checkout.request.CheckoutSubmitRequest;
import com.example.sales.dto.checkout.response.CheckoutCancelResponse;
import com.example.sales.dto.checkout.response.CheckoutReserveResponse;
import com.example.sales.dto.checkout.response.CheckoutSubmitResponse;
import com.example.sales.entity.CheckoutSession;
import com.example.sales.entity.CheckoutSessionLineItem;
import com.example.sales.entity.CheckoutSessionStatus;
import com.example.sales.entity.CheckoutSubmitAttempt;
import com.example.sales.event.CheckoutReservedEvent;
import com.example.sales.exception.SalesErrorCode;
import com.example.sales.repository.CheckoutSessionRepository;
import com.example.sales.repository.CheckoutSubmitAttemptRepository;
import com.example.sales.service.query.CheckoutQuoteQueryService;
import com.example.sales.service.support.CheckoutSnapshotAssembler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CheckoutCommandService {

    private final OrderCreateClientFacade orderCreateClientFacade;
    private final CheckoutSubmitFailurePolicy checkoutSubmitFailurePolicy;
    private final StockOrderReservationClientFacade stockOrderReservationClientFacade;
    private final StockReservationClientFacade stockReservationClientFacade;
    private final CheckoutQuoteQueryService checkoutQuoteQueryService;
    private final CheckoutSnapshotAssembler checkoutSnapshotAssembler;
    private final CheckoutDraftRedisService checkoutDraftRedisService;
    private final CheckoutQuoteCacheRedisService checkoutQuoteCacheRedisService;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final CheckoutSubmitAttemptRepository checkoutSubmitAttemptRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    @DistributedLock(key = "'checkout:reserve:' + #userId + ':' + #request.idempotencyKey", waitTime = 5, leaseTime = 10)
    @Transactional
    public CheckoutReserveResponse reserve(CheckoutReserveRequest request, Long userId) {
        ExistingReserveAttempt existingAttempt = findExistingReserveAttempt(userId, request.getIdempotencyKey());
        if (existingAttempt != null) {
            validateExistingReserveAttempt(existingAttempt, request);
            if (!existingAttempt.isReplayable()) {
                throw new BusinessException(SalesErrorCode.CHECKOUT_IDEMPOTENCY_CONFLICT);
            }
            return toReserveResponse(existingAttempt.draft());
        }

        StockReserveChannelContext stockChannelContext = resolveStockReserveChannelContext(request.getLineItems());
        ReserveOrderStockResponse stockResponse;
        try {
            stockResponse = stockOrderReservationClientFacade.reserveOrderStock(new ReserveOrderStockRequest(
                    stockChannelContext.channelType(),
                    stockChannelContext.channelRefId(),
                    userId,
                    request.getIdempotencyKey(),
                    request.getLineItems().stream()
                            .map(item -> new ReserveOrderStockLineItem(
                                    item.getItemId(),
                                    item.getStockItemType(),
                                    item.getReferenceId(),
                                    item.getQuantity()))
                            .toList()
            ));
        } catch (StockClientConflictException e) {
            throw new BusinessException(SalesErrorCode.STOCK_INSUFFICIENT);
        } catch (StockClientException e) {
            throw new BusinessException(SalesErrorCode.STOCK_SERVICE_ERROR);
        }

        CheckoutDraft draft = CheckoutDraft.builder()
                .orderId(stockResponse.orderId())
                .userId(userId)
                .idempotencyKey(request.getIdempotencyKey())
                .expiresAt(stockResponse.expiresAt())
                .lineItems(request.getLineItems().stream()
                        .map(item -> CheckoutDraft.LineItem.builder()
                                .itemId(item.getItemId())
                                .channelType(item.getChannelType())
                                .channelRefId(item.getChannelRefId())
                                .stockItemType(item.getStockItemType())
                                .referenceId(item.getReferenceId())
                                .quantity(item.getQuantity())
                                .build())
                        .toList())
                .build();

        Duration ttl = ttlUntil(stockResponse.expiresAt());
        checkoutDraftRedisService.saveDraft(draft, ttl);
        checkoutSessionRepository.save(toCheckoutSession(request, userId, stockResponse));
        applicationEventPublisher.publishEvent(new CheckoutReservedEvent(stockResponse.orderId()));
        return CheckoutReserveResponse.builder()
                .orderId(stockResponse.orderId())
                .expiresAt(stockResponse.expiresAt())
                .reservedItems(stockResponse.reservedItems().stream()
                        .map(this::toReservedLineItem)
                        .toList())
                .build();
    }

    @Transactional
    @DistributedLock(key = "'checkout:submit:' + #request.orderId", waitTime = 5, leaseTime = 10)
    public CheckoutSubmitResponse submit(CheckoutSubmitRequest request, Long userId) {
        CheckoutSession session = checkoutSessionRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new BusinessException(SalesErrorCode.CHECKOUT_SESSION_NOT_FOUND));
        validateOwnedSession(session, userId);
        validateSubmittableSession(session);

        if (session.getStatus() == CheckoutSessionStatus.ORDER_CREATED) {
            return toSubmitResponse(session);
        }

        checkoutQuoteQueryService.ensureSubmitQuoteSnapshot(session);

        OrderCreateRequest orderRequest = toOrderCreateRequest(session, request);
        String requestPayloadJson = toJson(orderRequest, "checkout submit request");

        session.markSubmitting();
        CheckoutSubmitAttempt attempt = checkoutSubmitAttemptRepository.save(
                CheckoutSubmitAttempt.createRequested(session, session.getOrderId(), requestPayloadJson)
        );

        try {
            OrderCreateResponse orderResponse = orderCreateClientFacade.createOrder(orderRequest);
            session.markOrderCreated();
            attempt.markSucceeded(toJson(orderResponse, "checkout submit response"));
            return toSubmitResponse(session);
        } catch (OrderClientException e) {
            handleSubmitFailure(session, attempt, e);
            throw new BusinessException(SalesErrorCode.ORDER_SERVICE_ERROR, e.getMessage(), e);
        }
    }

    @Transactional
    @DistributedLock(key = "'checkout:quote:' + #request.orderId", waitTime = 5, leaseTime = 10)
    public CheckoutCancelResponse cancel(CheckoutCancelRequest request, Long userId) {
        CheckoutSession session = checkoutSessionRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new BusinessException(SalesErrorCode.CHECKOUT_SESSION_NOT_FOUND));
        validateOwnedSession(session, userId);

        if (session.getStatus() == CheckoutSessionStatus.CANCELLED
                || session.getStatus() == CheckoutSessionStatus.EXPIRED) {
            clearCheckoutCaches(session);
            return toCancelResponse(session);
        }

        session.cancel();

        try {
            stockReservationClientFacade.cancelReservationsByOrderId(session.getOrderId());
        } catch (StockClientException e) {
            throw new BusinessException(SalesErrorCode.STOCK_SERVICE_ERROR);
        }

        clearCheckoutCaches(session);
        return toCancelResponse(session);
    }

    private ExistingReserveAttempt findExistingReserveAttempt(Long userId, String idempotencyKey) {
        CheckoutDraft redisDraft = checkoutDraftRedisService.findOrderIdByIdempotencyKey(userId, idempotencyKey)
                .flatMap(checkoutDraftRedisService::findDraft)
                .orElse(null);
        if (redisDraft != null) {
            return new ExistingReserveAttempt(redisDraft, redisDraft.getExpiresAt() == null || LocalDateTime.now().isBefore(redisDraft.getExpiresAt()));
        }

        CheckoutSession persistedSession = checkoutSessionRepository.findTopByUserIdAndIdempotencyKeyOrderByCreatedAtDesc(userId, idempotencyKey)
                .orElse(null);
        if (persistedSession == null) {
            return null;
        }
        CheckoutDraft persistedDraft = checkoutSnapshotAssembler.toDraft(persistedSession);
        boolean replayable = isReplayableSession(persistedSession);
        if (replayable && persistedDraft.getExpiresAt() != null && LocalDateTime.now().isBefore(persistedDraft.getExpiresAt())) {
            checkoutDraftRedisService.saveDraft(persistedDraft, ttlUntil(persistedDraft.getExpiresAt()));
        }
        return new ExistingReserveAttempt(persistedDraft, replayable);
    }

    private Duration ttlUntil(LocalDateTime expiresAt) {
        Duration ttl = Duration.between(LocalDateTime.now(), expiresAt);
        return ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(1) : ttl;
    }

    private void validateOwnedSession(CheckoutSession session, Long userId) {
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(SalesErrorCode.CHECKOUT_SESSION_FORBIDDEN);
        }
    }

    private void validateSubmittableSession(CheckoutSession session) {
        if (session.getStatus() == CheckoutSessionStatus.CANCELLED) {
            throw new BusinessException(SalesErrorCode.CHECKOUT_SESSION_CANCELLED);
        }
        if (session.getStatus() == CheckoutSessionStatus.EXPIRED
                || (session.getExpiresAt() != null && LocalDateTime.now().isAfter(session.getExpiresAt()))) {
            clearCheckoutCaches(session);
            throw new BusinessException(SalesErrorCode.CHECKOUT_SESSION_EXPIRED);
        }
        if (session.getStatus() == CheckoutSessionStatus.SUBMITTING) {
            throw new BusinessException(SalesErrorCode.CHECKOUT_SUBMIT_IN_PROGRESS);
        }
    }

    private void clearCheckoutCaches(CheckoutSession session) {
        checkoutDraftRedisService.deleteDraft(session.getOrderId(), session.getUserId(), session.getIdempotencyKey());
        checkoutQuoteCacheRedisService.deleteQuote(session.getOrderId());
    }

    private CheckoutReserveResponse toReserveResponse(CheckoutDraft draft) {
        return CheckoutReserveResponse.builder()
                .orderId(draft.getOrderId())
                .expiresAt(draft.getExpiresAt())
                .reservedItems(draft.getLineItems().stream()
                        .map(item -> CheckoutReserveResponse.ReservedLineItem.builder()
                                .itemId(item.getItemId())
                                .stockItemType(item.getStockItemType())
                                .referenceId(item.getReferenceId())
                                .quantity(item.getQuantity())
                                .build())
                        .toList())
                .build();
    }

    private CheckoutReserveResponse.ReservedLineItem toReservedLineItem(ReservedOrderStockLineItem item) {
        return CheckoutReserveResponse.ReservedLineItem.builder()
                .itemId(item.itemId())
                .stockItemType(item.stockItemType())
                .referenceId(item.referenceId())
                .quantity(item.quantity())
                .build();
    }

    private CheckoutCancelResponse toCancelResponse(CheckoutSession session) {
        return CheckoutCancelResponse.builder()
                .orderId(session.getOrderId())
                .status(session.getStatus())
                .build();
    }

    private CheckoutSubmitResponse toSubmitResponse(CheckoutSession session) {
        return CheckoutSubmitResponse.builder()
                .orderId(session.getOrderId())
                .status(session.getStatus())
                .submittedAt(session.getOrderCreatedAt())
                .build();
    }

    private OrderCreateRequest toOrderCreateRequest(CheckoutSession session, CheckoutSubmitRequest request) {
        return new OrderCreateRequest(
                session.getOrderId(),
                session.getUserId(),
                session.getExpiresAt(),
                calculateTotalAmount(session),
                request.getRecipientName(),
                request.getRecipientPhone(),
                request.getDeliveryAddressId(),
                request.getDeliveryMemo(),
                session.getLineItems().stream()
                        .map(lineItem -> new OrderCreateLineItem(
                                lineItem.getChannelType(),
                                lineItem.getChannelRefId(),
                                lineItem.getItemId(),
                                lineItem.getItemType(),
                                lineItem.getTitle(),
                                lineItem.getSellerId(),
                                lineItem.getStoreId(),
                                lineItem.getStockItemType(),
                                lineItem.getReferenceId(),
                                lineItem.getReferenceName(),
                                lineItem.getQuantity(),
                                lineItem.getBaseUnitPrice(),
                                lineItem.getFinalUnitPrice(),
                                lineItem.getLineAmount()
                        ))
                        .toList()
        );
    }

    private long calculateTotalAmount(CheckoutSession session) {
        return session.getLineItems().stream()
                .map(CheckoutSessionLineItem::getLineAmount)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
    }

    private String toJson(Object value, String description) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(SalesErrorCode.ORDER_SERVICE_ERROR, description + " serialization failed", e);
        }
    }

    private void handleSubmitFailure(
            CheckoutSession session,
            CheckoutSubmitAttempt attempt,
            OrderClientException exception
    ) {
        CheckoutSubmitFailureDecision decision = checkoutSubmitFailurePolicy.decide(exception);
        session.markFailed(exception.getErrorCode(), exception.getMessage());
        if (decision.retryable()) {
            Duration retryDelay = decision.retryDelay() == null ? Duration.ofMinutes(1) : decision.retryDelay();
            attempt.scheduleRetry(exception.getMessage(), LocalDateTime.now().plus(retryDelay));
        } else {
            attempt.markFailed(exception.getMessage());
        }

        if (!decision.releaseReservation()) {
            throw new BusinessException(decision.errorCode(), exception.getMessage(), exception);
        }

        try {
            stockReservationClientFacade.cancelReservationsByOrderId(session.getOrderId());
            session.cancel();
            clearCheckoutCaches(session);
        } catch (StockClientException e) {
            throw new BusinessException(SalesErrorCode.STOCK_SERVICE_ERROR, e.getMessage(), e);
        }

        throw new BusinessException(decision.errorCode(), exception.getMessage(), exception);
    }

    private CheckoutSession toCheckoutSession(
            CheckoutReserveRequest request,
            Long userId,
            ReserveOrderStockResponse stockResponse
    ) {
        CheckoutSession session = CheckoutSession.createReserved(
                stockResponse.orderId(),
                userId,
                request.getIdempotencyKey(),
                stockResponse.expiresAt()
        );

        List<CheckoutReserveRequest.LineItem> requestLineItems = request.getLineItems();
        for (int i = 0; i < requestLineItems.size(); i++) {
            CheckoutReserveRequest.LineItem item = requestLineItems.get(i);
            session.addLineItem(CheckoutSessionLineItem.createReserved(
                    i + 1,
                    item.getChannelType(),
                    item.getChannelRefId(),
                    item.getItemId(),
                    item.getStockItemType(),
                    item.getReferenceId(),
                    item.getQuantity()
            ));
        }
        return session;
    }

    private boolean isReplayableSession(CheckoutSession session) {
        if (session.getExpiresAt() != null && LocalDateTime.now().isAfter(session.getExpiresAt())) {
            return false;
        }
        return session.getStatus() != CheckoutSessionStatus.EXPIRED
                && session.getStatus() != CheckoutSessionStatus.CANCELLED;
    }

    private void validateExistingReserveAttempt(ExistingReserveAttempt existingAttempt, CheckoutReserveRequest request) {
        if (!hasSameReserveIntent(existingAttempt.draft(), request)) {
            throw new BusinessException(SalesErrorCode.CHECKOUT_IDEMPOTENCY_CONFLICT);
        }
    }

    private boolean hasSameReserveIntent(CheckoutDraft draft, CheckoutReserveRequest request) {
        return toReserveIntent(draft).matches(toReserveIntent(request));
    }

    private String normalizeEnumLike(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private record ExistingReserveAttempt(CheckoutDraft draft, boolean replayable) {
        private boolean isReplayable() {
            return replayable;
        }
    }

    private ReserveIntent toReserveIntent(CheckoutDraft draft) {
        return new ReserveIntent(draft.getLineItems().stream()
                .map(item -> new ReserveIntent.LineItem(
                        new CheckoutLineItemKey(item.getItemId(), item.getReferenceId()),
                        item.getChannelType(),
                        item.getChannelRefId(),
                        item.getStockItemType(),
                        item.getQuantity()
                ))
                .toList());
    }

    private ReserveIntent toReserveIntent(CheckoutReserveRequest request) {
        return new ReserveIntent(request.getLineItems().stream()
                .map(item -> new ReserveIntent.LineItem(
                        new CheckoutLineItemKey(item.getItemId(), item.getReferenceId()),
                        item.getChannelType(),
                        item.getChannelRefId(),
                        item.getStockItemType(),
                        item.getQuantity()
                ))
                .toList());
    }

    private StockReserveChannelContext resolveStockReserveChannelContext(List<CheckoutReserveRequest.LineItem> lineItems) {
        List<String> normalizedChannelTypes = lineItems.stream()
                .map(CheckoutReserveRequest.LineItem::getChannelType)
                .map(this::normalizeEnumLike)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<Long> distinctChannelRefIds = lineItems.stream()
                .map(CheckoutReserveRequest.LineItem::getChannelRefId)
                .distinct()
                .toList();

        if (normalizedChannelTypes.size() == 1 && distinctChannelRefIds.size() <= 1) {
            return new StockReserveChannelContext(
                    normalizedChannelTypes.getFirst(),
                    distinctChannelRefIds.isEmpty() ? null : distinctChannelRefIds.getFirst()
            );
        }

        return new StockReserveChannelContext("MIXED", null);
    }

    private record StockReserveChannelContext(String channelType, Long channelRefId) {
    }
}
