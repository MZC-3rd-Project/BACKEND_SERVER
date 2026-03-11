package com.example.sales.service.command;

import com.example.clients.order.dto.OrderCreateLineItem;
import com.example.clients.order.dto.OrderCreateRequest;
import com.example.clients.order.dto.OrderCreateResponse;
import com.example.clients.order.exception.OrderClientException;
import com.example.clients.order.facade.OrderCreateClientFacade;
import com.example.clients.product.dto.ProductQuoteLineItemRequest;
import com.example.clients.product.dto.ProductQuoteRequest;
import com.example.clients.product.dto.ProductQuoteResponse;
import com.example.clients.product.dto.ProductQuotedLineItem;
import com.example.clients.product.exception.ProductClientException;
import com.example.clients.product.facade.ProductQuoteClientFacade;
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
import com.example.sales.dto.checkout.CheckoutDraft;
import com.example.sales.dto.checkout.CheckoutQuoteCache;
import com.example.sales.dto.checkout.request.CheckoutCancelRequest;
import com.example.sales.dto.checkout.request.CheckoutQuoteRequest;
import com.example.sales.dto.checkout.request.CheckoutReserveRequest;
import com.example.sales.dto.checkout.request.CheckoutSubmitRequest;
import com.example.sales.dto.checkout.response.CheckoutCancelResponse;
import com.example.sales.dto.checkout.response.CheckoutQuoteResponse;
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
    private final ProductQuoteClientFacade productQuoteClientFacade;
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

        ReserveOrderStockResponse stockResponse;
        try {
            stockResponse = stockOrderReservationClientFacade.reserveOrderStock(new ReserveOrderStockRequest(
                    request.getChannelType(),
                    request.getChannelRefId(),
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
                .channelType(request.getChannelType())
                .channelRefId(request.getChannelRefId())
                .idempotencyKey(request.getIdempotencyKey())
                .expiresAt(stockResponse.expiresAt())
                .lineItems(request.getLineItems().stream()
                        .map(item -> CheckoutDraft.LineItem.builder()
                                .itemId(item.getItemId())
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
    @DistributedLock(key = "'checkout:quote:' + #request.orderId", waitTime = 3, leaseTime = 10)
    public CheckoutQuoteResponse quote(CheckoutQuoteRequest request, Long userId) {
        CheckoutSession session = checkoutSessionRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new BusinessException(SalesErrorCode.CHECKOUT_SESSION_NOT_FOUND));
        validateOwnedSession(session, userId);
        validateQuoteableSession(session);

        CheckoutDraft draft = findDraft(request.getOrderId())
                .orElseGet(() -> toDraft(session));

        return loadQuoteResponse(draft);
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

        ensureSubmitSnapshot(session);

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
    @DistributedLock(key = "'checkout:quote:' + #orderId", waitTime = 3, leaseTime = 10)
    public void warmQuoteCache(Long orderId) {
        findDraft(orderId)
                .filter(draft -> draft.getExpiresAt() == null || LocalDateTime.now().isBefore(draft.getExpiresAt()))
                .ifPresent(this::loadQuoteResponse);
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
        CheckoutDraft persistedDraft = toDraft(persistedSession);
        boolean replayable = isReplayableSession(persistedSession);
        if (replayable && persistedDraft.getExpiresAt() != null && LocalDateTime.now().isBefore(persistedDraft.getExpiresAt())) {
            checkoutDraftRedisService.saveDraft(persistedDraft, ttlUntil(persistedDraft.getExpiresAt()));
        }
        return new ExistingReserveAttempt(persistedDraft, replayable);
    }

    private Optional<CheckoutDraft> findDraft(Long orderId) {
        Optional<CheckoutDraft> redisDraft = checkoutDraftRedisService.findDraft(orderId);
        if (redisDraft.isPresent()) {
            return redisDraft;
        }

        Optional<CheckoutSession> persistedSession = checkoutSessionRepository.findByOrderId(orderId);
        if (persistedSession.isEmpty()) {
            return Optional.empty();
        }

        CheckoutSession session = persistedSession.get();
        if (!isReplayableSession(session)) {
            return Optional.empty();
        }

        CheckoutDraft persistedDraft = toDraft(session);
        if (persistedDraft.getExpiresAt() != null && LocalDateTime.now().isBefore(persistedDraft.getExpiresAt())) {
            checkoutDraftRedisService.saveDraft(persistedDraft, ttlUntil(persistedDraft.getExpiresAt()));
        }
        return Optional.of(persistedDraft);
    }

    private CheckoutQuoteResponse loadQuoteResponse(CheckoutDraft draft) {
        Optional<CheckoutQuoteResponse> cachedQuote = checkoutQuoteCacheRedisService.findQuote(draft.getOrderId())
                .map(this::toQuoteResponse);
        if (cachedQuote.isPresent()) {
            return cachedQuote.get();
        }

        Optional<CheckoutQuoteResponse> persistedQuote = checkoutSessionRepository.findByOrderId(draft.getOrderId())
                .filter(this::hasPersistedQuoteSnapshot)
                .map(session -> toQuoteResponse(session, draft.getExpiresAt()));
        if (persistedQuote.isPresent()) {
            cacheQuote(draft, persistedQuote.get());
            return persistedQuote.get();
        }

        ProductQuoteResponse quoteResponse = fetchLiveQuote(draft);
        applyQuoteSnapshot(draft.getOrderId(), quoteResponse);

        CheckoutQuoteResponse response = toQuoteResponse(draft, quoteResponse);
        cacheQuote(draft, response);
        return response;
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

    private void validateQuoteableSession(CheckoutSession session) {
        if (session.getStatus() == CheckoutSessionStatus.CANCELLED) {
            throw new BusinessException(SalesErrorCode.CHECKOUT_SESSION_CANCELLED);
        }
        if (session.getStatus() == CheckoutSessionStatus.EXPIRED
                || (session.getExpiresAt() != null && LocalDateTime.now().isAfter(session.getExpiresAt()))) {
            clearCheckoutCaches(session);
            throw new BusinessException(SalesErrorCode.CHECKOUT_SESSION_EXPIRED);
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

    private CheckoutQuoteResponse.QuotedLineItem toQuotedLineItem(ProductQuotedLineItem item) {
        return CheckoutQuoteResponse.QuotedLineItem.builder()
                .itemId(item.itemId())
                .itemType(item.itemType())
                .title(item.title())
                .sellerId(item.sellerId())
                .storeId(item.storeId())
                .referenceId(item.referenceId())
                .referenceName(item.referenceName())
                .stockItemType(item.stockItemType())
                .quantity(item.quantity())
                .baseUnitPrice(item.baseUnitPrice())
                .finalUnitPrice(item.finalUnitPrice())
                .lineAmount(item.lineAmount())
                .build();
    }

    private CheckoutQuoteResponse.QuotedLineItem toQuotedLineItem(CheckoutQuoteCache.LineItem item) {
        return CheckoutQuoteResponse.QuotedLineItem.builder()
                .itemId(item.getItemId())
                .itemType(item.getItemType())
                .title(item.getTitle())
                .sellerId(item.getSellerId())
                .storeId(item.getStoreId())
                .referenceId(item.getReferenceId())
                .referenceName(item.getReferenceName())
                .stockItemType(item.getStockItemType())
                .quantity(item.getQuantity())
                .baseUnitPrice(item.getBaseUnitPrice())
                .finalUnitPrice(item.getFinalUnitPrice())
                .lineAmount(item.getLineAmount())
                .build();
    }

    private void applyQuoteSnapshot(Long orderId, ProductQuoteResponse quoteResponse) {
        checkoutSessionRepository.findByOrderId(orderId).ifPresent(session -> {
            for (ProductQuotedLineItem quotedLineItem : quoteResponse.lineItems()) {
                session.getLineItems().stream()
                        .filter(lineItem -> lineItem.matches(quotedLineItem.itemId(), quotedLineItem.referenceId()))
                        .findFirst()
                        .ifPresent(lineItem -> lineItem.applyQuoteSnapshot(
                                quotedLineItem.itemType(),
                                quotedLineItem.title(),
                                quotedLineItem.sellerId(),
                                quotedLineItem.storeId(),
                                quotedLineItem.referenceName(),
                                quotedLineItem.baseUnitPrice(),
                                quotedLineItem.finalUnitPrice(),
                                quotedLineItem.lineAmount()
                        ));
            }

            if (session.getStatus() == CheckoutSessionStatus.RESERVED) {
                session.markQuoted(quoteResponse.quotedAt());
            } else if (session.getStatus() == CheckoutSessionStatus.QUOTED) {
                session.refreshQuotedAt(quoteResponse.quotedAt());
            }
        });
    }

    private void ensureSubmitSnapshot(CheckoutSession session) {
        CheckoutQuoteResponse quoteResponse = loadQuoteResponse(toDraft(session));

        for (CheckoutQuoteResponse.QuotedLineItem lineItem : quoteResponse.getLineItems()) {
            session.getLineItems().stream()
                    .filter(candidate -> candidate.matches(lineItem.getItemId(), lineItem.getReferenceId()))
                    .findFirst()
                    .ifPresent(candidate -> candidate.applyQuoteSnapshot(
                            lineItem.getItemType(),
                            lineItem.getTitle(),
                            lineItem.getSellerId(),
                            lineItem.getStoreId(),
                            lineItem.getReferenceName(),
                            lineItem.getBaseUnitPrice(),
                            lineItem.getFinalUnitPrice(),
                            lineItem.getLineAmount()
                    ));
        }

        if (session.getStatus() == CheckoutSessionStatus.RESERVED) {
            session.markQuoted(quoteResponse.getQuotedAt());
        }
    }

    private ProductQuoteResponse fetchLiveQuote(CheckoutDraft draft) {
        try {
            return productQuoteClientFacade.quoteItems(new ProductQuoteRequest(
                    draft.getChannelType(),
                    draft.getChannelRefId(),
                    draft.getLineItems().stream()
                            .map(item -> new ProductQuoteLineItemRequest(item.getItemId(), item.getReferenceId(), item.getQuantity()))
                            .toList()
            ));
        } catch (ProductClientException e) {
            throw new BusinessException(SalesErrorCode.PRODUCT_SERVICE_ERROR);
        }
    }

    private boolean hasPersistedQuoteSnapshot(CheckoutSession session) {
        if (session.getQuotedAt() == null || session.getStatus() != CheckoutSessionStatus.QUOTED) {
            return false;
        }
        if (session.getLineItems().isEmpty()) {
            return false;
        }
        return session.getLineItems().stream().allMatch(this::hasLineItemQuoteSnapshot);
    }

    private boolean hasLineItemQuoteSnapshot(CheckoutSessionLineItem lineItem) {
        return lineItem.getItemType() != null
                && lineItem.getTitle() != null
                && lineItem.getReferenceName() != null
                && lineItem.getBaseUnitPrice() != null
                && lineItem.getFinalUnitPrice() != null
                && lineItem.getLineAmount() != null;
    }

    private CheckoutQuoteResponse toQuoteResponse(CheckoutDraft draft, ProductQuoteResponse quoteResponse) {
        return CheckoutQuoteResponse.builder()
                .orderId(draft.getOrderId())
                .expiresAt(draft.getExpiresAt())
                .quotedAt(quoteResponse.quotedAt())
                .totalAmount(quoteResponse.totalAmount())
                .lineItems(quoteResponse.lineItems().stream()
                        .map(this::toQuotedLineItem)
                        .toList())
                .build();
    }

    private CheckoutQuoteResponse toQuoteResponse(CheckoutSession session, LocalDateTime expiresAt) {
        long totalAmount = session.getLineItems().stream()
                .map(CheckoutSessionLineItem::getLineAmount)
                .filter(amount -> amount != null)
                .mapToLong(Long::longValue)
                .sum();

        return CheckoutQuoteResponse.builder()
                .orderId(session.getOrderId())
                .expiresAt(expiresAt)
                .quotedAt(session.getQuotedAt())
                .totalAmount(totalAmount)
                .lineItems(session.getLineItems().stream()
                        .map(this::toQuotedLineItem)
                        .toList())
                .build();
    }

    private CheckoutQuoteResponse.QuotedLineItem toQuotedLineItem(CheckoutSessionLineItem item) {
        return CheckoutQuoteResponse.QuotedLineItem.builder()
                .itemId(item.getItemId())
                .itemType(item.getItemType())
                .title(item.getTitle())
                .sellerId(item.getSellerId())
                .storeId(item.getStoreId())
                .referenceId(item.getReferenceId())
                .referenceName(item.getReferenceName())
                .stockItemType(item.getStockItemType())
                .quantity(item.getQuantity())
                .baseUnitPrice(item.getBaseUnitPrice())
                .finalUnitPrice(item.getFinalUnitPrice())
                .lineAmount(item.getLineAmount())
                .build();
    }

    private void cacheQuote(CheckoutDraft draft, CheckoutQuoteResponse response) {
        if (draft.getExpiresAt() == null) {
            log.debug("Skip quote cache because expiresAt is missing: orderId={}", draft.getOrderId());
            return;
        }

        checkoutQuoteCacheRedisService.saveQuote(
                CheckoutQuoteCache.builder()
                        .orderId(response.getOrderId())
                        .expiresAt(response.getExpiresAt())
                        .quotedAt(response.getQuotedAt())
                        .totalAmount(response.getTotalAmount())
                        .lineItems(response.getLineItems().stream()
                                .map(item -> CheckoutQuoteCache.LineItem.builder()
                                        .itemId(item.getItemId())
                                        .itemType(item.getItemType())
                                        .title(item.getTitle())
                                        .sellerId(item.getSellerId())
                                        .storeId(item.getStoreId())
                                        .referenceId(item.getReferenceId())
                                        .referenceName(item.getReferenceName())
                                        .stockItemType(item.getStockItemType())
                                        .quantity(item.getQuantity())
                                        .baseUnitPrice(item.getBaseUnitPrice())
                                        .finalUnitPrice(item.getFinalUnitPrice())
                                        .lineAmount(item.getLineAmount())
                                        .build())
                                .toList())
                        .build(),
                ttlUntil(draft.getExpiresAt())
        );
    }

    private CheckoutQuoteResponse toQuoteResponse(CheckoutQuoteCache quoteCache) {
        return CheckoutQuoteResponse.builder()
                .orderId(quoteCache.getOrderId())
                .expiresAt(quoteCache.getExpiresAt())
                .quotedAt(quoteCache.getQuotedAt())
                .totalAmount(quoteCache.getTotalAmount())
                .lineItems(quoteCache.getLineItems().stream()
                        .map(this::toQuotedLineItem)
                        .toList())
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
                    request.getChannelType(),
                    request.getChannelRefId(),
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
        if (!Objects.equals(normalizeEnumLike(draft.getChannelType()), normalizeEnumLike(request.getChannelType()))) {
            return false;
        }
        if (!Objects.equals(draft.getChannelRefId(), request.getChannelRefId())) {
            return false;
        }

        List<CheckoutDraft.LineItem> existingLineItems = draft.getLineItems().stream()
                .sorted(Comparator
                        .comparing(CheckoutDraft.LineItem::getItemId)
                        .thenComparing(item -> normalizeEnumLike(item.getStockItemType()))
                        .thenComparing(CheckoutDraft.LineItem::getReferenceId)
                        .thenComparing(CheckoutDraft.LineItem::getQuantity))
                .toList();
        List<CheckoutReserveRequest.LineItem> requestLineItems = request.getLineItems().stream()
                .sorted(Comparator
                        .comparing(CheckoutReserveRequest.LineItem::getItemId)
                        .thenComparing(item -> normalizeEnumLike(item.getStockItemType()))
                        .thenComparing(CheckoutReserveRequest.LineItem::getReferenceId)
                        .thenComparing(CheckoutReserveRequest.LineItem::getQuantity))
                .toList();

        if (existingLineItems.size() != requestLineItems.size()) {
            return false;
        }

        for (int i = 0; i < existingLineItems.size(); i++) {
            CheckoutDraft.LineItem existing = existingLineItems.get(i);
            CheckoutReserveRequest.LineItem candidate = requestLineItems.get(i);
            if (!Objects.equals(existing.getItemId(), candidate.getItemId())
                    || !Objects.equals(normalizeEnumLike(existing.getStockItemType()), normalizeEnumLike(candidate.getStockItemType()))
                    || !Objects.equals(existing.getReferenceId(), candidate.getReferenceId())
                    || !Objects.equals(existing.getQuantity(), candidate.getQuantity())) {
                return false;
            }
        }
        return true;
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

    private CheckoutDraft toDraft(CheckoutSession session) {
        CheckoutSessionLineItem firstLineItem = session.getLineItems().isEmpty()
                ? null
                : session.getLineItems().get(0);

        return CheckoutDraft.builder()
                .orderId(session.getOrderId())
                .userId(session.getUserId())
                .channelType(firstLineItem == null ? null : firstLineItem.getChannelType())
                .channelRefId(firstLineItem == null ? null : firstLineItem.getChannelRefId())
                .idempotencyKey(session.getIdempotencyKey())
                .expiresAt(session.getExpiresAt())
                .lineItems(session.getLineItems().stream()
                        .map(item -> CheckoutDraft.LineItem.builder()
                                .itemId(item.getItemId())
                                .stockItemType(item.getStockItemType())
                                .referenceId(item.getReferenceId())
                                .quantity(item.getQuantity())
                                .build())
                        .toList())
                .build();
    }
}
